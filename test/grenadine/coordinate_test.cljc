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

(deftest splits-legacy-and-classified-library-names
  (is (= ["hiccup" "hiccup" nil]
         (coordinate/split-lib 'hiccup)))
  (is (= ["org.lwjgl" "lwjgl-stb" "natives-macos"]
         (coordinate/split-lib 'org.lwjgl/lwjgl-stb$natives-macos)))
  (is (= 'org.lwjgl/lwjgl-stb$natives-macos
         (coordinate/lib-symbol "org.lwjgl" "lwjgl-stb" "natives-macos")))
  (is (= 'org.lwjgl/lwjgl-stb
         (coordinate/base-lib 'org.lwjgl/lwjgl-stb$natives-macos)))
  (is (= "org.lwjgl/lwjgl-stb"
         (coordinate/base-lib "org.lwjgl/lwjgl-stb$natives-macos"))))

(deftest canonicalizes-maven-version-ranges
  (let [url "https://repo.example/demo/a/maven-metadata.xml"
        host {:http-get
              (fn [requested]
                (if (= requested url)
                  {:status 200
                   :body (str "<metadata><versioning><versions>"
                              "<version>1.0</version>"
                              "<version>1.9</version>"
                              "<version>2.0</version>"
                              "</versions></versioning></metadata>")}
                  {:status 404}))
              :bytes->utf8 identity}]
    (is (= {:mvn/version "1.9"}
           (coordinate/canonicalize
            'demo/a {:mvn/version "[1.0,2.0)"}
            {:host host :repos ["https://repo.example"]})))
    (is (= {:mvn/version "1.5"}
           (coordinate/canonicalize
            'demo/a {:mvn/version "[1.5]"}
            {:host {} :repos []})))))

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

#?(:lg nil
   :jolt nil
   :default
   (deftest maven-classifiers-remain-distinct-in-bases-and-locks
     (let [runtime (host/host)
           result
           (basis/calc-basis
            {:deps {'hiccup {:mvn/version "1.0"}
                    'org.lwjgl/lwjgl-stb$natives-macos
                    {:mvn/version "3.2.3"}}}
            {:host runtime
             :local-repo "/tmp/grenadine-classifier-m2"
             :fetch-artifacts? false
             :pom-fn (fn [_] {:deps []})})]
       (is (= #{'hiccup 'org.lwjgl/lwjgl-stb$natives-macos}
              (set (keys (:libs result)))))
       (is (= (str "/tmp/grenadine-classifier-m2/"
                   "org/lwjgl/lwjgl-stb/3.2.3/"
                   "lwjgl-stb-3.2.3-natives-macos.jar")
              (get-in result
                      [:libs 'org.lwjgl/lwjgl-stb$natives-macos :paths 0])))
       (is (= "natives-macos"
              (get-in result [:grenadine/lock :artifacts 1 :classifier]))))
     (let [runtime (host/host)
           result
           (basis/calc-basis
            {:deps {'demo/root {:mvn/version "1"
                                :exclusions #{'demo/native}}}}
            {:host runtime
             :local-repo "/tmp/grenadine-classifier-m2"
             :fetch-artifacts? false
             :pom-fn
             (fn [{:keys [artifact]}]
               {:deps (if (= artifact "root")
                        [{:group "demo" :artifact "native"
                          :classifier "linux" :version "1"}]
                        [])})})]
       (is (= #{'demo/root} (set (keys (:libs result))))))))
