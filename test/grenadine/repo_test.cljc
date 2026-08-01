(ns grenadine.repo-test
  (:require [clojure.test :refer [deftest is]]
            [grenadine.repo :as repo]))

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

(deftest fetches-verifies-and-enriches-lock
  (let [url "https://repo.example/demo/a/1/a-1.jar"
        {:keys [host files]}
        (fake-host
         {}
         {url {:status 200 :body "bytes"}
          (str url ".sha1") {:status 200 :body "sha1-bytes  a-1.jar"}})
        result
        (repo/fetch-lock!
         {:lock/version 1
          :repos ["https://repo.example"]
          :artifacts
          [{:group "demo" :artifact "a" :version "1"
            :path "demo/a/1/a-1.jar" :repo 0}]}
         {:host host :local-repo "/m2"})]
    (is (empty? (:failed result)))
    (is (= 1 (count (:fetched result))))
    (is (= "bytes" (get @files "/m2/demo/a/1/a-1.jar")))
    (is (= {:sha256 "sha256-bytes" :size 5}
           (select-keys (first (get-in result [:lock :artifacts]))
                        [:sha256 :size])))))

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
