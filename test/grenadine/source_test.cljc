(ns grenadine.source-test
  (:require [clojure.test :refer [deftest is testing]]
            [grenadine.source :as source]
            [grenadine.test-support :refer [throws?]]))

(deftest remote-source-test
  (is (source/remote? "https://example.com/deps.edn"))
  (is (source/remote? "HTTP://example.com/deps.edn"))
  (is (not (source/remote? "deps.edn")))
  (is (not (source/remote? "file:///tmp/deps.edn"))))

(deftest request-url-test
  (testing "GitHub blob pages request raw content"
    (is (= "https://github.com/yaml/yamlscript/blob/main/core/deps.edn?raw=1"
           (source/request-url
            "https://github.com/yaml/yamlscript/blob/main/core/deps.edn")))
    (is (= "https://github.com/o/r/blob/main/deps.edn?plain=1&raw=1#L1"
           (source/request-url
            "https://github.com/o/r/blob/main/deps.edn?plain=1#L1"))))
  (testing "other URLs are unchanged"
    (is (= "https://raw.githubusercontent.com/o/r/main/deps.edn"
           (source/request-url
            "https://raw.githubusercontent.com/o/r/main/deps.edn")))
    (is (= "https://github.com/o/r/raw/main/deps.edn"
           (source/request-url
            "https://github.com/o/r/raw/main/deps.edn")))))

(deftest fetch-text-test
  (let [requested (atom nil)
        host {:http-get
              (fn [url]
                (reset! requested url)
                {:status 200 :body "{:deps {}}"})
              :bytes->utf8 identity}
        url "https://github.com/o/r/blob/main/deps.edn"]
    (is (= "{:deps {}}" (source/fetch-text host url)))
    (is (= (str url "?raw=1") @requested))))

(deftest fetch-error-test
  (let [source-url "https://example.com/deps.edn"
        host (fn [status]
               {:http-get (fn [_] {:status status :body nil})
                :bytes->utf8 identity})]
    #?(:glj
       (do
         (is (throws? (source/fetch-text (host 404) source-url)))
         (is (throws? (source/fetch-text (host 0) source-url))))
       :jolt
       (do
         (is (throws? (source/fetch-text (host 404) source-url)))
         (is (throws? (source/fetch-text (host 0) source-url))))
       :lg
       (do
         (is (throws? (source/fetch-text (host 404) source-url)))
         (is (throws? (source/fetch-text (host 0) source-url))))
       :default
       (do
         (is (thrown-with-msg? Exception #"HTTP 404"
                               (source/fetch-text (host 404) source-url)))
         (is (thrown-with-msg? Exception #"request failed"
                               (source/fetch-text (host 0) source-url)))))))
