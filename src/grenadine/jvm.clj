(ns grenadine.jvm
  "Dynamic dependency loading for JVM Clojure."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [grenadine.core :as grenadine]
            [grenadine.host.jvm :as host]))

(defonce ^:private basis
  (atom {:libs {} :classpath {} :classpath-roots [] :grenadine/loaded {}}))

(defn current-basis [] (dissoc @basis :grenadine/loaded))

(defn- add-loader-url [url]
  (let [loader
        (loop [loader (.getContextClassLoader (Thread/currentThread))]
          (let [parent (.getParent loader)]
            (if (instance? clojure.lang.DynamicClassLoader parent)
              (recur parent)
              loader)))]
    (if (instance? clojure.lang.DynamicClassLoader loader)
      (.addURL ^clojure.lang.DynamicClassLoader loader url)
      (throw (IllegalAccessError.
              "Context classloader is not a DynamicClassLoader")))))

(defn- missing-libs [libs]
  (into {} (remove #(contains? (:grenadine/loaded @basis) (key %))) libs))

(defn add-libs
  ([libs] (add-libs libs nil))
  ([libs opts]
   (let [missing (missing-libs libs)]
     (if (empty? missing)
       {:classpath [] :lock nil :warnings []}
       (let [opts (or opts {})
             result (grenadine/install!
                     missing
                     (assoc opts
                            :host (or (:host opts) (host/host))
                            :mediation (or (:mediation opts) :tools-deps)))]
         (doseq [jar (:classpath result)]
           (add-loader-url (.toURL (.toURI (io/file jar)))))
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
         result)))))

(defn add-lib
  ([lib coordinate] (add-lib lib coordinate nil))
  ([lib coordinate opts] (add-libs {lib coordinate} opts)))

(defn add-deps
  ([deps-map] (add-deps deps-map nil))
  ([deps-map opts]
   (add-libs (or (:deps deps-map) {})
             (cond-> (or opts {})
               (:mvn/local-repo deps-map)
               (assoc :local-repo (:mvn/local-repo deps-map))

               (:mvn/repos deps-map)
               (assoc :repos (:mvn/repos deps-map))

               (:gitlibs/dir deps-map)
               (assoc :gitlibs-dir (:gitlibs/dir deps-map))))))

(defn sync-deps
  ([] (sync-deps "deps.edn" nil))
  ([path] (sync-deps path nil))
  ([path opts]
   (let [index (max (or (str/last-index-of path "/") -1)
                    (or (str/last-index-of path "\\") -1))]
     (add-deps (read-string (slurp path))
               (assoc (or opts {}) :base-dir
                      (if (neg? index) "." (subs path 0 index)))))))
