(ns grenadine.bb
  "Babashka dependency installation backed by Grenadine."
  (:require [babashka.classpath :as classpath]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [grenadine.core :as grenadine]
            [grenadine.host.bb :as bb-host]))

(defonce ^:private basis (atom {}))

(defn current-basis
  "Return the top-level coordinates added through this namespace."
  []
  @basis)

(defn- missing-libs
  [libs]
  (into {} (remove #(contains? @basis (key %))) libs))

(defn- retained-warnings
  [libs]
  (->> libs
       (keep
        (fn [entry]
          (let [lib (key entry)
                requested (val entry)
                loaded (get @basis lib)]
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
         (swap! basis merge missing)
         (update result :warnings into retained))))))

(defn add-lib
  "Add one Maven library to Babashka."
  ([lib coordinate] (add-lib lib coordinate nil))
  ([lib coordinate opts]
   (add-libs {lib coordinate} opts)))

(defn add-deps
  "Babashka-compatible entry point accepting a deps.edn map."
  ([deps-map] (add-deps deps-map nil))
  ([deps-map opts]
   (add-libs (:deps deps-map) opts)))

(defn sync-deps
  "Read a deps.edn-compatible file and add top-level libraries not yet present."
  ([] (sync-deps "deps.edn" nil))
  ([path] (sync-deps path nil))
  ([path opts]
   (add-libs (:deps (edn/read-string (slurp path))) opts)))
