(ns grenadine.test-support)

(defmacro throws?
  "Return true when evaluating body throws, across Grenadine's runtime matrix."
  [& body]
  #?(:lg
     `(try (do ~@body false)
           (catch _# true))

     :glj
     `(try (do ~@body false)
           (catch go/any _# true))

     :jolt
     `(try (do ~@body false)
           (catch Throwable _# true))

     :default
     `(try (do ~@body false)
           (catch Throwable _# true))))
