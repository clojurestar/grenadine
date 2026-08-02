(require '[jolt.deps :as deps])

(deps/add-deps
 '{:mvn/local-repo "./m2"
   :deps
   {dev.weavejester/medley {:mvn/version "1.10.0"}}})

(require '[medley.core :as medley])

(println (medley/map-vals inc {:one 1 :two 2}))
