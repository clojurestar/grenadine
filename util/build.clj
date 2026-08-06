(ns util.build
  (:require
   [clojure.java.io :as io]
   [clojure.tools.build.api :as b]))

(def lib 'cc.clojure/grenadine)
(def class-dir "target/classes")
(def basis {:libs {}})

(def resources
  [["License" "META-INF/LICENSE"]
   ["ThirdPartyNotices.md" "ThirdPartyNotices.md"]
   ["Provenance.md" "Provenance.md"]
   ["patch/sources.yaml" "patch/sources.yaml"]
   ["patch/tools.deps.patch" "patch/tools.deps.patch"]
   ["patch/tools.deps.edn.patch" "patch/tools.deps.edn.patch"]
   ["patch/tools.gitlibs.patch" "patch/tools.gitlibs.patch"]
   ["licenses/Apache-2.0.txt" "licenses/Apache-2.0.txt"]
   ["licenses/Apache-Maven-NOTICE.txt"
    "licenses/Apache-Maven-NOTICE.txt"]])

(defn- version []
  (let [value (System/getenv "GRENADINE_VERSION")]
    (when-not (and value (re-matches #"[0-9]+\.[0-9]+\.[0-9]+" value))
      (throw
       (ex-info
        "GRENADINE_VERSION must have the form X.Y.Z"
        {:value value})))
    value))

(defn- copy-resource [[source target]]
  (let [destination (io/file class-dir target)]
    (io/make-parents destination)
    (io/copy (io/file source) destination)))

(defn jar [_]
  (let [version (version)
        jar-file (format "target/grenadine-%s.jar" version)]
    (b/delete {:path class-dir})
    (b/copy-dir {:src-dirs ["src"]
                 :target-dir class-dir})
    (run! copy-resource resources)
    (b/write-pom
     {:class-dir class-dir
      :lib lib
      :version version
      :basis basis
      :src-dirs ["src"]
      :src-pom :none
      :scm {:url "https://github.com/clojurestar/grenadine"
            :tag "HEAD"}
      :pom-data
      [[:description
        "Portable Maven dependency resolution for Clojure dialects"]
       [:url "https://github.com/clojurestar/grenadine"]
       [:licenses
        [:license
         [:name "Eclipse Public License 1.0"]
         [:url "https://opensource.org/license/epl-1-0"]]]]})
    (io/copy
     (io/file
      class-dir "META-INF/maven/cc.clojure/grenadine/pom.xml")
     (io/file "pom.xml"))
    (b/jar {:class-dir class-dir
            :jar-file jar-file})
    {:jar-file jar-file}))
