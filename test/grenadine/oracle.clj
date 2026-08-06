(ns grenadine.oracle
  "JVM-only differential harness against clojure.tools.deps."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.tools.deps :as tools-deps]
            [grenadine.core :as grenadine]
            [grenadine.host.jvm :as jvm]
            [grenadine.version :as version])
  (:import [org.apache.maven.artifact.versioning ComparableVersion]))

(def repos
  [{:id "central" :url "https://repo.maven.apache.org/maven2/"}
   {:id "clojars" :url "https://repo.clojars.org/"}])

(def tools-repos
  (into {} (map (juxt :id #(select-keys % [:url])) repos)))

(def gitlibs-dir
  (.getCanonicalPath (io/file ".cache/oracle/gitlibs")))

(defn- tools-basis
  [deps local-repo]
  (select-keys
   (tools-deps/calc-basis
    {:deps deps
     :mvn/repos tools-repos
     :mvn/local-repo local-repo})
   [:libs :classpath :classpath-roots]))

(defn- grenadine-basis
  [deps local-repo]
  (select-keys
   (grenadine/calc-basis
    {:deps deps
     :mvn/repos tools-repos
     :mvn/local-repo local-repo}
    {:host (jvm/host)
     :repos repos
     :local-repo local-repo
     :gitlibs-dir gitlibs-dir
     :mediation :tools-deps})
   [:libs :classpath :classpath-roots]))

(defn- load-corpus
  []
  (edn/read-string
   (slurp (io/file "test/fixtures/differential-corpus.edn"))))

(defn- delete-tree! [root]
  (let [root (io/file root)]
    (when (.exists root)
      (doseq [file (reverse (file-seq root))]
        (when-not (.delete file)
          (throw (ex-info (str "Unable to delete " file) {:path (str file)})))))))

(defn- copy-tree! [source destination]
  (let [source (.toPath (io/file source))
        destination (.toPath (io/file destination))]
    (doseq [file (file-seq (.toFile source))]
      (let [relative (.relativize source (.toPath file))
            target (.toFile (.resolve destination relative))]
        (if (.isDirectory file)
          (.mkdirs target)
          (do (.mkdirs (.getParentFile target))
              (io/copy file target)))))))

(defn- git! [& args]
  (let [{:keys [exit out err]} (apply shell/sh "git" args)]
    (when-not (zero? exit)
      (throw (ex-info (str "git failed: " err) {:args args :exit exit})))
    (.trim out)))

(defn- prepare-git-case! []
  (let [repository (io/file ".cache/oracle/git-repo")]
    (delete-tree! repository)
    (copy-tree! "test/fixtures/git-lib" repository)
    (git! "-C" (str repository) "init" "--quiet")
    (git! "-C" (str repository) "add" ".")
    (git! "-C" (str repository) "-c" "user.name=Grenadine Oracle"
          "-c" "user.email=oracle@example.invalid"
          "commit" "--quiet" "-m" "fixture")
    (let [sha (git! "-C" (str repository) "rev-parse" "HEAD")]
      {:name :git-pom
       :deps
       {'example/git
        {:git/url (str "file://" (.getCanonicalPath repository))
         :git/sha sha
         :deps/manifest :pom}}})))

(def version-corpus
  ["1" "1.0" "1.0.0" "1-1" "1.0-1" "1.0.1"
   "1-alpha" "1-a1" "1-beta" "1-m1" "1-rc1" "1-SNAPSHOT"
   "1-sp" "1-foo" "1-foo2" "1-foo10"])

(defn- environment-long [name fallback]
  (if-let [value (System/getenv name)]
    (Long/parseLong value)
    fallback))

(defn- basis-pair [deps local-repo]
  {:deps deps
   :expected (tools-basis deps local-repo)
   :actual (grenadine-basis deps local-repo)})

(defn- random-deps [^java.util.Random random corpus]
  (let [n (inc (.nextInt random (min 3 (count corpus))))]
    (reduce merge {}
            (repeatedly n
                        #(-> corpus (.get (.nextInt random (count corpus))) :deps)))))

(defn- mismatch? [deps local-repo]
  (let [{:keys [expected actual]} (basis-pair deps local-repo)]
    (not= expected actual)))

(defn- shrink-deps [deps local-repo]
  (loop [current deps candidates (keys deps)]
    (if-let [lib (first candidates)]
      (let [smaller (dissoc current lib)]
        (if (and (seq smaller) (mismatch? smaller local-repo))
          (recur smaller (keys smaller))
          (recur current (next candidates))))
      current)))

(defn- fuzz-results [corpus local-repo]
  (let [seed (environment-long "GRENADINE_ORACLE_SEED" 424242)
        cases (environment-long "GRENADINE_ORACLE_CASES" 25)
        random (java.util.Random. seed)
        results
        (mapv
         (fn [index]
           (let [{:keys [deps expected actual] :as result}
                 (basis-pair (random-deps random corpus) local-repo)]
             (assoc result :case index :match? (= expected actual))))
         (range cases))]
    {:seed seed :cases cases :results results}))

(defn- save-fuzz-failure! [seed local-repo failure]
  (let [deps (shrink-deps (:deps failure) local-repo)
        pair (basis-pair deps local-repo)
        path (str ".cache/oracle/failure-" seed ".edn")]
    (.mkdirs (io/file ".cache/oracle"))
    (spit path (pr-str (assoc pair :seed seed :case (:case failure))))
    path))

(defn- version-failures
  []
  (vec
   (for [left version-corpus
         right version-corpus
         :let [expected (compare (ComparableVersion. left)
                                 (ComparableVersion. right))
               actual (version/compare-versions left right)]
         :when (not= (compare expected 0) (compare actual 0))]
     {:left left :right right :expected expected :actual actual})))

(defn -main
  [& _args]
  (try
    (let [local-repo ".cache/oracle-m2"
        _ (System/setProperty "clojure.gitlibs.dir" gitlibs-dir)
        corpus (conj (load-corpus)
                     {:name :local-pom
                      :deps
                      {'example/local-pom
                       {:local/root "test/fixtures/local-pom-lib"}}}
                     (prepare-git-case!))
        results
        (mapv
         (fn [{:keys [name deps]}]
           (println "Differential:" name)
           (let [expected (tools-basis deps local-repo)
                 actual (grenadine-basis deps local-repo)]
             {:name name
              :expected expected
              :actual actual
              :match? (= expected actual)}))
         corpus)
        failures (remove :match? results)
        version-failures (version-failures)
        fuzz (fuzz-results corpus local-repo)
        fuzz-failures (remove :match? (:results fuzz))]
    (doseq [{:keys [name expected actual]} failures]
      (binding [*out* *err*]
        (println "\nMismatch:" name)
        (println "tools.deps:" (pr-str expected))
        (println "grenadine: " (pr-str actual))))
    (println "Differential cases:" (count results)
             "matched:" (- (count results) (count failures))
             "failed:" (count failures))
    (println "Version comparisons:" (* (count version-corpus)
                                        (count version-corpus))
             "failed:" (count version-failures))
    (println "Fuzz bases:" (:cases fuzz)
             "seed:" (:seed fuzz)
             "failed:" (count fuzz-failures))
    (when-let [failure (first fuzz-failures)]
      (binding [*out* *err*]
        (println "Fuzz mismatch saved:"
                 (save-fuzz-failure! (:seed fuzz) local-repo failure))))
    (doseq [failure version-failures]
      (binding [*out* *err*]
        (println "ComparableVersion mismatch:" (pr-str failure))))
    (when (or (seq failures) (seq version-failures) (seq fuzz-failures))
      (throw
       (ex-info "Grenadine JVM oracle mismatch"
                {:dependency-failures (mapv :name failures)
                 :version-failures version-failures
                 :fuzz-failures (mapv :case fuzz-failures)}))))
    (finally
      (shutdown-agents))))
