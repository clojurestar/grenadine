(ns grenadine.graph-test
  (:require [clojure.test :refer [deftest is testing]]
            [grenadine.graph :as graph]))

(def poms
  {["demo" "a" "1"]
   {:coords {:group "demo" :artifact "a" :version "1"}
    :deps [{:group "demo" :artifact "c" :version "1"}]}

   ["demo" "b" "1"]
   {:coords {:group "demo" :artifact "b" :version "1"}
    :deps [{:group "demo" :artifact "c" :version "2"}]}

   ["demo" "c" "1"]
   {:coords {:group "demo" :artifact "c" :version "1"}
    :deps [{:group "demo" :artifact "leaf" :version "1"}]}

   ["demo" "c" "2"]
   {:coords {:group "demo" :artifact "c" :version "2"}
    :deps [{:group "demo" :artifact "leaf" :version "2"}]}

   ["demo" "leaf" "1"]
   {:coords {:group "demo" :artifact "leaf" :version "1"} :deps []}

   ["demo" "leaf" "2"]
   {:coords {:group "demo" :artifact "leaf" :version "2"} :deps []}})

(defn pom-fn
  [coords]
  (get poms [(:group coords) (:artifact coords) (:version coords)]))

(def roots
  '{demo/a {:mvn/version "1"}
    demo/b {:mvn/version "1"}})

(defn selected-version
  [resolution artifact]
  (get-in resolution [:selected ["demo" artifact] :coords :version]))

(deftest mediates-versions
  (is (= "2" (selected-version
              (graph/resolve-graph roots
                                   {:pom-fn pom-fn :mediation :newest})
              "c")))
  (is (= "1" (selected-version
              (graph/resolve-graph roots
                                   {:pom-fn pom-fn :mediation :nearest})
              "c")))
  (is (= "2" (selected-version
              (graph/resolve-graph roots
                                   {:pom-fn pom-fn :mediation :tools-deps})
              "c"))))

(deftest tools-deps-honors-direct-pins
  (let [deps
        '{demo/b {:mvn/version "1"}
          demo/c {:mvn/version "1"}}]
    (is (= "1" (selected-version
                (graph/resolve-graph deps
                                     {:pom-fn pom-fn
                                      :mediation :tools-deps})
                "c")))
    (is (= "2" (selected-version
                (graph/resolve-graph deps
                                     {:pom-fn pom-fn
                                      :mediation :newest})
                "c")))))

(deftest provided-libraries-are-not-selected-or-expanded
  (doseq [mode [:newest :tools-deps]]
    (let [requested (atom [])
          resolution
          (graph/resolve-graph
           '{demo/application {:mvn/version "1"}
             org.clojure/spec.alpha {:mvn/version "0.1.143"}}
           {:mediation mode
            :provided-libs '#{org.clojure/clojure org.clojure/clojurescript}
            :pom-fn
            (fn [{:keys [group artifact] :as coords}]
              (swap! requested conj [group artifact])
              (cond
                (= [group artifact] ["demo" "application"])
                {:deps [{:group "org.clojure" :artifact "clojure"
                         :version "1.9.0"}]}

                (= [group artifact] ["org.clojure" "spec.alpha"])
                {:deps []}

                :else
                (throw (ex-info "provided dependency was expanded"
                                {:coords coords}))))})]
      (is (= #{["demo" "application"] ["org.clojure" "spec.alpha"]}
             (set (keys (:selected resolution)))))
      (is (= #{["demo" "application"] ["org.clojure" "spec.alpha"]}
             (set @requested))))))

(deftest filters-paths
  (testing "exclusions are path-specific"
    (let [deps
          '{demo/a {:mvn/version "1"
                    :exclusions #{demo/c}}
            demo/b {:mvn/version "1"}}
          resolution (graph/resolve-graph deps {:pom-fn pom-fn})]
      (is (= "2" (selected-version resolution "c")))
      (is (= 1 (count (filter #(= :excluded (:reason %))
                              (:omitted resolution)))))))
  (testing "optional and non-runtime scopes are omitted"
    (let [custom-poms
          (assoc poms ["demo" "a" "1"]
                 {:coords {:group "demo" :artifact "a" :version "1"}
                  :deps [{:group "demo" :artifact "c" :version "1"
                          :optional true}
                         {:group "demo" :artifact "leaf" :version "1"
                          :scope "test"}]})
          resolution
          (graph/resolve-graph
           '{demo/a {:mvn/version "1"}}
           {:pom-fn
            (fn [coords]
              (get custom-poms
                   [(:group coords) (:artifact coords) (:version coords)]))})]
      (is (= #{["demo" "a"]} (set (keys (:selected resolution)))))
      (is (= #{:optional :scope}
             (set (map :reason (:omitted resolution))))))))

(deftest prunes-the-losing-versions-subtree
  (let [custom-poms
        (-> poms
            (assoc ["demo" "c" "1"]
                   {:coords {:group "demo" :artifact "c" :version "1"}
                    :deps [{:group "demo" :artifact "only-old" :version "1"}]})
            (assoc ["demo" "only-old" "1"]
                   {:coords {:group "demo" :artifact "only-old" :version "1"}
                    :deps []}))
        resolution
        (graph/resolve-graph
         roots
         {:pom-fn
          (fn [coords]
            (get custom-poms
                 [(:group coords) (:artifact coords) (:version coords)]))
          :mediation :newest})]
    (is (= "2" (selected-version resolution "c")))
    (is (nil? (get-in resolution [:selected ["demo" "only-old"]])))))

(deftest tools-deps-narrows-exclusions-across-paths
  (let [custom-poms
        {["demo" "a" "1"]
         {:deps [{:group "demo" :artifact "b" :version "1"
                  :exclusions #{'demo/c}}
                 {:group "demo" :artifact "d" :version "1"}]}
         ["demo" "b" "1"]
         {:deps [{:group "demo" :artifact "c" :version "1"}]}
         ["demo" "c" "1"] {:deps []}
         ["demo" "d" "1"]
         {:deps [{:group "demo" :artifact "b" :version "1"}]}}
        resolution
        (graph/resolve-graph
         '{demo/a {:mvn/version "1"}}
         {:pom-fn
          (fn [coords]
            (get custom-poms
                 [(:group coords) (:artifact coords) (:version coords)]))
          :mediation :tools-deps})]
    (is (= #{["demo" "a"] ["demo" "b"]
             ["demo" "c"] ["demo" "d"]}
           (set (keys (:selected resolution)))))))

(deftest classifiers-have-distinct-library-identities
  (let [custom-poms
        {["demo" "root" "1"]
         {:deps [{:group "demo" :artifact "native" :classifier "linux"
                  :version "1"}]}
         ["demo" "native" "1"] {:deps []}}
        pom-fn (fn [{:keys [group artifact version]}]
                 (get custom-poms [group artifact version]))
        resolution
        (graph/resolve-graph '{demo/root {:mvn/version "1"}
                               demo/native {:mvn/version "1"}
                               demo/native$linux {:mvn/version "1"}}
                             {:pom-fn pom-fn})]
    (is (= #{["demo" "root"]
             ["demo" "native"]
             ["demo" "native$linux"]}
           (set (keys (:selected resolution)))))
    (is (= #{["demo" "root"]}
           (-> (graph/resolve-graph
                '{demo/root {:mvn/version "1"
                             :exclusions #{demo/native}}}
                {:pom-fn pom-fn})
               :selected keys set)))))
