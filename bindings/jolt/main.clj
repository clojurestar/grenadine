(require '[grenadine.runtime :as runtime]
         '[jolt.host :as host])

(def basis (atom {}))
(def medley-root (host/getenv "GRENADINE_MEDLEY_ROOT"))

(defn add-roots! [roots]
  (host/set-source-roots!
   (vec (distinct (concat (host/source-roots) roots)))))

(runtime/add-libs!
 basis
 add-roots!
 '{dev.weavejester/medley {:mvn/version "1.10.0"}}
 {:install-fn
  (fn [_libs _opts]
    {:source-roots [medley-root]
     :warnings []})})

(require '[medley.core :as medley])

(println (medley/map-vals inc {:one 1 :two 2}))
