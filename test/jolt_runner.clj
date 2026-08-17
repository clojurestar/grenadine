(doseq [file ["src/grenadine/source.cljc"
              "src/grenadine/gitlibs.cljc"
              "src/grenadine/version.cljc"
              "src/grenadine/xml.cljc"
              "src/grenadine/lock.cljc"
              "src/grenadine/repo.cljc"
              "src/grenadine/coordinate.cljc"
              "src/grenadine/require_deps.cljc"
              "src/clojurestar/deps.cljc"
              ;; Refresh namespaces Jolt may replace from its preloaded image
              ;; while compiling coordinate's repository require or the
              ;; clojurestar facade's host implementation.
              "src/grenadine/source.cljc"
              "src/grenadine/gitlibs.cljc"
              "src/grenadine/version.cljc"
              "src/grenadine/xml.cljc"
              "src/grenadine/repo.cljc"
              "src/grenadine/lock.cljc"
              "src/grenadine/coordinate.cljc"
              "src/grenadine/expander.cljc"
              "src/grenadine/graph.cljc"
              "src/grenadine/pom.cljc"
              "src/grenadine/basis.cljc"
              "src/grenadine/core.cljc"
              "src/grenadine/runtime.cljc"
              "src/grenadine/require_deps.cljc"
              "test/grenadine/test_support.cljc"
              "test/grenadine/xml_test.cljc"
              "test/grenadine/pom_test.cljc"
              "test/grenadine/version_test.cljc"
              "test/grenadine/expander_test.cljc"
              "test/grenadine/graph_test.cljc"
              "test/grenadine/lock_test.cljc"
              "test/grenadine/repo_test.cljc"
              "test/grenadine/source_test.cljc"
              "test/grenadine/runtime_test.cljc"
              "test/grenadine/require_deps_test.cljc"
              "test/grenadine/clojurestar_deps_test.cljc"
              "test/grenadine/coordinate_test.cljc"
              "test/grenadine/test_runner.cljc"]]
  (load-file file))

;; Test namespace declarations can reload Jolt's embedded Grenadine release.
;; Restore worktree definitions after all test namespaces have been compiled.
(doseq [file ["src/grenadine/gitlibs.cljc"
              "src/grenadine/graph.cljc"
              "src/grenadine/runtime.cljc"
              "src/grenadine/require_deps.cljc"]]
  (load-file file))

(grenadine.test-runner/-main)
