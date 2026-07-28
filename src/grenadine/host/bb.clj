(ns grenadine.host.bb
  "Babashka host implementation.

  Babashka exposes the required JDK IO, networking, ZIP, and digest classes, so
  it can reuse Grenadine's JVM effect implementation without invoking Java."
  (:require [grenadine.host.jvm :as jvm]))

(defn host
  []
  (jvm/host))
