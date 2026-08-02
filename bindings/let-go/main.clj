(require '[let-go.deps :as deps])

(def medley-root (first *command-line-args*))

(deps/add-lib
 'dev.weavejester/medley
 {:mvn/version "1.10.0"}
 {:install-fn
  (fn [_libs _opts]
    {:source-roots [medley-root]
     :warnings []})})

(require '[medley.core :as medley])

(println (medley/map-vals inc {:one 1 :two 2}))
