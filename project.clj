(defproject cc.clojure/grenadine "0.1.5"
  :description "Portable Maven dependency resolution for Clojure dialects"
  :url "https://github.com/clojurestar/grenadine"

  :license
  {:name "Eclipse Public License 1.0"
   :url "https://opensource.org/license/epl-1-0"}

  :scm
  {:name "git"
   :url "https://github.com/clojurestar/grenadine"
   :tag "HEAD"}

  :dependencies
  [[org.clojure/clojure "1.12.0"]]

  :filespecs
  [{:type :paths
    :paths ["ThirdPartyNotices.md" "Provenance.md"]}
   {:type :bytes
    :path "patch/sources.yaml"
    :bytes ~(slurp "patch/sources.yaml")}
   {:type :bytes
    :path "patch/tools.deps.patch"
    :bytes ~(slurp "patch/tools.deps.patch")}
   {:type :bytes
    :path "patch/tools.deps.edn.patch"
    :bytes ~(slurp "patch/tools.deps.edn.patch")}
   {:type :bytes
    :path "patch/tools.gitlibs.patch"
    :bytes ~(slurp "patch/tools.gitlibs.patch")}
   {:type :bytes
    :path "licenses/Apache-2.0.txt"
    :bytes ~(slurp "licenses/Apache-2.0.txt")}
   {:type :bytes
    :path "licenses/Apache-Maven-NOTICE.txt"
    :bytes ~(slurp "licenses/Apache-Maven-NOTICE.txt")}]

  :source-paths ["src"]
  :test-paths ["test"]

  :deploy-repositories
  [["clojars"
    {:url "https://repo.clojars.org"
     :username :env/clojars_username
     :password :env/clojars_password
     :sign-releases false}]])
