(ns grenadine.cli
  (:require [clojure.string :as str]
            [grenadine.build-info :as build-info]
            [grenadine.core :as grenadine]
            [grenadine.host.glojure :as glojure-host]
            [grenadine.lock :as lock]
            [grenadine.repo :as repo]
            [grenadine.source :as source]))

(def usage
  (str
   "Usage: grenadine [OPTIONS] DEPS-SOURCE\n"
   "       grenadine [--repository DIR] --list\n"
   "       grenadine [--repository DIR] --add NAME [VERSION]...\n"
   "       grenadine [--repository DIR] --remove NAME [VERSION]...\n"
   "       grenadine [--repository DIR] [--resolver MODE] --resolve NAME [VERSION]...\n"
   "       grenadine --resolvers\n"
   "       grenadine --help\n"
   "       grenadine --version\n\n"
   "Install the Maven dependencies in a local or remote DEPS-SOURCE.\n\n"
   "Options:\n"
   "  -R, --repository DIR  Use this Maven repository\n"
   "      --list            List libraries in the Maven repository\n"
   "      --add             Add libraries to the Maven repository\n"
   "      --remove          Remove libraries from the Maven repository\n"
   "      --resolve         Resolve libraries without installing JARs\n"
   "      --resolver MODE   Use newest, nearest, or tools-deps\n"
   "      --resolvers       List the available resolver methodologies\n"
   "  -q, --quiet           Suppress non-error output\n"
   "  -h, --help            Show this help\n"
   "  -V, --version         Show the Grenadine version"))

(defn- exit! [status]
  (os.Exit status))

(defn- fail! [message]
  (fmt.Fprintln os.Stderr (str "grenadine: " message))
  (exit! 1))

(defn- parse-options [argv]
  (loop [remaining argv options {}]
    (if-let [argument (first remaining)]
      (cond
        (contains? #{"-h" "--help"} argument)
        (recur (next remaining) (assoc options :help true))

        (contains? #{"-V" "--version"} argument)
        (recur (next remaining) (assoc options :version true))

        (contains? #{"-q" "--quiet"} argument)
        (recur (next remaining) (assoc options :quiet true))

        (= "--list" argument)
        (recur (next remaining) (assoc options :list true))

        (= "--add" argument)
        (recur (next remaining) (assoc options :add true))

        (= "--remove" argument)
        (recur (next remaining) (assoc options :remove true))

        (= "--resolve" argument)
        (recur (next remaining) (assoc options :resolve true))

        (= "--resolvers" argument)
        (recur (next remaining) (assoc options :resolvers true))

        (= "--resolver" argument)
        (if-let [resolver (second remaining)]
          (recur (drop 2 remaining) (assoc options :resolver resolver))
          (fail! "--resolver requires a methodology"))

        (.startsWith argument "--resolver=")
        (let [resolver (subs argument (count "--resolver="))]
          (when (empty? resolver)
            (fail! "--resolver requires a methodology"))
          (recur (next remaining) (assoc options :resolver resolver)))

        (contains? #{"-R" "--repository"} argument)
        (if-let [repository (second remaining)]
          (recur (drop 2 remaining) (assoc options :repository repository))
          (fail! (str argument " requires a directory")))

        (.startsWith argument "--repository=")
        (let [repository (subs argument (count "--repository="))]
          (when (empty? repository)
            (fail! "--repository requires a directory"))
          (recur (next remaining) (assoc options :repository repository)))

        (.startsWith argument "-")
        (fail! (str "unknown option: " argument))

        :else
        (recur (next remaining)
               (update options :operands (fnil conj []) argument)))
      options)))

(def library-pattern #"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
(def component-pattern #"^[A-Za-z0-9_.-]+$")
(def resolver-methodologies
  {"newest" :newest
   "nearest" :nearest
   "tools-deps" :tools-deps})

(def resolver-descriptions
  [["newest" "Select the highest Maven-compatible version"]
   ["nearest" "Select the shortest path, then declaration order"]
   ["tools-deps"
    "Preserve direct dependencies; otherwise select newest (default)"]])

(defn- library-coordinate [value]
  (when-not (re-matches library-pattern value)
    (fail! (str "invalid library name: " value)))
  (let [[group artifact] (str/split value #"/")
        components (concat (str/split group #"\.") [artifact])]
    (when (some #(or (= "." %) (= ".." %)
                     (not (re-matches component-pattern %)))
                components)
      (fail! (str "invalid library name: " value)))
    {:name value :group group :artifact artifact}))

(defn- valid-version! [value]
  (when (or (empty? value)
            (= "." value)
            (= ".." value)
            (str/includes? value "/")
            (str/includes? value "\\"))
    (fail! (str "invalid version: " value)))
  value)

(defn- coordinate-requests [operands]
  (loop [remaining operands requests []]
    (if-let [name (first remaining)]
      (let [coordinate (library-coordinate name)
            following (second remaining)
            version (when (and following
                               (not (str/includes? following "/")))
                      (valid-version! following))]
        (recur (drop (if version 2 1) remaining)
               (conj requests (assoc coordinate :version version))))
      requests)))

(defn- validate-root-requests! [requests]
  (let [names (map :name requests)]
    (when-not (= (count names) (count (set names)))
      (fail! "each library name may be specified only once")))
  requests)

(defn- resolver-methodology [value]
  (if value
    (or (get resolver-methodologies value)
        (fail! (str "unknown resolver methodology: " value
                    " (expected newest, nearest, or tools-deps)")))
    :tools-deps))

(defn- list-resolvers! [{:keys [quiet]}]
  (when-not quiet
    (doseq [[name description] resolver-descriptions]
      (fmt.Fprintln os.Stdout (fmt.Sprintf "%-10s %s" name description)))))

(defn- validate-remove-requests! [requests]
  (doseq [[name entries] (group-by :name requests)]
    (let [versions (map :version entries)]
      (when-not (= (count versions) (count (set versions)))
        (fail! (str "duplicate removal request: " name)))
      (when (and (some nil? versions) (> (count versions) 1))
        (fail! (str "cannot combine all-version and version removals for "
                    name)))))
  requests)

(defn- read-directory [directory]
  (let [[entries error] (os.ReadDir directory)]
    (when-not (nil? error)
      (fail! (str "cannot list " directory ": " (fmt.Sprint error))))
    entries))

(defn- repository-coordinates [root]
  (letfn
   [(walk [directory parts]
      (let [entries (read-directory directory)
            names (set (map #(.Name %) entries))
            count-parts (count parts)
            artifact (when (>= count-parts 3)
                       (nth parts (- count-parts 2)))
            version (when artifact (last parts))
            coordinate
            (when (and artifact
                       (contains? names (str artifact "-" version ".jar")))
              {:group (str/join "." (take (- count-parts 2) parts))
               :artifact artifact
               :version version})]
        (concat
         (when coordinate [coordinate])
         (mapcat
          (fn [entry]
            (when (.IsDir entry)
              (let [name (.Name entry)]
                (walk (path:filepath.Join directory name)
                      (conj parts name)))))
          entries))))]
    (sort-by (juxt :group :artifact :version) (walk root []))))

(defn- list-repository! [{:keys [repository quiet]}]
  (let [host (glojure-host/host)
        local-repo (repo/local-repo {:host host :local-repo repository})]
    (when-not quiet
      (doseq [{:keys [group artifact version]}
              (repository-coordinates local-repo)]
        (fmt.Fprintln os.Stdout (str group "/" artifact " " version))))))

(defn- repo-id [value]
  (cond
    (keyword? value) (name value)
    (symbol? value) (name value)
    :else (str value)))

(defn- configured-repos [config]
  (let [defaults (into {} (map (juxt :id identity) lock/default-repos))
        configured
        (into {}
              (map (fn [[id value]]
                     (let [id (repo-id id)]
                       [id {:id id :url (:url value)}])))
              (:mvn/repos config))
        repos (merge defaults configured)
        preferred ["central" "clojars"]
        extras (sort (remove (set preferred) (keys repos)))]
    (mapv repos (concat preferred extras))))

(defn- read-config [input host]
  (let [content
        (if (source/remote? input)
          (source/fetch-text host input)
          (let [[content error] (os.ReadFile input)]
            (when error
              (fail! (str "cannot read " input ": " (fmt.Sprint error))))
            (go/string content)))]
    (let [config
          (try
            (read-string content)
            (catch Exception error
              (fail! (str "cannot parse " input ": " (fmt.Sprint error)))))]
      (when-not (map? config)
        (fail! (str input " must contain an EDN map")))
      (when (and (contains? config :deps)
                 (not (map? (:deps config))))
        (fail! (str input " :deps must be a map")))
      (if (contains? config :deps)
        config
        (assoc config :deps {})))))

(defn- print-installed!
  [{:keys [group artifact version]}]
  (fmt.Fprintln os.Stdout
                (str "Installed " group "/" artifact " " version)))

(defn- print-summary!
  [result]
  (let [installed (count (:fetched result))
        already (count (:cached result))]
    (fmt.Fprintln os.Stdout
                  (str "=> Installed: " installed
                       "  Already: " already
                       "  Total: " (+ installed already)))))

(defn- install-deps!
  [deps {:keys [host repos local-repo quiet resolver]}]
  (let [result
        (grenadine/install!
         deps
         (cond-> {:host host
                  :repos repos
                  :mediation resolver}
           local-repo (assoc :local-repo local-repo)
           (not quiet) (assoc :on-install print-installed!)))]
    (when-not quiet
      (doseq [warning (:warnings result)]
        (fmt.Fprintln os.Stderr
                      (str "grenadine: warning: " (pr-str warning))))
      (print-summary! result))))

(defn- install! [{:keys [file repository quiet resolver]}]
  (let [host (glojure-host/host)
        config (read-config file host)]
    (install-deps!
     (:deps config)
     {:host host
      :repos (configured-repos config)
      :local-repo (or repository (:mvn/local-repo config))
      :resolver resolver
      :quiet quiet})))

(defn- resolve-requests
  [requests host repos]
  (mapv
   (fn [{:keys [version] :as request}]
     (if version
       request
       (assoc request :version
              (repo/latest-version request {:host host :repos repos}))))
   requests))

(defn- requests->deps [requests]
  (into {}
        (map (fn [{:keys [name version]}]
               [(symbol name) {:mvn/version version}]))
        requests))

(defn- add! [{:keys [repository quiet operands resolver]}]
  (let [host (glojure-host/host)
        repos (configured-repos {})
        requests (validate-root-requests! (coordinate-requests operands))
        resolved (resolve-requests requests host repos)
        deps (requests->deps resolved)]
    (install-deps!
     deps
     {:host host
      :repos repos
      :local-repo repository
      :resolver resolver
      :quiet quiet})))

(defn- resolve! [{:keys [repository quiet operands resolver]}]
  (let [host (glojure-host/host)
        repos (configured-repos {})
        requests (validate-root-requests! (coordinate-requests operands))
        deps (requests->deps (resolve-requests requests host repos))
        resolution
        (grenadine/resolve-graph
         deps
         (cond-> {:host host
                  :repos repos
                  :mediation resolver}
           repository (assoc :local-repo repository)))
        coordinates
        (->> (:selected resolution)
             vals
             (map :coords)
             (sort-by (juxt :group :artifact :version)))]
    (when-not quiet
      (doseq [{:keys [group artifact version]} coordinates]
        (fmt.Fprintln os.Stdout (str group "/" artifact " " version)))
      (doseq [warning (:warnings resolution)]
        (fmt.Fprintln os.Stderr
                      (str "grenadine: warning: " (pr-str warning)))))))

(defn- absolute-path [value]
  (let [[absolute error] (path:filepath.Abs value)]
    (when-not (nil? error)
      (fail! (str "cannot resolve " value ": " (fmt.Sprint error))))
    (path:filepath.Clean absolute)))

(defn- child-path [root segments]
  (reduce (fn [parent segment]
            (path:filepath.Join parent segment))
          root
          segments))

(defn- removal-target [root {:keys [group artifact version] :as request}]
  (let [target
        (path:filepath.Clean
         (child-path root
                     (concat (str/split group #"\.")
                             [artifact]
                             (when version [version]))))
        [relative error] (path:filepath.Rel root target)]
    (when-not (nil? error)
      (fail! (str "cannot resolve removal path: " (fmt.Sprint error))))
    (when (or (= "." relative)
              (= ".." relative)
              (path:filepath.IsAbs relative)
              (str/starts-with? relative
                                (str ".." (go/string os.PathSeparator))))
      (fail! (str "refusing removal outside repository: " (:name request))))
    (assoc request :target target)))

(defn- print-removal! [action {:keys [name version]}]
  (fmt.Fprintln os.Stdout
                (str action " " name " " (or version "(all versions)"))))

(defn- remove! [{:keys [repository quiet operands]}]
  (let [host (glojure-host/host)
        root (absolute-path (repo/local-repo {:host host
                                              :local-repo repository}))
        requests
        (mapv #(removal-target root %)
              (validate-remove-requests!
               (coordinate-requests operands)))
        result
        (reduce
         (fn [counts {:keys [target] :as request}]
           (if ((:exists? host) target)
             (do
               ((:delete! host) target)
               (when-not quiet (print-removal! "Removed" request))
               (update counts :removed inc))
             (do
               (when-not quiet (print-removal! "Missing" request))
               (update counts :missing inc))))
         {:removed 0 :missing 0}
         requests)]
    (when-not quiet
      (fmt.Fprintln os.Stdout
                    (str "=> Removed: " (:removed result)
                         "  Missing: " (:missing result)
                         "  Total: " (+ (:removed result)
                                        (:missing result)))))))

(defn -main [& argv]
  (try
    (github.com:glojurelang:glojure:pkg:stdlib:clojure:core:protocols.LoadNS)
    (let [{:keys [help version list add remove resolve resolvers operands
                  repository]
           :as parsed}
          (parse-options argv)
          modes (filter identity [list add remove resolve resolvers])
          methodology (resolver-methodology (:resolver parsed))
          options (assoc parsed
                         :file (first operands)
                         :resolver methodology)]
      (cond
        help (fmt.Fprintln os.Stdout usage)
        version (fmt.Fprintln os.Stdout
                              (str "grenadine v" build-info/version))
        (> (count modes) 1)
        (fail! (str "--list, --add, --remove, --resolve, and --resolvers "
                    "are mutually exclusive"))
        (and (:resolver parsed) (or list remove resolvers))
        (fail! "--resolver is only valid with --resolve, --add, or a deps source")
        resolvers
        (cond
          (seq operands) (fail! "--resolvers does not accept arguments")
          repository (fail! "--resolvers does not use --repository")
          :else (list-resolvers! options))
        list (if (seq operands)
               (fail! "--list does not accept a deps source")
               (list-repository! options))
        add (if (empty? operands)
              (fail! "--add requires at least one library name")
              (add! options))
        remove (if (empty? operands)
                 (fail! "--remove requires at least one library name")
                 (remove! options))
        resolve (if (empty? operands)
                  (fail! "--resolve requires at least one library name")
                  (resolve! options))
        (empty? operands) (fail! "a deps source is required")
        (> (count operands) 1)
        (fail! (str "unexpected argument: " (second operands)))
        :else (install! options)))
    (catch Exception error
      (fmt.Fprintln os.Stderr (str "grenadine: " (fmt.Sprint error)))
      (exit! 1))))
