(ns grenadine.repo-test
  (:require [clojure.test :refer [deftest is]]
            [grenadine.repo :as repo]
            [grenadine.test-support :refer [throws?]]))

(defn fake-host
  [initial responses]
  (let [files (atom initial)]
    {:files files
     :host
     {:http-get #(get responses % {:status 404})
      :read-bytes #(get @files %)
      :write-bytes! #(swap! files assoc %1 %2)
      :bytes->utf8 identity
      :digest (fn [algorithm bytes]
                (get {[:sha1 bytes] (str "sha1-" bytes)
                      [:sha256 bytes] (str "sha256-" bytes)}
                     [algorithm bytes]))
      :byte-count count
      :exists? #(contains? @files %)
      :mkdirs! (fn [_])
      :atomic-move!
      (fn [from to]
        (swap! files #(-> % (assoc to (get % from)) (dissoc from))))
      :delete! #(swap! files dissoc %)
      :extract-jar!
      (fn [jar destination]
        (swap! files assoc
               (str destination "/.grenadine-complete")
               (str "extracted:" jar)))
      :home-dir (fn [] "/home/test")
      :getenv (fn [_] nil)}}))

(deftest selects-local-repository
  (let [host {:home-dir (fn [] "/home/test")
              :getenv
              (fn [name]
                (when (= name "GRENADINE_LOCAL_REPOSITORY")
                  "/env/m2"))}]
    (is (= "/explicit/m2"
           (repo/local-repo {:host host :local-repo "/explicit/m2"})))
    (is (= "/env/m2"
           (repo/local-repo {:host host})))
    (is (= "/home/test/.m2/repository"
           (repo/local-repo
            {:host (assoc host :getenv (fn [_] ""))})))
    (is (= "/home/test/.m2/repository"
           (repo/local-repo
            {:host (assoc host :getenv (fn [_] nil))})))))

(defn metadata
  [{:keys [release latest versions]}]
  (str "<metadata><versioning>"
       (when release (str "<release>" release "</release>"))
       (when latest (str "<latest>" latest "</latest>"))
       "<versions>"
       (apply str (map #(str "<version>" % "</version>") versions))
       "</versions></versioning></metadata>"))

(deftest selects-latest-maven-release
  (let [url "https://repo.example/demo/a/maven-metadata.xml"
        coords {:group "demo" :artifact "a"}
        latest
        (fn [body]
          (let [{:keys [host]}
                (fake-host {} {url {:status 200 :body body}})]
            (repo/latest-version
             coords {:host host :repos ["https://repo.example"]})))]
    (is (= "2.0" (latest (metadata {:release "2.0"
                                     :latest "3.0-SNAPSHOT"
                                     :versions ["1.0" "3.0-SNAPSHOT"]}))))
    (is (= "3.0-SNAPSHOT"
           (latest (metadata {:latest "3.0-SNAPSHOT"
                              :versions ["1.0" "2.0"]}))))
    (is (= "1.10"
           (latest (metadata {:versions ["1.9" "1.10"
                                         "2.0-SNAPSHOT"]}))))))

(deftest latest-release-falls-back-between-repositories
  (let [central "https://central.example/demo/a/maven-metadata.xml"
        clojars "https://clojars.example/demo/a/maven-metadata.xml"
        {:keys [host]}
        (fake-host
         {}
         {central {:status 404}
          clojars {:status 200 :body (metadata {:release "4.2"})}})]
    (is (= "4.2"
           (repo/latest-version
            {:group "demo" :artifact "a"}
            {:host host
             :repos ["https://central.example" "https://clojars.example"]})))))

(deftest resolves-highest-matching-version-range-across-repositories
  (let [central "https://central.example/demo/a/maven-metadata.xml"
        clojars "https://clojars.example/demo/a/maven-metadata.xml"
        {:keys [host]}
        (fake-host
         {}
         {central {:status 200
                   :body (metadata {:versions ["4.9" "5.0" "5.9" "6.0"]})}
          clojars {:status 200
                   :body (metadata {:versions ["5.10" "5.11"]})}})]
    (is (= "5.11"
           (repo/resolve-version-range
            {:group "demo" :artifact "a"}
            "[5.0,6.0)"
            {:host host
             :repos ["https://central.example" "https://clojars.example"]})))
    (is (throws?
         (repo/resolve-version-range
          {:group "demo" :artifact "a"}
          "[7.0,8.0)"
          {:host host
           :repos ["https://central.example" "https://clojars.example"]})))))

(deftest rejects-missing-and-invalid-metadata
  (let [{missing-host :host} (fake-host {} {})
        url "https://repo.example/demo/a/maven-metadata.xml"
        {invalid-host :host}
        (fake-host {} {url {:status 200 :body "<metadata>"}})]
    (is (throws?
         (repo/latest-version
          {:group "demo" :artifact "a"}
          {:host missing-host :repos ["https://repo.example"]})))
    (is (throws?
         (repo/latest-version
          {:group "demo" :artifact "a"}
          {:host invalid-host :repos ["https://repo.example"]})))))

(deftest fetches-timestamped-snapshot-poms-from-the-base-version-directory
  (let [relative (str "metosin/malli/0.0.1-SNAPSHOT/"
                      "malli-0.0.1-20200715.082439-21.pom")
        url (str "https://repo.example/" relative)
        {:keys [host files]}
        (fake-host {} {url {:status 200 :body "<project/>"}})
        fetch-pom (repo/pom-fetcher
                   {:host host :local-repo "/m2"
                    :repos ["https://repo.example"]})]
    (is (= "<project/>"
           (fetch-pom {:group "metosin" :artifact "malli"
                       :version "0.0.1-20200715.082439-21"})))
    (is (= "<project/>" (get @files (str "/m2/" relative))))))

(deftest fetches-verifies-and-enriches-lock
  (let [url "https://repo.example/demo/a/1/a-1.jar"
        {:keys [host files]}
        (fake-host
         {}
         {url {:status 200 :body "bytes"}
          (str url ".sha1") {:status 200 :body "sha1-bytes  a-1.jar"}})
        installed (atom [])
        result
        (repo/fetch-lock!
         {:lock/version 1
          :repos ["https://repo.example"]
          :artifacts
          [{:group "demo" :artifact "a" :version "1"
            :path "demo/a/1/a-1.jar" :repo 0}]}
         {:host host
          :local-repo "/m2"
          :on-install
          (fn [artifact]
            (swap! installed conj
                   [artifact (get @files "/m2/demo/a/1/a-1.jar")]))})]
    (is (empty? (:failed result)))
    (is (= 1 (count (:fetched result))))
    (is (= "bytes" (get @files "/m2/demo/a/1/a-1.jar")))
    (is (= [[{:group "demo" :artifact "a" :version "1"
              :path "demo/a/1/a-1.jar" :repo 0}
             "bytes"]]
           @installed))
    (is (= {:sha256 "sha256-bytes" :size 5}
           (select-keys (first (get-in result [:lock :artifacts]))
                        [:sha256 :size])))))

(deftest does-not-notify-or-warn-for-cached-artifacts
  (let [path "/m2/demo/a/1/a-1.jar"
        {:keys [host]} (fake-host {path "bytes"} {})
        installed (atom [])
        result
        (repo/fetch-lock!
         {:lock/version 1
          :repos ["https://repo.example"]
          :artifacts
          [{:group "demo" :artifact "a" :version "1"
            :path "demo/a/1/a-1.jar" :repo 0}]}
         {:host host
          :local-repo "/m2"
          :on-install #(swap! installed conj %)})]
    (is (empty? (:fetched result)))
    (is (= 1 (count (:cached result))))
    (is (empty? (:warnings result)))
    (is (empty? @installed))))

(deftest rejects-checksum-mismatch
  (let [url "https://repo.example/demo/a/1/a-1.jar"
        {:keys [host files]} (fake-host {} {url {:status 200 :body "bad"}})
        result
        (repo/fetch-lock!
         {:lock/version 1
          :repos ["https://repo.example"]
          :artifacts
          [{:path "demo/a/1/a-1.jar" :repo 0 :sha256 "wanted"}]}
         {:host host :local-repo "/m2"})]
    (is (= :checksum-mismatch (get-in result [:failed 0 :reason])))
    (is (not (contains? @files "/m2/demo/a/1/a-1.jar")))))

(deftest falls-back-to-another-repository
  (let [central "https://central.example/demo/a/1/a-1.jar"
        clojars "https://clojars.example/demo/a/1/a-1.jar"
        {:keys [host files]}
        (fake-host
         {}
         {central {:status 404}
          clojars {:status 200 :body "bytes"}
          (str clojars ".sha1") {:status 200 :body "sha1-bytes"}})
        result
        (repo/fetch-lock!
         {:lock/version 1
          :repos ["https://central.example" "https://clojars.example"]
          :artifacts
          [{:group "demo" :artifact "a" :version "1"
            :path "demo/a/1/a-1.jar" :repo 0}]}
         {:host host :local-repo "/m2"})]
    (is (empty? (:failed result)))
    (is (= 1 (get-in result [:lock :artifacts 0 :repo])))
    (is (= "bytes" (get @files "/m2/demo/a/1/a-1.jar")))))

(deftest prepares-digest-keyed-source-roots
  (let [{:keys [host files]}
        (fake-host {"/m2/demo/a/1/a-1.jar" "bytes"} {})
        result
        (repo/prepare-source-roots!
         {:artifacts [{:path "demo/a/1/a-1.jar"
                       :sha256 "locked-sha"}]}
         {:host host :local-repo "/m2"})
        root "/m2/demo/a/1/a-1.jar.grenadine/locked-sha"]
    (is (= [root] (:roots result)))
    (is (contains? @files (str root "/.grenadine-complete")))))

(deftest selects-source-libraries
  (let [{:keys [host files]}
        (fake-host
         {"/m2/demo/a/1/a-1.jar" "a"
          "/m2/demo/b/1/b-1.jar" "b"}
         {})
        result
        (repo/prepare-source-roots!
         {:artifacts
          [{:group "demo" :artifact "a" :path "demo/a/1/a-1.jar"}
           {:group "demo" :artifact "b" :path "demo/b/1/b-1.jar"}]}
         {:host host
          :local-repo "/m2"
          :source-libs #{'demo/b}})]
    (is (= 1 (count (:roots result))))
    (is (re-find #"/demo/b/" (first (:roots result))))
    (is (not-any? #(re-find #"/demo/a/.*\.grenadine/" %)
                  (keys @files)))))
