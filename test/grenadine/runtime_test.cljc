(ns grenadine.runtime-test
  (:require [clojure.test :refer [deftest is]]
            [grenadine.runtime :as runtime]))

(deftest add-only-source-roots
  (let [basis (atom {})
        added (atom [])
        installs (atom 0)
        install-fn
        (fn [_libs _opts]
          (swap! installs inc)
          {:source-roots ["/cache/a" "/cache/b"]
           :warnings []})]
    (runtime/add-libs! basis #(swap! added into %)
                       {'example/a {:mvn/version "1"}}
                       {:install-fn install-fn})
    (is (= ["/cache/a" "/cache/b"] @added))
    (is (= 1 @installs))
    (is (= {'example/a {:mvn/version "1"}} (runtime/current-basis basis)))
    (let [result
          (runtime/add-libs! basis #(swap! added into %)
                             {'example/a {:mvn/version "2"}}
                             {:install-fn install-fn})]
      (is (= 1 @installs))
      (is (= :loaded-lib-not-upgraded
             (get-in result [:warnings 0 :warning]))))))
