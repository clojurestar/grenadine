(ns grenadine.clojurestar-deps-test
  (:require [clojure.test :refer [deftest is]]
            [clojurestar.deps :as deps]
            [grenadine.test-support]
            #?(:gobb [gobb.deps]
               :glj [glojure.deps]
               :jolt [jolt.deps])))

(deftest empty-deps-is-a-portable-no-op
  (is (nil? (deps/add-deps {})))
  (is (nil? (deps/add-deps {:deps {}}))))

(deftest require-deps-orchestrates-left-to-right
  (let [seen (atom [])
        prepare
        (fn [coordinate options]
          (swap! seen conj [(:coordinate coordinate) options])
          'grenadine.test-support)
        exercise
        (fn []
          (is (nil?
               (deps/require-deps
                {:gitlibs/dir "/gitlibs"}
                ["gist:ingydotnet/f70409675d234aa4f2fe379cd975a4f5"
                 :as support]
                '["mvn:example/library@1.0.0/example.library"
                  :refer [throws?]])))
          (is (= [["gist:ingydotnet/f70409675d234aa4f2fe379cd975a4f5"
                   {:gitlibs/dir "/gitlibs"}]
                  ["mvn:example/library@1.0.0/example.library"
                   {:gitlibs/dir "/gitlibs"}]]
                 @seen))
          (is (some? (resolve 'support/throws?)))
          (is (some? (resolve 'throws?))))]
    #?(:glj (with-redefs [glojure.deps/prepare-required! prepare] (exercise))
       :jolt (with-redefs-fn
               {(var jolt.deps/prepare-required!) prepare}
               exercise)
       :gobb (with-redefs [gobb.deps/prepare-required! prepare] (exercise)))))
