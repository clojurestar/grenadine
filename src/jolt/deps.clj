(ns jolt.deps
  "Jolt dependency facade backed by Grenadine.

  This namespace intentionally uses Jolt's established `jolt.deps` API name.
  Pass a Jolt-native Grenadine `:host` in opts."
  (:require [grenadine.runtime :as runtime]
            [jolt.host]))

(defonce ^:private basis (atom {}))

(defn current-basis [] (runtime/current-basis basis))

(defn- add-roots!
  [roots]
  (let [source-roots (resolve 'jolt.host/source-roots)
        set-source-roots! (resolve 'jolt.host/set-source-roots!)]
    (when-not (and source-roots set-source-roots!)
      (throw (ex-info "Jolt does not expose its source-root mutation hooks"
                      {:type :grenadine.runtime/missing-load-path-hook})))
    (let [current (vec (source-roots))]
      (set-source-roots!
       (vec (distinct (concat current roots)))))))

(defn add-libs
  ([libs] (add-libs libs nil))
  ([libs opts] (runtime/add-libs! basis add-roots! libs opts)))

(defn add-lib
  ([lib coordinate] (add-lib lib coordinate nil))
  ([lib coordinate opts] (add-libs {lib coordinate} opts)))

(defn add-deps
  ([deps-map] (add-deps deps-map nil))
  ([deps-map opts] (add-libs (:deps deps-map) opts)))

(defn sync-deps
  ([] (sync-deps "deps.edn" nil))
  ([path] (sync-deps path nil))
  ([path opts]
   (add-deps (read-string (slurp path)) opts)))
