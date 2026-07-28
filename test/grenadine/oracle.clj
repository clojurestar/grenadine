(ns grenadine.oracle
  "JVM-only differential harness against clojure.tools.deps."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
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

(defn- tools-selection
  [deps local-repo]
  (->> (tools-deps/resolve-deps
        {:deps deps
         :mvn/repos tools-repos
         :mvn/local-repo local-repo}
        {})
       (keep
        (fn [[lib coordinate]]
          (when-let [version (:mvn/version coordinate)]
            [lib version])))
       (into (sorted-map))))

(defn- grenadine-selection
  [deps local-repo]
  (let [resolution
        (grenadine/resolve-graph
         deps
         {:host (jvm/host)
          :repos repos
          :local-repo local-repo
          :mediation :tools-deps})]
    (->> (:selected resolution)
         (map
          (fn [[[group artifact] occurrence]]
            [(symbol group artifact)
             (get-in occurrence [:coords :version])]))
         (into (sorted-map)))))

(defn- load-corpus
  []
  (edn/read-string
   (slurp (io/file "test/fixtures/differential-corpus.edn"))))

(def version-corpus
  ["1" "1.0" "1.0.0" "1-1" "1.0-1" "1.0.1"
   "1-alpha" "1-a1" "1-beta" "1-m1" "1-rc1" "1-SNAPSHOT"
   "1-sp" "1-foo" "1-foo2" "1-foo10"])

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
  (let [local-repo ".cache/oracle-m2"
        results
        (mapv
         (fn [{:keys [name deps]}]
           (println "Differential:" name)
           (let [expected (tools-selection deps local-repo)
                 actual (grenadine-selection deps local-repo)]
             {:name name
              :expected expected
              :actual actual
              :match? (= expected actual)}))
         (load-corpus))
        failures (remove :match? results)
        version-failures (version-failures)]
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
    (doseq [failure version-failures]
      (binding [*out* *err*]
        (println "ComparableVersion mismatch:" (pr-str failure))))
    (when (or (seq failures) (seq version-failures))
      (throw
       (ex-info "Grenadine JVM oracle mismatch"
                {:dependency-failures (mapv :name failures)
                 :version-failures version-failures})))))
