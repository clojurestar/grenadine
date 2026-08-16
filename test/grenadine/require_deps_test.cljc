(ns grenadine.require-deps-test
  (:require [clojure.test :refer [deftest is testing]]
            [grenadine.require-deps :as required]
            [grenadine.test-support :refer [throws?]]))

(def gist-id "f70409675d234aa4f2fe379cd975a4f5")
(def revision "0123456789abcdef0123456789abcdef01234567")

(defn fake-host
  ([source] (fake-host source {}))
  ([source initial-files]
   (let [files (atom initial-files)
         downloads (atom [])
         moves (atom [])]
     {:files files
      :downloads downloads
      :moves moves
      :host
      {:home-dir (constantly "/home/tester")
       :file-exists? #(contains? @files %)
       :mkdirs! (constantly nil)
       :delete! #(do (swap! files dissoc %) nil)
       :read-text #(get @files %)
       :download!
       (fn [url path]
         (swap! downloads conj url)
         (when source
           (swap! files assoc path source)
           true))
       :atomic-move!
       (fn [from to]
         (swap! moves conj [from to])
         (swap! files
                (fn [current]
                  (-> current
                      (assoc to (get current from))
                      (dissoc from))))
         nil)}})))

(deftest coordinate-parsing
  (testing "Maven"
    (is (= {:provider :mvn
            :lib 'dev.weavejester/medley
            :version "1.10.0"
            :namespace 'medley.core}
           (select-keys
            (required/parse-coordinate
             "mvn:dev.weavejester/medley@1.10.0/medley.core")
            [:provider :lib :version :namespace]))))
  (testing "Gist forms"
    (is (= {:owner "ingydotnet" :id gist-id
            :filename nil :revision nil}
           (select-keys
            (required/parse-coordinate (str "gist:ingydotnet/" gist-id))
            [:owner :id :filename :revision])))
    (is (= "mathy.clj"
           (:filename
            (required/parse-coordinate
             (str "gist:ingydotnet/" gist-id "/mathy.clj")))))
    (is (= revision
           (:revision
            (required/parse-coordinate
             (str "gist:ingydotnet/" gist-id "@" revision)))))
    (is (= "mathy.cljc"
           (:filename
            (required/parse-coordinate
             (str "gist:ingydotnet/" gist-id "/mathy.cljc@" revision)))))
    (is (= [:gist "ingydotnet" gist-id nil nil]
           (:identity
            (required/parse-coordinate
             (str "https://gist.github.com/ingydotnet/" gist-id))))))
  (testing "malformed and unsafe forms"
    (doseq [coordinate
            ["mvn:medley@1.10.0/medley.core"
             "gist:ingydotnet/not-a-hex-id"
             (str "gist:ingydotnet/" gist-id "/../mathy.clj")
             (str "gist:ingydotnet/" gist-id "/mathy.cljs")
             (str "gist:ingydotnet/" gist-id "@abc")
             "https://example.com/library.clj"]]
      (is (throws? (required/parse-coordinate coordinate))))))

(deftest libspec-validation
  (let [coordinate (str "gist:ingydotnet/" gist-id)]
    (is (= {:as 'm :refer '[add sub]}
           (select-keys
            (required/parse-libspec
             [coordinate :as 'm :refer '[add sub]])
            [:as :refer])))
    (doseq [libspec
            [[coordinate :refer :all]
             [coordinate :rename '{add plus}]
             [coordinate :unknown true]
             [coordinate :as :m]
             [coordinate :as 'qualified/m]
             [coordinate :refer '(add sub)]
             [coordinate :refer []]
             [coordinate :refer '[add add]]
             [coordinate :refer '[add :sub]]
             [coordinate :as 'm :as 'again]
             [coordinate :as]]]
      (is (throws? (required/parse-libspec libspec))))))

(deftest raw-url-construction
  (is (= (str "https://gist.githubusercontent.com/ingydotnet/" gist-id
              "/raw")
         (required/gist-raw-url
          (required/parse-coordinate (str "gist:ingydotnet/" gist-id)))))
  (is (= (str "https://gist.githubusercontent.com/ingydotnet/" gist-id
              "/raw/mathy.clj")
         (required/gist-raw-url
          (required/parse-coordinate
           (str "gist:ingydotnet/" gist-id "/mathy.clj")))))
  (is (= (str "https://gist.githubusercontent.com/ingydotnet/" gist-id
              "/raw/" revision)
         (required/gist-raw-url
          (required/parse-coordinate
           (str "gist:ingydotnet/" gist-id "@" revision)))))
  (is (= (str "https://gist.githubusercontent.com/ingydotnet/" gist-id
              "/raw/" revision "/mathy.cljc")
         (required/gist-raw-url
          (required/parse-coordinate
           (str "gist:ingydotnet/" gist-id "/mathy.cljc@" revision))))))

(deftest cache-layout-and-acquisition
  (let [latest (required/parse-coordinate
                (str "gist:ingydotnet/" gist-id "/mathy.clj"))
        pinned (required/parse-coordinate
                (str "gist:ingydotnet/" gist-id "/mathy.clj@" revision))
        source "(ns mathy)\n(defn add [a b] (+ a b))\n"
        latest-host (fake-host source)
        latest-path (str "/cache/gist/ingydotnet/" gist-id
                         "/latest/mathy.clj")]
    (is (= (str "/home/tester/.cache/clojurestar/gist/ingydotnet/"
                gist-id "/latest/mathy.clj")
           (required/gist-cache-path (:host latest-host) {} latest)))
    (is (= latest-path
           (:path (required/acquire-gist! (:host latest-host)
                                          {:cache-dir "/cache"} latest))))
    (is (= source (get @(:files latest-host) latest-path)))
    (is (= 1 (count @(:downloads latest-host))))
    (is (= [[(str latest-path ".grenadine.part") latest-path]]
           @(:moves latest-host)))
    (required/acquire-gist! (:host latest-host) {:cache-dir "/cache"} latest)
    (is (= 2 (count @(:downloads latest-host)))
        "latest is refreshed when acquisition is requested again")
    (let [pinned-path (str "/cache/gist/ingydotnet/" gist-id "/"
                           revision "/mathy.clj")
          pinned-host (fake-host nil {pinned-path source})
          result (required/acquire-gist! (:host pinned-host)
                                         {:cache-dir "/cache"} pinned)]
      (is (:cached? result))
      (is (= source (:source result)))
      (is (empty? @(:downloads pinned-host)))
      (is (empty? @(:moves pinned-host))))))

(deftest source-and-http-validation
  (let [coordinate
        (required/parse-coordinate (str "gist:ingydotnet/" gist-id))]
    (is (= 'mathy (required/gist-namespace coordinate '(ns mathy))))
    (is (throws? (required/gist-namespace coordinate '(println 42))))
    (is (throws? (required/gist-namespace coordinate '(ns))))
    (let [host (fake-host nil)]
      (try
        (required/acquire-gist! (:host host) {:cache-dir "/cache"} coordinate)
        (is false "expected HTTP failure")
        (catch #?(:glj go/any :jolt Throwable :default Throwable) error
          (is (= :clojurestar.deps/gist-http-failure
                 (:type (ex-data error)))))))))

(deftest options-and-conflicts
  (is (= {:cache-dir "/cache" :mvn/local-repo "/m2"}
         (required/parse-options
          {:cache-dir "/cache" :mvn/local-repo "/m2"})))
  (is (throws? (required/parse-options {:unknown true})))
  (is (throws? (required/parse-options {:cache-dir ""})))
  (try
    (required/namespace-conflict!
     'mathy {:coordinate "gist:one/aaaa"} {:coordinate "gist:two/bbbb"})
    (is false "expected namespace conflict")
    (catch #?(:glj go/any :jolt Throwable :default Throwable) error
      (is (= :clojurestar.deps/namespace-conflict
             (:type (ex-data error)))))))
