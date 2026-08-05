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

(deftest emits-classified-artifact-paths
  (let [result
        (lock/emit-lock
         {:selected
          {["org.lwjgl" "lwjgl-stb$natives-linux"]
           {:coords {:group "org.lwjgl" :artifact "lwjgl-stb"
                     :classifier "natives-linux" :version "3.2.3"}}}}
         {:pom-fn (fn [_] {:packaging "jar"})})]
    (is (= "natives-linux" (get-in result [:artifacts 0 :classifier])))
    (is (= "org/lwjgl/lwjgl-stb/3.2.3/lwjgl-stb-3.2.3-natives-linux.jar"
           (get-in result [:artifacts 0 :path])))))

(deftest emits-timestamped-snapshots-under-the-base-version
  (is (= (str "metosin/malli/0.0.1-SNAPSHOT/"
              "malli-0.0.1-20200715.082439-21.jar")
         (lock/artifact-path
          {:group "metosin" :artifact "malli"
           :version "0.0.1-20200715.082439-21"}))))

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
