(ns grenadine.clojurestar-deps-test
  (:require [clojure.test :refer [deftest is]]
            [clojurestar.deps :as deps]))

(deftest empty-deps-is-a-portable-no-op
  (is (nil? (deps/add-deps {})))
  (is (nil? (deps/add-deps {:deps {}}))))
