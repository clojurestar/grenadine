(ns grenadine.runtime-test
  (:require [clojure.test :refer [deftest is]]
            [grenadine.runtime :as runtime]))

(deftest add-only-source-roots
  (let [basis (atom {:libs {} :classpath {} :classpath-roots []
                     :grenadine/loaded {}})
        added (atom [])
        installs (atom 0)
        install-options (atom nil)
        install-fn
        (fn [libs opts]
          (swap! installs inc)
          (reset! install-options opts)
          {:source-roots ["/cache/a" "/cache/b"]
           :basis {:libs libs :classpath {} :classpath-roots []}
           :warnings []})]
    (runtime/add-libs! basis #(swap! added into %)
                       {'example/a {:mvn/version "1"}
                        'org.clojure/clojure {:mvn/version "1.12.4"}}
                       {:install-fn install-fn})
    (is (= ["/cache/a" "/cache/b"] @added))
    (is (= 1 @installs))
    (is (= {'example/a {:mvn/version "1"}}
           (:libs (runtime/current-basis basis))))
    (is (= '#{org.clojure/clojure org.clojure/clojurescript}
           (:provided-libs @install-options)))
    (let [result
          (runtime/add-libs! basis #(swap! added into %)
                             {'example/a {:mvn/version "2"}}
                             {:install-fn install-fn})]
      (is (= 1 @installs))
      (is (= :loaded-lib-not-upgraded
             (get-in result [:warnings 0 :warning]))))
    (runtime/add-libs! basis #(swap! added into %)
                       {'org.clojure/clojure {:mvn/version "1.12.4"}}
                       {:install-fn install-fn})
    (is (= 1 @installs))))
