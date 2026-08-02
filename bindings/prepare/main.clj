(ns main
  (:require [grenadine.core :as grenadine]
            [grenadine.host.bb :as host]))

(def medley
  '{dev.weavejester/medley {:mvn/version "1.10.0"}})

(defn -main [& _]
  (let [result
        (grenadine/install!
         medley
         {:host (host/host)
          :local-repo (System/getenv "GRENADINE_EXAMPLE_REPOSITORY")
          :source-roots? true
          :source-libs #{'dev.weavejester/medley}})]
    (println (first (:source-roots result)))))

(apply -main *command-line-args*)
