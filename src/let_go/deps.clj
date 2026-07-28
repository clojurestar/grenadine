(ns let-go.deps
  "let-go dependency facade backed by Grenadine.

  let-go currently exposes load-path mutation to Go embedders through
  `SetLoadPath`, but not as a language function. Until that hook is exposed,
  pass `:add-roots!` in opts."
  (:require [grenadine.runtime :as runtime]))

(defonce ^:private basis (atom {}))

(defn current-basis [] (runtime/current-basis basis))

(defn- missing-hook
  [_roots]
  (throw
   (ex-info
    "let-go needs :add-roots! (typically backed by the embedding SetLoadPath)"
    {:type :grenadine.runtime/missing-load-path-hook})))

(defn add-libs
  ([libs] (add-libs libs nil))
  ([libs opts]
   (runtime/add-libs! basis (or (:add-roots! opts) missing-hook)
                      libs (dissoc opts :add-roots!))))

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
