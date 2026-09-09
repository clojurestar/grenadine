(ns grenadine.tree
  "Selected dependency edges and portable tree rendering."
  (:require [grenadine.coordinate :as coordinate]))

(defn dependency-graph
  "Collect edges reachable with path-specific exclusions.

  `children-fn` returns normalized dependencies of a selected coordinate,
  already filtered for scope and optionality. Merge valid edges across paths
  so displaying a shared library once does not hide its dependencies."
  [roots libs children-fn]
  (let [roots (filterv #(contains? libs (first %)) roots)
        initial (mapv (fn [[lib coord]]
                        [lib (set (:exclusions coord))]) roots)]
    (loop [pending initial
           visited #{}
           edges {}]
      (if (empty? pending)
        {:roots (mapv first roots) :children edges}
        (let [[lib excluded :as state] (peek pending)
              pending (pop pending)]
          (if (contains? visited state)
            (recur pending visited edges)
            (let [children
                  (filterv
                   (fn [[child _]]
                     (and (contains? libs child)
                          (not (contains? excluded
                                          (coordinate/base-lib child)))))
                   (children-fn lib (get libs lib)))]
              (recur
               (into pending
                     (map (fn [[child coord]]
                            [child (into excluded (:exclusions coord))])
                          children))
               (conj visited state)
               (update edges lib (fnil into #{}) (map first children))))))))))

(defn visible?
  [lib]
  (not= 'org.clojure/clojure lib))

(defn- branches
  [libs prefix ancestors]
  (let [libs (->> libs
                  (filter visible?)
                  distinct
                  (sort-by str)
                  vec)
        last-index (dec (count libs))]
    (mapv (fn [index lib]
            [lib prefix (= index last-index) ancestors])
          (range (count libs)) libs)))

(defn nodes
  "Walk visible branches in display order, expanding each library once."
  [{:keys [roots children]}]
  (loop [pending (vec (reverse (branches roots "" #{})))
         shown #{}
         result []]
    (if (empty? pending)
      result
      (let [[lib prefix last? ancestors] (peek pending)
            cycle? (contains? ancestors lib)
            repeated? (contains? shown lib)
            node {:lib lib
                  :prefix (str prefix (if last? "└── " "├── "))
                  :suffix (cond cycle? " (cycle)"
                                repeated? " (already shown)"
                                :else "")}
            descendants
            (when-not (or cycle? repeated?)
              (branches (get children lib)
                        (str prefix (if last? "    " "│   "))
                        (conj ancestors lib)))]
        (recur (into (pop pending) (reverse descendants))
               (conj shown lib)
               (conj result node))))))

(defn lines
  "Render sorted branches once, omitting the org.clojure/clojure branch."
  ([graph label] (lines graph label identity))
  ([graph label style-suffix]
   (lines graph label style-suffix identity))
  ([graph label style-suffix style-prefix]
   (mapv (fn [{:keys [lib prefix suffix]}]
           (str (style-prefix prefix) (label lib) (style-suffix suffix)))
         (nodes graph))))
