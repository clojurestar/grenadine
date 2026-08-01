(defproject cc.clojure/grenadine "0.1.1"
  :description "Portable Maven dependency resolution for Clojure dialects"
  :url "https://github.com/clojurestar/grenadine"

  :license
  {:name "MIT License"
   :url "https://opensource.org/license/mit"}

  :scm
  {:name "git"
   :url "https://github.com/clojurestar/grenadine"
   :tag "HEAD"}

  :dependencies
  [[org.clojure/clojure "1.12.0"]]

  :source-paths ["src"]
  :test-paths ["test"]

  :deploy-repositories
  [["clojars"
    {:url "https://repo.clojars.org"
     :username :env/clojars_username
     :password :env/clojars_password
     :sign-releases false}]])
