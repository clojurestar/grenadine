(ns grenadine.test-runner
  (:require [clojure.test :as test]
            [grenadine.graph-test]
            [grenadine.lock-test]
            [grenadine.pom-test]
            [grenadine.repo-test]
            [grenadine.runtime-test]
            [grenadine.version-test]
            [grenadine.xml-test]))

(defn -main
  [& _args]
  #?(:lg
     (do
       ;; let-go's test runner reliably runs all registered namespaces through
       ;; its no-argument path and records the aggregate result in this var.
       (test/run-tests)
       (when-not test/*test-result*
         (throw (ex-info "Grenadine tests failed" {}))))

     :default
     (let [{:keys [fail error]}
           (test/run-tests 'grenadine.xml-test
                           'grenadine.pom-test
                           'grenadine.version-test
                           'grenadine.graph-test
                           'grenadine.lock-test
                           'grenadine.repo-test
                           'grenadine.runtime-test)]
       (when (pos? (+ fail error))
         (throw (ex-info "Grenadine tests failed"
                         {:fail fail :error error}))))))
