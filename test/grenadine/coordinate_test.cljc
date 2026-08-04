(ns grenadine.coordinate-test
  (:require [clojure.test :refer [deftest is testing]]
            [grenadine.basis :as basis]
            [grenadine.coordinate :as coordinate]
            [grenadine.gitlibs :as gitlibs]
            [grenadine.lock :as lock]
            [grenadine.test-support :refer [throws?]]
            #?(:glj [grenadine.host.glojure :as host]
               :lg [grenadine.host.let-go :as host]
               :jolt [grenadine.coordinate]
               :clj [grenadine.host.jvm :as host]
               :bb [grenadine.host.bb :as host])))

(deftest coordinate-types
  (is (= :mvn (coordinate/coordinate-type {:mvn/version "1"})))
  (is (= :git (coordinate/coordinate-type {:git/sha (apply str (repeat 40 "a"))})))
  (is (= :local (coordinate/coordinate-type {:local/root "."})))
  (is (throws? (coordinate/coordinate-type
                {:mvn/version "1" :local/root "."}))))

(deftest git-url-inference
  (is (= "https://github.com/cognitect-labs/test-runner.git"
         (coordinate/infer-git-url 'io.github.cognitect-labs/test-runner)))
  (is (= "https://git.sr.ht/~example/demo"
         (coordinate/infer-git-url (symbol "ht.sr.~example" "demo")))))

(deftest git-cache-configuration
  (let [env (atom {"GRENADINE_GITLIBS" "/grenadine"
                   "GITLIBS" "/tools"})
        host {:getenv #(get @env %) :home-dir (constantly "/home/test")}]
    (is (= "/explicit" (gitlibs/gitlibs-dir
                         {:host host :gitlibs-dir "/explicit"})))
    (is (= "/grenadine" (gitlibs/gitlibs-dir {:host host})))
    (swap! env dissoc "GRENADINE_GITLIBS")
    (is (= "/tools" (gitlibs/gitlibs-dir {:host host})))
    (reset! env {})
    (is (= "/home/test/.gitlibs" (gitlibs/gitlibs-dir {:host host}))))
  (is (= "https/github.com/example/project"
         (gitlibs/clean-url "https://github.com/example/project.git"))))

#?(:lg nil
   :jolt nil
   :clj
   (deftest local-basis
     (let [runtime (host/host)
           root ((:canonical-path runtime) "test/fixtures/local-lib")
           result
           (basis/calc-basis
            {:deps {'example/local {:local/root "test/fixtures/local-lib"}}}
            {:host runtime :base-dir "."})]
       (is (= {:local/root root
               :deps/root root
               :deps/manifest :deps
               :paths [(str root "/src")]
               :parents #{[]}}
              (get-in result [:libs 'example/local])))
       (is (= [(str root "/src")] (:classpath-roots result)))))

   :bb
   (deftest local-basis
     (let [runtime (host/host)
           root ((:canonical-path runtime) "test/fixtures/local-lib")
           result
           (basis/calc-basis
            {:deps {'example/local {:local/root "test/fixtures/local-lib"}}}
            {:host runtime :base-dir "."})]
       (is (= root (get-in result [:libs 'example/local :local/root])))
       (is (= [(str root "/src")] (:classpath-roots result)))
       (is (= 2 (get-in result [:grenadine/lock :lock/version])))
       (is (= [(str root "/src")]
              (lock/lock->classpath
               (:grenadine/lock result)
               {:host runtime :local-repo ".cache/m2"}))))))
