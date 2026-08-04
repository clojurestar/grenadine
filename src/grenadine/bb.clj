(ns grenadine.bb
  "Babashka dependency installation backed by Grenadine."
  (:require [babashka.classpath :as classpath]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [grenadine.core :as grenadine]
            [grenadine.host.bb :as bb-host]))

(defonce ^:private basis
  (atom {:libs {} :classpath {} :classpath-roots [] :grenadine/loaded {}}))

(defn current-basis
  "Return the top-level coordinates added through this namespace."
  []
  (dissoc @basis :grenadine/loaded))

(defn- missing-libs
  [libs]
  (into {} (remove #(contains? (:grenadine/loaded @basis) (key %))) libs))

(defn- retained-warnings
  [libs]
  (->> libs
       (keep
        (fn [entry]
          (let [lib (key entry)
                requested (val entry)
                loaded (get-in @basis [:grenadine/loaded lib])]
            (when (and loaded (not= loaded requested))
              {:warning :loaded-lib-not-upgraded
               :lib lib
               :loaded loaded
               :requested requested}))))
       vec))

(defn add-libs
  "Resolve, install, and add deps.edn-style libraries to Babashka's classpath.
  Already-added top-level libraries are never upgraded."
  ([libs] (add-libs libs nil))
  ([libs opts]
   (let [opts (merge {:host (bb-host/host)
                      :mediation :tools-deps}
                     opts)
         missing (missing-libs libs)
         retained (retained-warnings libs)]
     (if (empty? missing)
       {:classpath []
        :lock nil
        :warnings retained}
       (let [result (grenadine/install! missing opts)]
         (when (seq (:classpath result))
           (classpath/add-classpath
            (str/join java.io.File/pathSeparator (:classpath result))))
         (swap! basis
                (fn [current]
                  (let [new-basis (:basis result)]
                    (-> (merge current new-basis)
                        (assoc :libs (merge (:libs current) (:libs new-basis)))
                        (assoc :classpath
                               (merge (:classpath current)
                                      (:classpath new-basis)))
                        (assoc :classpath-roots
                               (vec (distinct
                                     (concat (:classpath-roots current)
                                             (:classpath-roots new-basis)))))
                        (assoc :grenadine/loaded
                               (merge (:grenadine/loaded current) missing))))))
         (update result :warnings into retained))))))

(defn add-lib
  "Add one dependency to Babashka."
  ([lib coordinate] (add-lib lib coordinate nil))
  ([lib coordinate opts]
   (add-libs {lib coordinate} opts)))

(defn add-deps
  "Babashka-compatible entry point accepting a deps.edn map."
  ([deps-map] (add-deps deps-map nil))
  ([deps-map opts]
   (add-libs
    (:deps deps-map)
    (cond-> (or opts {})
      (:mvn/local-repo deps-map) (assoc :local-repo (:mvn/local-repo deps-map))
      (:mvn/repos deps-map) (assoc :repos (:mvn/repos deps-map))
      (:gitlibs/dir deps-map) (assoc :gitlibs-dir (:gitlibs/dir deps-map))))))

(defn sync-deps
  "Read a deps.edn-compatible file and add top-level libraries not yet present."
  ([] (sync-deps "deps.edn" nil))
  ([path] (sync-deps path nil))
  ([path opts]
   (let [index (max (or (str/last-index-of path "/") -1)
                    (or (str/last-index-of path "\\") -1))]
     (add-deps (edn/read-string (slurp path))
               (assoc (or opts {}) :base-dir
                      (if (neg? index) "." (subs path 0 index)))))))
