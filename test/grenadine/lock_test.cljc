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

(deftest reconstructs-mixed-version-two-classpath
  (let [sha (apply str (repeat 40 "a"))
        result
        (lock/lock->classpath
         {:lock/version 2
          :libs
          [{:lib 'demo/maven
            :coord {:mvn/version "1"}
            :classpath [{:type :mvn :path "demo/maven/1/maven-1.jar"}]}
           {:lib 'demo/git
            :coord {:git/url "https://example.test/demo.git" :git/sha sha}
            :classpath [{:type :git :path "src"}]}
           {:lib 'demo/local
            :coord {:local/root "/work/local"}
            :classpath [{:type :local :path "/work/local/src"}]}]}
         {:local-repo "/tmp/m2"
          :gitlibs-dir "/tmp/gitlibs"})]
    (is (= [(str "/tmp/m2/demo/maven/1/maven-1.jar")
            (str "/tmp/gitlibs/libs/demo/git/" sha "/src")
            "/work/local/src"]
           result))))
