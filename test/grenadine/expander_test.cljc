(ns grenadine.expander-test
  (:require [clojure.test :refer [deftest is testing]]
            [grenadine.expander :as expander]
            [grenadine.version :as version]))

(def base-repo
  {'fake/clojure {{:fkn/version "1.9.0"}
                  [['fake/spec.alpha {:fkn/version "0.1.124"}]
                   ['fake/core.specs.alpha {:fkn/version "0.1.10"}]]}
   'fake/spec.alpha {{:fkn/version "0.1.124"} nil
                     {:fkn/version "0.1.1"} nil}
   'fake/core.specs.alpha {{:fkn/version "0.1.10"} nil}
   'e1/a {{:fkn/version "1"} [['e1/b {:fkn/version "1"}]
                               ['e1/c {:fkn/version "2"}]]}
   'e1/b {{:fkn/version "1"} [['e1/c {:fkn/version "1"}]]}
   'e1/c {{:fkn/version "1"} nil
          {:fkn/version "2"} nil}
   'opt/a {{:fkn/version "1"} [['opt/b {:fkn/version "1"
                                         :optional true}]
                                ['opt/c {:fkn/version "1"}]]}
   'opt/b {{:fkn/version "1"} nil}
   'opt/c {{:fkn/version "1"} nil}})

(defn- expansion
  ([repo deps] (expansion repo deps {}))
  ([repo deps opts]
   (expander/expand-deps
    deps
    (merge
     {:coord-id (fn [_ coordinate]
                  (select-keys coordinate [:fkn/version]))
      :known-coordinate? #(contains? % :fkn/version)
      :compare-versions
      (fn [_ left right]
        (version/compare-versions (:fkn/version left)
                                  (:fkn/version right)))
      :coord-deps
      (fn [lib coordinate]
        (remove
         (fn [[_ child]] (:optional child))
         (get-in repo [lib (select-keys coordinate [:fkn/version])]))) }
     opts))))

(defn- libs
  ([repo deps] (:libs (expansion repo deps)))
  ([repo deps opts] (:libs (expansion repo deps opts))))

(defn- lib-versions
  [lib-map]
  (reduce
   (fn [result [lib coordinate]]
     (assoc result (keyword (name lib)) (:fkn/version coordinate)))
   {}
   lib-map))

(deftest expands-tools-deps-semantics
  (testing "top dependencies, optional dependencies, and transitive expansion"
    (is (= #{'opt/b}
           (set (keys (libs base-repo
                            {'opt/b {:fkn/version "1"
                                     :optional true}})))))
    (is (= #{'opt/a 'opt/c}
           (set (keys (libs base-repo
                            {'opt/a {:fkn/version "1"}})))))
    (is (= #{'fake/clojure 'fake/spec.alpha 'fake/core.specs.alpha}
           (set (keys (libs base-repo
                            {'fake/clojure {:fkn/version "1.9.0"}}))))))

  (testing "top dependency, override, and default precedence"
    (is (= "0.1.124"
           (get-in (libs base-repo
                         {'fake/clojure {:fkn/version "1.9.0"}})
                   ['fake/spec.alpha :fkn/version])))
    (is (= "0.1.1"
           (get-in (libs base-repo
                         {'fake/clojure {:fkn/version "1.9.0"}
                          'fake/spec.alpha {:fkn/version "0.1.1"}})
                   ['fake/spec.alpha :fkn/version])))
    (is (= "0.1.1"
           (get-in (libs base-repo
                         {'fake/clojure {:fkn/version "1.9.0"}}
                         {:override-deps
                          {'fake/spec.alpha {:fkn/version "0.1.1"}}})
                   ['fake/spec.alpha :fkn/version])))
    (is (= "1.9.0"
           (get-in (libs base-repo
                         {'fake/clojure nil}
                         {:default-deps
                          {'fake/clojure {:fkn/version "1.9.0"}}})
                   ['fake/clojure :fkn/version]))))

  (testing "newest transitive coordinate wins"
    (is (= {:a "1" :b "1" :c "2"}
           (lib-versions
            (libs base-repo {'e1/a {:fkn/version "1"}}))))))

(deftest cuts-orphans-and-expands-selected-children
  (let [repo
        {'e2/a {{:fkn/version "1"} [['e2/b {:fkn/version "1"}]
                                     ['e2/c {:fkn/version "2"}]]}
         'e2/b {{:fkn/version "1"} [['e2/c {:fkn/version "1"}]]}
         'e2/c {{:fkn/version "1"} [['e2/x {:fkn/version "2"}]]
                {:fkn/version "2"} nil}
         'e2/x {{:fkn/version "2"} nil}}]
    (is (= {:a "1" :b "1" :c "2"}
           (lib-versions (libs repo {'e2/a {:fkn/version "1"}})))))
  (let [repo
        {'e3/a {{:fkn/version "1"} [['e3/b {:fkn/version "1"}]
                                     ['e3/c {:fkn/version "2"}]]}
         'e3/b {{:fkn/version "1"} [['e3/c {:fkn/version "1"}]]}
         'e3/c {{:fkn/version "1"} nil
                {:fkn/version "2"} [['e3/d {:fkn/version "1"}]]}
         'e3/d {{:fkn/version "1"} nil}}]
    (is (= {:a "1" :b "1" :c "2" :d "1"}
           (lib-versions (libs repo {'e3/a {:fkn/version "1"}})))))
  (let [repo
        {'cut/a {{:fkn/version "1"} [['cut/b {:fkn/version "1"}]
                                      ['cut/c {:fkn/version "1"}]]}
         'cut/b {{:fkn/version "1"} [['cut/x {:fkn/version "2"}]]
                 {:fkn/version "2"} [['cut/x {:fkn/version "3"}]]}
         'cut/c {{:fkn/version "1"} [['cut/b {:fkn/version "2"}]]}
         'cut/x {{:fkn/version "2"} [['cut/y {:fkn/version "1"}]]
                 {:fkn/version "3"} [['cut/z {:fkn/version "1"}]]}
         'cut/y {{:fkn/version "1"} nil}
         'cut/z {{:fkn/version "1"} nil}}
        versions
        (lib-versions (libs repo {'cut/a {:fkn/version "1"}}))]
    (is (= "2" (:b versions)))
    (is (= "3" (:x versions)))
    (is (nil? (:y versions)))
    (is (= "1" (:z versions)))))

(deftest handles-cycles-and-path-specific-exclusions
  (let [repo
        {'c1/a {{:fkn/version "1"} [['c1/b {:fkn/version "1"}]]}
         'c1/b {{:fkn/version "1"} [['c1/a {:fkn/version "1"}]]}}]
    (is (= #{'c1/a 'c1/b}
           (set (keys (libs repo {'c1/a {:fkn/version "1"}}))))))
  (let [repo
        {'ex/a {{:fkn/version "1"}
                [['ex/b {:fkn/version "1" :exclusions ['ex/c]}]
                 ['ex/d {:fkn/version "1"}]]}
         'ex/b {{:fkn/version "1"} [['ex/c {:fkn/version "1"}]]}
         'ex/c {{:fkn/version "1"} nil}
         'ex/d {{:fkn/version "1"} [['ex/b {:fkn/version "1"}]]}}]
    (is (= #{'ex/a 'ex/b 'ex/c 'ex/d}
           (set (keys (libs repo {'ex/a {:fkn/version "1"}}))))))
  (let [repo
        {'ex2/a {{:fkn/version "1"}
                 [['ex2/b {:fkn/version "1" :exclusions ['ex2/c]}]]}
         'ex2/b {{:fkn/version "1"} [['ex2/c {:fkn/version "1"}]
                                      ['ex2/d {:fkn/version "1"}]]}
         'ex2/c {{:fkn/version "1"} nil}
         'ex2/d {{:fkn/version "1"} nil}}]
    (is (= #{'ex2/a 'ex2/b 'ex2/d}
           (set (keys (libs repo {'ex2/a {:fkn/version "1"}})))))))

(deftest returns-stable-order-trace-and-warnings
  (let [warnings (atom [])
        result
        (expansion base-repo
                   {'fake/clojure {:fkn/version "1.9.0"}
                    'bad/coordinate nil}
                   {:trace? true
                    :on-warning #(swap! warnings conj %)})]
    (is (= ['fake/clojure 'fake/spec.alpha 'fake/core.specs.alpha]
           (:order result)))
    (is (= (:warnings result) @warnings))
    (is (= :unsupported-coordinate
           (get-in result [:warnings 0 :warning])))
    (is (seq (get-in result [:trace :log])))
    (is (map? (get-in result [:trace :vmap])))))
