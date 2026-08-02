(require '[grenadine.bb :as deps])

(deps/add-lib
 'dev.weavejester/medley
 {:mvn/version "1.10.0"}
 {:local-repo (System/getenv "GRENADINE_EXAMPLE_REPOSITORY")})

(require '[medley.core :as medley])

(println (medley/map-vals inc {:one 1 :two 2}))
