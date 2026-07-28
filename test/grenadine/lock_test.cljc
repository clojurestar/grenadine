(ns grenadine.lock-test
  (:require [clojure.test :refer [deftest is]]
            [grenadine.graph-test :as graph-fixture]
            [grenadine.graph :as graph]
            [grenadine.lock :as lock]))

(deftest emits-stable-lock
  (let [resolution
        (graph/resolve-graph
         graph-fixture/roots
         {:pom-fn graph-fixture/pom-fn :mediation :newest})
        result
        (lock/emit-lock
         resolution
         {:pom-fn graph-fixture/pom-fn
          :repos [{:id "test" :url "https://repo.example/"}]})]
    (is (= 1 (:lock/version result)))
    (is (= ["https://repo.example/"] (:repos result)))
    (is (= [["a" "1"] ["b" "1"] ["c" "2"] ["leaf" "2"]]
           (mapv (juxt :artifact :version) (:artifacts result))))
    (is (= "demo/c/2/c-2.jar"
           (:path (nth (:artifacts result) 2))))))

(deftest creates-local-classpath
  (is (= ["/tmp/m2/demo/a/1/a-1.jar"]
         (lock/lock->classpath
          {:artifacts [{:path "demo/a/1/a-1.jar"}]}
          {:local-repo "/tmp/m2/"}))))
