(ns grenadine.test-support)

(defmacro throws?
  "Return true when evaluating body throws, across Grenadine's runtime matrix."
  [& body]
  #?(:glj
     `(try (do ~@body false)
           (catch go/any _# true))

     :jolt
     `(try (do ~@body false)
           (catch Throwable _# true))

     :default
     `(try (do ~@body false)
           (catch Throwable _# true))))
