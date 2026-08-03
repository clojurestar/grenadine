(ns grenadine.host.let-go
  "Native effects supplied by let-go for Grenadine."
  (:require [let-go.deps.host :as native]))

(defn host [] (native/host))
