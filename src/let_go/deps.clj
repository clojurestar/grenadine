(ns let-go.deps
  "let-go dependency facade backed by Grenadine.

  Installed JARs are safely extracted and their source roots are appended to
  the active namespace resolver."
  (:require [clojure.string :as str]
            [grenadine.host.let-go :as host]
            [grenadine.runtime :as runtime]
            [let-go.deps.host :as native]))

(defonce ^:private basis (atom {:libs {} :classpath {} :classpath-roots []
                                :grenadine/loaded {}}))

(defn current-basis [] (runtime/current-basis basis))

(defn- add-roots! [roots] (native/add-source-roots! roots))

(defn add-libs
  ([libs] (add-libs libs nil))
  ([libs opts]
   (let [opts (or opts {})]
     (runtime/add-libs! basis add-roots! libs
                        (assoc opts :host (or (:host opts) (host/host)))))))

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
