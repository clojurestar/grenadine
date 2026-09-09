(ns grenadine.tree-test
  (:require [clojure.test :refer [deftest is testing]]
            [grenadine.tree :as tree]))

(deftest renders-sorted-shared-branches
  (let [graph '{:roots [demo/z demo/a]
                :children {demo/a #{demo/leaf demo/branch}
                           demo/branch #{demo/leaf}
                           demo/z #{demo/branch}}}]
    (is (= ["├── demo/a"
            "│   ├── demo/branch"
            "│   │   └── demo/leaf"
            "│   └── demo/leaf (already shown)"
            "└── demo/z"
            "    └── demo/branch (already shown)"]
           (tree/lines graph str)))))

(deftest renders-empty-single-and-cyclic-graphs
  (is (= [] (tree/lines {:roots [] :children {}} str)))
  (is (= ["└── demo/a 1"]
         (tree/lines '{:roots [demo/a]} #(str % " 1"))))
  (is (= ["└── demo/a"
          "    └── demo/b"
          "        └── demo/a (cycle)"]
         (tree/lines '{:roots [demo/a]
                       :children {demo/a [demo/b] demo/b [demo/a]}}
                     str))))

(deftest omits-clojure-branches-from-display
  (let [graph '{:roots [org.clojure/clojure demo/a]
                :children {demo/a [org.clojure/clojure
                                    org.clojure/spec.alpha]
                           org.clojure/clojure [org.clojure/core.specs.alpha
                                                org.clojure/spec.alpha]}}]
    (is (= ["└── demo/a"
            "    └── org.clojure/spec.alpha"]
           (tree/lines graph str)))
    (is (= [] (tree/lines (assoc graph :roots '[org.clojure/clojure])
                         str)))))

(deftest follows-selected-coordinates
  (let [requested (atom [])
        libs '{demo/a {:mvn/version "1"}
               demo/b {:mvn/version "2"}
               demo/new {:mvn/version "1"}}
        graph
        (tree/dependency-graph
         '[[demo/a {:mvn/version "1"}]] libs
         (fn [lib coord]
           (swap! requested conj [lib coord])
           (case lib
             demo/a '[[demo/b {:mvn/version "1"}]
                      [demo/omitted {:mvn/version "1"}]]
             demo/b '[[demo/new {:mvn/version "1"}]]
             [])))]
    (is (= '{:roots [demo/a]
             :children {demo/a #{demo/b}
                        demo/b #{demo/new}
                        demo/new #{}}}
           graph))
    (is (= (set libs) (set @requested)))))

(deftest combines-path-specific-exclusions
  (let [libs '{demo/a {:mvn/version "1"}
               demo/b {:mvn/version "1"}
               demo/shared {:mvn/version "1"}
               demo/leaf {:mvn/version "1"}
               demo/leaf$tests {:mvn/version "1"}}
        children
        '{demo/a [[demo/shared {:mvn/version "1"
                               :exclusions #{demo/leaf}}]]
          demo/b [[demo/shared {:mvn/version "1"}]]
          demo/shared [[demo/leaf {:mvn/version "1"}]
                       [demo/leaf$tests {:mvn/version "1"}]]}
        graph (fn [roots]
                (tree/dependency-graph roots libs
                                       (fn [lib _] (get children lib []))))]
    (testing "an excluded path cannot hide edges valid on another path"
      (is (= '#{demo/leaf demo/leaf$tests}
             (get-in (graph '[[demo/a {}] [demo/b {}]])
                     [:children 'demo/shared]))))
    (testing "base-library exclusions also exclude classifiers"
      (is (= #{}
             (get-in (graph '[[demo/a {}]])
                     [:children 'demo/shared]))))
    (testing "root exclusions apply to descendants, not the root itself"
      (is (= '{:roots [demo/a] :children {demo/a #{}}}
             (graph '[[demo/a {:exclusions #{demo/a demo/shared}}]]))))))

(deftest collects-cycles-and-skips-unselected-roots
  (is (= '{:roots [demo/a]
           :children {demo/a #{demo/b} demo/b #{demo/a}}}
         (tree/dependency-graph
          '[[demo/provided {}] [demo/a {}]]
          '{demo/a {} demo/b {}}
          (fn [lib _]
            (case lib
              demo/a '[[demo/b {}]]
              demo/b '[[demo/a {}]]))))))
