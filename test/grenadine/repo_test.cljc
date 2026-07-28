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
