(ns grenadine.runtime-test
  (:require [clojure.test :refer [deftest is]]
            [grenadine.runtime :as runtime]))

(deftest add-only-source-roots
  (let [basis (atom {:libs {} :classpath {} :classpath-roots []
                     :grenadine/loaded {}})
        added (atom [])
        installs (atom 0)
        install-fn
        (fn [libs _opts]
          (swap! installs inc)
          {:source-roots ["/cache/a" "/cache/b"]
           :basis {:libs libs :classpath {} :classpath-roots []}
           :warnings []})]
    (runtime/add-libs! basis #(swap! added into %)
                       {'example/a {:mvn/version "1"}}
                       {:install-fn install-fn})
    (is (= ["/cache/a" "/cache/b"] @added))
    (is (= 1 @installs))
    (is (= {'example/a {:mvn/version "1"}}
           (:libs (runtime/current-basis basis))))
    (let [result
          (runtime/add-libs! basis #(swap! added into %)
                             {'example/a {:mvn/version "2"}}
                             {:install-fn install-fn})]
      (is (= 1 @installs))
      (is (= :loaded-lib-not-upgraded
             (get-in result [:warnings 0 :warning]))))))
