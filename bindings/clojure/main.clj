(ns main
  (:require [clojure.java.io :as io]
            [grenadine.core :as grenadine]
            [grenadine.host.jvm :as host]))

(def result
  (grenadine/install!
   '{dev.weavejester/medley {:mvn/version "1.10.0"}}
   {:host (host/host)
    :local-repo (System/getenv "GRENADINE_EXAMPLE_REPOSITORY")}))

(def loader
  (clojure.lang.DynamicClassLoader. (clojure.lang.RT/baseLoader)))

(doseq [jar (:classpath result)]
  (.addURL loader (.toURL (.toURI (io/file jar)))))

(with-bindings {clojure.lang.Compiler/LOADER loader}
  (require '[medley.core :as medley]))

(defn -main [& _]
  (println (medley/map-vals inc {:one 1 :two 2})))
