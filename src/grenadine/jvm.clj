(ns grenadine.jvm
  "Dynamic dependency loading for JVM Clojure."
  (:require [clojure.java.io :as io]
            [grenadine.core :as grenadine]
            [grenadine.host.jvm :as host]))

(defonce ^:private basis (atom {}))

(defn current-basis [] @basis)

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
  (into {} (remove #(contains? @basis (key %))) libs))

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
         (swap! basis merge missing)
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
               (assoc :repos (:mvn/repos deps-map))))))

(defn sync-deps
  ([] (sync-deps "deps.edn" nil))
  ([path] (sync-deps path nil))
  ([path opts] (add-deps (read-string (slurp path)) opts)))
