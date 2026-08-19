(ns grenadine.cli
  (:require [clojure.string :as str]
            [grenadine.build-info :as build-info]
            [grenadine.coordinate :as coordinate]
            [grenadine.core :as grenadine]
            [grenadine.gitlibs :as gitlibs]
            [grenadine.host.glojure :as glojure-host]
            [grenadine.lock :as lock]
            [grenadine.repo :as repo]
            [grenadine.source :as source]
            [grenadine.version :as version]))

(def usage
  (str
   "Usage: grenadine\n"
   "       grenadine [OPTIONS] --list [ITEM...]\n"
   "       grenadine [OPTIONS] --current [ITEM...]\n"
   "       grenadine [OPTIONS] [-M MODE] --install ITEM...\n"
   "       grenadine [OPTIONS] --delete ITEM...\n"
   "       grenadine [OPTIONS] [-M MODE] --remove ITEM...\n"
   "       grenadine [OPTIONS] [-M MODE] --expand ITEM...\n"
   "       grenadine --mediators\n"
   "       grenadine --help\n"
   "       grenadine --version\n\n"
   "ITEM is NAME [VERSION] or a local/remote DEPENDENCY-SOURCE.\n"
   "--list and --current also accept a Maven repository DIR.\n\n"
   "Options:\n"
   "  -R, --repository DIR  Use this Maven repository\n"
   "  -G, --gitlibs DIR     Use this Git library cache\n"
   "  -M, --mediator MODE   Use newest, nearest, or tools-deps\n"
   "      --list            List the repository or an expanded graph\n"
   "      --current         List installed or selected dependency updates\n"
   "      --install         Install an expanded dependency graph\n"
   "      --delete          Delete only explicitly requested coordinates\n"
   "      --remove          Delete complete expanded dependency closures\n"
   "  -X, --expand          Expand dependencies without installing JARs\n"
   "      --mediators       List the available mediation strategies\n"
   "  -q, --quiet           Suppress non-error output\n"
   "  -h, --help            Show this help\n"
   "      --version         Show the Grenadine version"))

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

        (= "--version" argument)
        (recur (next remaining) (assoc options :version true))

        (contains? #{"-q" "--quiet"} argument)
        (recur (next remaining) (assoc options :quiet true))

        (= "--list" argument)
        (recur (next remaining) (assoc options :list true))

        (= "--current" argument)
        (recur (next remaining) (assoc options :current true))

        (= "--install" argument)
        (recur (next remaining) (assoc options :install true))

        (= "--add" argument)
        (recur (next remaining)
               (assoc options :install true :deprecated-add true))

        (= "--delete" argument)
        (recur (next remaining) (assoc options :delete true))

        (= "--remove" argument)
        (recur (next remaining) (assoc options :remove true))

        (contains? #{"-X" "--expand"} argument)
        (recur (next remaining) (assoc options :expand true))

        (= "--mediators" argument)
        (recur (next remaining) (assoc options :mediators true))

        (contains? #{"-M" "--mediator"} argument)
        (if-let [mediator (second remaining)]
          (recur (drop 2 remaining) (assoc options :mediator mediator))
          (fail! (str argument " requires a strategy")))

        (.startsWith argument "--mediator=")
        (let [mediator (subs argument (count "--mediator="))]
          (when (empty? mediator)
            (fail! "--mediator requires a strategy"))
          (recur (next remaining) (assoc options :mediator mediator)))

        (contains? #{"-R" "--repository"} argument)
        (if-let [repository (second remaining)]
          (recur (drop 2 remaining) (assoc options :repository repository))
          (fail! (str argument " requires a directory")))

        (.startsWith argument "--repository=")
        (let [repository (subs argument (count "--repository="))]
          (when (empty? repository)
            (fail! "--repository requires a directory"))
          (recur (next remaining) (assoc options :repository repository)))

        (contains? #{"-G" "--gitlibs"} argument)
        (if-let [directory (second remaining)]
          (recur (drop 2 remaining) (assoc options :gitlibs directory))
          (fail! (str argument " requires a directory")))

        (.startsWith argument "--gitlibs=")
        (let [directory (subs argument (count "--gitlibs="))]
          (when (empty? directory)
            (fail! "--gitlibs requires a directory"))
          (recur (next remaining) (assoc options :gitlibs directory)))

        (.startsWith argument "-")
        (fail! (str "unknown option: " argument))

        :else
        (recur (next remaining)
               (update options :operands (fnil conj []) argument)))
      options)))

(def library-pattern
  #"^[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)?(?:[$][A-Za-z0-9_.-]+)?$")
(def component-pattern #"^[A-Za-z0-9_.-]+$")
(def mediator-strategies
  {"newest" :newest
   "nearest" :nearest
   "tools-deps" :tools-deps})

(def mediator-descriptions
  [["newest" "Select the highest Maven-compatible version"]
   ["nearest" "Select the shortest path, then declaration order"]
   ["tools-deps"
    "Preserve direct dependencies; otherwise select newest (default)"]])

(defn- library-coordinate [value]
  (when-not (re-matches library-pattern value)
    (fail! (str "invalid library name: " value)))
  (let [[group artifact classifier]
        (coordinate/split-lib value)
        components (concat (str/split group #"\.")
                           [artifact]
                           (when classifier [classifier]))]
    (when (some #(or (= "." %) (= ".." %)
                     (not (re-matches component-pattern %)))
                components)
      (fail! (str "invalid library name: " value)))
    (cond-> {:name value :group group :artifact artifact}
      classifier (assoc :classifier classifier))))

(declare explicit-source?)

(defn- valid-version! [value]
  (when (or (empty? value)
            (= "." value)
            (= ".." value)
            (str/includes? value "/")
            (str/includes? value "\\"))
    (fail! (str "invalid version: " value)))
  value)

(defn- version-operand?
  [value host]
  (and value
       (not (explicit-source? value host))
       (or (not (re-matches library-pattern value))
           (boolean (re-matches #"^[0-9].*" value))
           (contains? #{"LATEST" "RELEASE"} value))))

(defn- mediation-strategy [value]
  (if value
    (or (get mediator-strategies value)
        (fail! (str "unknown mediation strategy: " value
                    " (expected newest, nearest, or tools-deps)")))
    :tools-deps))

(defn- list-mediators! [{:keys [quiet]}]
  (when-not quiet
    (doseq [[name description] mediator-descriptions]
      (fmt.Fprintln os.Stdout (fmt.Sprintf "%-10s %s" name description)))))

(defn- validate-delete-requests! [requests]
  (doseq [[name entries] (group-by :name requests)]
    (let [versions (map :version entries)]
      (when-not (= (count versions) (count (set versions)))
        (fail! (str "duplicate deletion request: " name)))
      (when (and (some nil? versions) (> (count versions) 1))
        (fail! (str "cannot combine all-version and version deletions for "
                    name)))))
  requests)

(defn- validate-maven-removal! [requests]
  (doseq [{:keys [name coordinate classifier]} requests]
    (when (and coordinate
               (not= :mvn (coordinate/coordinate-type coordinate)))
      (fail! (str "--delete and --remove support only Maven coordinates: "
                  name)))
    (when classifier
      (fail! (str "--delete and --remove do not support Maven classifiers: "
                  name))))
  requests)

(defn- read-directory [directory]
  (let [[entries error] (os.ReadDir directory)]
    (when-not (nil? error)
      (fail! (str "cannot list " directory ": " (fmt.Sprint error))))
    entries))

(defn- pad-right [value width]
  (str value (apply str (repeat (- width (count value)) " "))))

(defn- table-lines [rows]
  (let [first-width (apply max 0 (map #(count (first %)) rows))
        second-width (apply max 0 (map #(count (second %)) rows))]
    (mapv
     (fn [[first-column second-column third-column]]
       (str (pad-right first-column first-width)
            "  "
            (if third-column
              (str (pad-right second-column second-width)
                   "  " third-column)
              second-column)))
     rows)))

(defn- print-table! [rows]
  (doseq [line (table-lines rows)]
    (fmt.Fprintln os.Stdout line)))

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
      (print-table!
       (mapv (fn [{:keys [group artifact version]}]
               [(str group "/" artifact) version])
             (repository-coordinates local-repo))))))

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

(declare project-error!)

(defn- source-layout?
  [character]
  (contains? #{\space \tab \newline \return \formfeed \,} character))

(defn- skip-source-layout
  [source start]
  (loop [index start]
    (if (< index (count source))
      (let [character (.charAt source index)]
        (cond
          (source-layout? character) (recur (inc index))
          (= character \;)
          (if-let [newline (str/index-of source "\n" index)]
            (recur (inc newline))
            (count source))
          :else index))
      index)))

(def ^:private source-opening-delimiters #{\( \[ \{})
(def ^:private source-closing-delimiters #{\) \] \}})

(defn- source-form-end
  [source start]
  (loop [index start
         depth 0
         string? false
         escaped? false
         comment? false]
    (if (>= index (count source))
      index
      (let [character (.charAt source index)]
        (cond
          comment?
          (recur (inc index) depth string? false
                 (not= character \newline))

          escaped?
          (recur (inc index) depth string? false false)

          string?
          (cond
            (= character \\)
            (recur (inc index) depth true true false)

            (= character \")
            (recur (inc index) depth false false false)

            :else
            (recur (inc index) depth true false false))

          (= character \\)
          (recur (inc index) depth false true false)

          (= character \")
          (recur (inc index) depth true false false)

          (= character \;)
          (recur (inc index) depth false false true)

          (source-opening-delimiters character)
          (recur (inc index) (inc depth) false false false)

          (source-closing-delimiters character)
          (if (= depth 1)
            (inc index)
            (recur (inc index) (dec depth) false false false))

          (and (zero? depth) (source-layout? character))
          index

          :else
          (recur (inc index) depth false false false))))))

(defn- next-source-form-end
  [source start]
  (let [end (source-form-end source start)
        metadata-prefix?
        (or (= \^ (.charAt source start))
            (and (= \# (.charAt source start))
                 (< (inc start) (count source))
                 (= \^ (.charAt source (inc start)))))]
    (if metadata-prefix?
      (let [value-start (skip-source-layout source end)]
        (if (< value-start (count source))
          (next-source-form-end source value-start)
          end))
      end)))

(defn- discard-marker?
  [source start]
  (and (< (inc start) (count source))
       (= \# (.charAt source start))
       (= \_ (.charAt source (inc start)))))

(defn- discarded-forms-end
  [source start]
  (let [[count position]
        (loop [count 0 position start]
          (if (discard-marker? source position)
            (recur (inc count)
                   (skip-source-layout source (+ position 2)))
            [count position]))]
    (loop [remaining count position position]
      (if (zero? remaining)
        position
        (let [end (next-source-form-end source position)]
          (recur (dec remaining) (skip-source-layout source end)))))))

(defn- project-form-sources
  [input source]
  (let [start (skip-source-layout source 0)]
    (when (or (>= start (count source))
              (not= \( (.charAt source start)))
      (project-error! input "must contain a literal defproject form"))
    (let [end (source-form-end source start)]
      (when (or (<= end start)
                (not= \) (.charAt source (dec end))))
        (project-error! input "contains an unterminated defproject form"))
      (loop [index (inc start) forms []]
        (let [form-start (skip-source-layout source index)]
          (if (>= form-start (dec end))
            forms
            (if (discard-marker? source form-start)
              (recur (discarded-forms-end source form-start) forms)
              (let [form-end (next-source-form-end source form-start)]
                (recur form-end
                       (conj forms
                             (subs source form-start form-end)))))))))))

(defn- project-error!
  [input message]
  (fail! (str input " " message)))

(defn- literal-options
  [input label values]
  (when (odd? (count values))
    (project-error! input (str label " must contain keyword/value pairs")))
  (reduce
   (fn [options [key value]]
     (when-not (keyword? key)
       (project-error! input (str label " option must be a keyword: "
                                  (pr-str key))))
     (assoc options key value))
   {}
   (partition 2 values)))

(defn- project-lib
  [input label value]
  (when-not (or (symbol? value) (string? value))
    (project-error! input (str label " library must be a symbol or string: "
                               (pr-str value))))
  (library-coordinate (str value)))

(defn- project-exclusion
  [input value]
  (let [lib (if (vector? value) (first value) value)
        request (project-lib input "exclusion" lib)]
    (symbol (:group request) (:artifact request))))

(defn- project-exclusions
  [input label values]
  (when-not (or (nil? values) (sequential? values))
    (project-error! input (str label " must be a sequential collection")))
  (set (map #(project-exclusion input %) (or values []))))

(def ^:private project-dependency-options
  #{:classifier :exclusions :extension :scope :optional :native-prefix})

(defn- project-dependency
  [input global-exclusions value]
  (when-not (vector? value)
    (project-error! input (str "dependency must be a vector: "
                               (pr-str value))))
  (let [[lib version & option-values] value
        request (project-lib input "dependency" lib)]
    (when-not (and (string? version) (not (str/blank? version)))
      (project-error! input
                      (str "dependency " (:name request)
                           " requires a literal version string")))
    (let [options (literal-options input
                                   (str "dependency " (:name request))
                                   option-values)
          unknown (seq (remove project-dependency-options (keys options)))
          classifier (:classifier options)
          extension (:extension options)
          scope (:scope options)]
      (when unknown
        (project-error! input
                        (str "dependency " (:name request)
                             " has unsupported options: "
                             (str/join ", " (sort (map str unknown))))))
      (when (and (some? classifier)
                 (not (and (string? classifier)
                           (re-matches component-pattern classifier))))
        (project-error! input
                        (str "dependency " (:name request)
                             " has an invalid :classifier")))
      (when-not (or (nil? extension) (= "jar" extension))
        (project-error! input
                        (str "dependency " (:name request)
                             " uses unsupported :extension "
                             (pr-str extension))))
      (when-not (or (nil? scope) (#{"compile" "runtime"} scope))
        (project-error! input
                        (str "dependency " (:name request)
                             " uses unsupported :scope " (pr-str scope))))
      (when (and (contains? options :optional)
                 (not (boolean? (:optional options))))
        (project-error! input
                        (str "dependency " (:name request)
                             " has a non-boolean :optional")))
      (when (and (contains? options :native-prefix)
                 (not (string? (:native-prefix options))))
        (project-error! input
                        (str "dependency " (:name request)
                             " has a non-string :native-prefix")))
      (let [exclusions
            (into global-exclusions
                  (project-exclusions input
                                      (str "dependency " (:name request)
                                           " :exclusions")
                                      (:exclusions options)))
            lib (if classifier
                  (symbol (:group request)
                          (str (:artifact request) "$" classifier))
                  (symbol (:group request) (:artifact request)))]
        [lib (cond-> {:mvn/version version}
               (seq exclusions) (assoc :exclusions exclusions))]))))

(defn- project-repositories
  [input repositories]
  (when-not (or (nil? repositories) (sequential? repositories))
    (project-error! input ":repositories must be a sequential collection"))
  (reduce
   (fn [result repository]
     (when-not (and (vector? repository) (= 2 (count repository)))
       (project-error! input
                       (str "repository must be an [ID URL-OR-MAP] vector: "
                            (pr-str repository))))
     (let [[id settings] repository
           url (if (string? settings) settings (:url settings))]
       (when-not (and (or (string? id) (keyword? id) (symbol? id))
                      (not (str/blank? (repo-id id))))
         (project-error! input (str "repository has an invalid ID: "
                                    (pr-str id))))
       (when-not (and (string? url) (not (str/blank? url)))
         (project-error! input
                         (str "repository " (repo-id id)
                              " requires a literal URL")))
       (assoc result (repo-id id) {:url url})))
   {}
   (or repositories [])))

(defn- safe-read
  [input source]
  (try
    (binding [*read-eval* false]
      (read-string source))
    (catch Exception error
      (fail! (str "cannot parse " input ": " (fmt.Sprint error))))))

(def ^:private project-fields
  #{:dependencies :repositories :local-repo :exclusions})

(defn- project-config
  [input source]
  (let [sources (project-form-sources input source)
        defproject-symbol (when-let [head (first sources)]
                            (safe-read input head))]
    (when-not (= 'defproject defproject-symbol)
      (project-error! input "must start with defproject"))
    (when (< (count sources) 3)
      (project-error! input "defproject requires a project name and version"))
    (let [[_ name-source _version-source & argument-sources] sources
          project-name (safe-read input name-source)]
      (when-not (or (symbol? project-name) (string? project-name))
        (project-error! input "defproject requires a literal project name"))
      (when (odd? (count argument-sources))
        (project-error! input "defproject must contain keyword/value pairs"))
      (let [project
            (reduce
             (fn [result [key-source value-source]]
               (let [key (safe-read input key-source)]
                 (when-not (keyword? key)
                   (project-error!
                    input
                    (str "defproject option must be a keyword: "
                         (pr-str key))))
                 (if (project-fields key)
                   (assoc result key (safe-read input value-source))
                   result)))
             {}
             (partition 2 argument-sources))
            dependencies (:dependencies project)
            local-repo (:local-repo project)
            global-exclusions
            (project-exclusions input ":exclusions" (:exclusions project))]
        (when-not (or (nil? dependencies) (vector? dependencies))
          (project-error! input ":dependencies must be a literal vector"))
        (when (and (contains? project :local-repo)
                   (not (and (string? local-repo)
                             (not (str/blank? local-repo)))))
          (project-error! input ":local-repo must be a nonblank string"))
        (cond->
         {:deps (into {}
                      (map #(project-dependency input global-exclusions %))
                      (or dependencies []))
          :mvn/repos (project-repositories input (:repositories project))}
          local-repo (assoc :mvn/local-repo local-repo))))))

(defn- dependency-config
  [input content]
  (let [start (skip-source-layout content 0)]
    (if (and (< start (count content))
             (= \( (.charAt content start)))
      (project-config input content)
      (let [form (safe-read input content)]
        (if (map? form)
          form
          (fail! (str input
                      " must contain an EDN map or literal defproject form")))))))

(defn- read-config [input host]
  (let [content
        (if (source/remote? input)
          (source/fetch-text host input)
          (let [[content error] (os.ReadFile input)]
            (when error
              (fail! (str "cannot read " input ": " (fmt.Sprint error))))
            (go/string content)))]
    (let [config (dependency-config input content)]
      (when-not (map? config)
        (fail! (str input " must contain an EDN map")))
      (when (and (contains? config :deps)
                 (not (map? (:deps config))))
        (fail! (str input " :deps must be a map")))
      (if (contains? config :deps)
        config
        (assoc config :deps {})))))

(defn- explicit-source?
  [value host]
  (or (source/remote? value)
      (str/ends-with? (str/lower-case value) ".edn")
      (str/ends-with? (str/lower-case value) "project.clj")
      ((:exists? host) value)))

(defn- parse-items
  [operands host]
  (loop [remaining operands items []]
    (if-let [value (first remaining)]
      (cond
        (explicit-source? value host)
        (recur (next remaining)
               (conj items {:kind :source :source value}))

        (re-matches library-pattern value)
        (let [request (library-coordinate value)
              following (second remaining)
              version
              (when (version-operand? following host)
                (valid-version! following))]
          (recur (drop (if version 2 1) remaining)
                 (conj items
                       {:kind :library
                        :request (assoc request :version version)})))

        :else
        (recur (next remaining)
               (conj items {:kind :source :source value})))
      items)))

(defn- source-base-dir [input]
  (if (source/remote? input) "." (path:filepath.Dir input)))

(defn- input-coordinate
  [lib coordinate input quiet]
  (if-let [root (:local/root coordinate)]
    (do
      (if (and (source/remote? input)
               (not (path:filepath.IsAbs root)))
        (do
          (when-not quiet
            (fmt.Fprintln
             os.Stderr
             (str "grenadine: warning: skipping " lib " from " input
                  ": relative :local/root " (pr-str root))))
          nil)
        (assoc coordinate :local/root
               (if (path:filepath.IsAbs root)
                 root
                 (path:filepath.Join (source-base-dir input) root)))))
    coordinate))

(defn- dependency-request
  [lib coordinate input quiet]
  (let [request (library-coordinate (str lib))]
    (when-not (map? coordinate)
      (fail! (str input " dependency " lib " must use a coordinate map")))
    (when-let [coordinate (input-coordinate lib coordinate input quiet)]
      (assoc request
             :version (:mvn/version coordinate)
             :coordinate coordinate
             :input input))))

(defn- input-bundle
  [operands host quiet]
  (reduce
   (fn [bundle item]
     (if (= :library (:kind item))
       (update bundle :requests conj (:request item))
       (let [input (:source item)
             config (read-config input host)
             requests
             (into []
                   (keep (fn [[lib coordinate]]
                           (dependency-request lib coordinate input quiet)))
                   (:deps config))]
         (cond-> (-> bundle
                     (update :requests into requests)
                     (update :mvn-repos merge (:mvn/repos config)))
           (contains? config :mvn/local-repo)
           (assoc :local-repo (:mvn/local-repo config))

           (contains? config :gitlibs/dir)
           (assoc :gitlibs-dir (:gitlibs/dir config))))))
   {:requests [] :mvn-repos {} :local-repo nil :gitlibs-dir nil}
   (parse-items operands host)))

(defn- bundle-repos
  [bundle]
  (configured-repos {:mvn/repos (:mvn-repos bundle)}))

(declare resolve-requests)

(defn- last-requests
  [requests]
  (->> requests
       (map-indexed vector)
       (reduce (fn [selected [index request]]
                 (assoc selected (:name request) [index request]))
               {})
       vals
       (sort-by first)
       (mapv second)))

(defn- resolved-deps
  [bundle host]
  (let [repos (bundle-repos bundle)
        requests (resolve-requests (last-requests (:requests bundle))
                                  host repos)]
    (reduce
     (fn [deps {:keys [name version coordinate]}]
       (assoc deps
              (symbol name)
              (or coordinate {:mvn/version version})))
     {}
     requests)))

(defn- operation-options
  [parsed bundle host]
  {:host host
   :repos (bundle-repos bundle)
   :local-repo (or (:repository parsed) (:local-repo bundle))
   :gitlibs-dir (or (:gitlibs parsed) (:gitlibs-dir bundle))
   :mediation (:mediation parsed)})

(declare coordinate-line)

(defn- print-installed!
  [{:keys [group artifact version lib coordinate]}]
  (fmt.Fprintln
   os.Stdout
   (if lib
     (str "Installed " (coordinate-line lib coordinate))
     (str "Installed " group "/" artifact " " version))))

(defn- print-summary!
  [result]
  (let [installed (count (or (:installed-libs result) (:fetched result)))
        already (count (or (:already-libs result) (:cached result)))]
    (fmt.Fprintln os.Stdout
                  (str "=> Installed: " installed
                       "  Already: " already
                       "  Total: " (+ installed already)))))

(defn- install-deps!
  [deps {:keys [host repos local-repo gitlibs-dir quiet mediation]}]
  (let [result
        (grenadine/install!
         deps
         (cond-> {:host host
                  :repos repos
                  :mediation mediation}
           local-repo (assoc :local-repo local-repo)
           gitlibs-dir (assoc :gitlibs-dir gitlibs-dir)
           (not quiet) (assoc :on-install print-installed!
                              :on-install-coordinate print-installed!)))]
    (when-not quiet
      (doseq [warning (:warnings result)]
        (fmt.Fprintln os.Stderr
                      (str "grenadine: warning: " (pr-str warning))))
      (print-summary! result))))

(defn- resolve-requests
  [requests host repos]
  (mapv
   (fn [{:keys [version coordinate] :as request}]
     (if (or version coordinate)
       request
       (assoc request :version
              (repo/latest-version request {:host host :repos repos}))))
   requests))

(defn- graph-input
  [parsed]
  (let [host (glojure-host/host)
        bundle (input-bundle (:operands parsed) host (:quiet parsed))]
    {:host host
     :bundle bundle
     :deps (resolved-deps bundle host)
     :options (operation-options parsed bundle host)}))

(defn- install!
  [{:keys [quiet] :as parsed}]
  (let [{:keys [deps options]} (graph-input parsed)]
    (install-deps! deps (assoc options :quiet quiet))))

(defn- expand-result
  [parsed]
  (let [{:keys [deps options] :as input} (graph-input parsed)
        basis (grenadine/calc-basis
               {:deps deps}
               (assoc options :fetch-artifacts? false))
        entries (->> (:libs basis)
                     (sort-by (comp str key))
                     vec)]
    (assoc input :basis basis :entries entries)))

(defn- coordinate-row
  [lib coordinate]
  [(str lib)
   (case (coordinate/coordinate-type coordinate)
     :mvn (:mvn/version coordinate)
     :git (or (:git/tag coordinate)
              (subs (:git/sha coordinate)
                    0 (min 12 (count (:git/sha coordinate)))))
     :local (:local/root coordinate))])

(defn- coordinate-line
  [lib coordinate]
  (str/join " " (coordinate-row lib coordinate)))

(defn- print-expansion!
  [{:keys [quiet] :as parsed}]
  (let [{:keys [basis entries]} (expand-result parsed)]
    (when-not quiet
      (print-table! (mapv (fn [[lib coordinate]]
                            (coordinate-row lib coordinate))
                          entries))
      (doseq [warning (:grenadine/warnings basis)]
        (fmt.Fprintln os.Stderr
                      (str "grenadine: warning: " (pr-str warning)))))))

(defn- installed-coordinate?
  [host local-repo coordinate]
  ((:exists? host)
   (path:filepath.Join local-repo (lock/artifact-path coordinate))))

(defn- list-expanded!
  [{:keys [quiet] :as parsed}]
  (let [{:keys [host options basis entries]}
        (expand-result parsed)
        local-repo (repo/local-repo options)
        statuses
        (mapv
         (fn [[lib coordinate]]
           [[lib coordinate]
            (case (coordinate/coordinate-type coordinate)
              :mvn
              (let [[group artifact classifier] (coordinate/split-lib lib)]
                (installed-coordinate?
                 host local-repo
                 (cond-> {:group group :artifact artifact
                          :version (:mvn/version coordinate)}
                   classifier (assoc :classifier classifier))))
              :git
              ((:exists? host)
               (gitlibs/checkout-dir
                lib (:git/sha coordinate) options))
              :local ((:exists? host) (:local/root coordinate)))])
         entries)
        installed (count (filter second statuses))
        missing (- (count statuses) installed)]
    (when-not quiet
      (print-table!
       (mapv (fn [[[lib coordinate] present?]]
               (cond-> (coordinate-row lib coordinate)
                 (not present?) (conj "MISSING")))
             statuses))
      (doseq [warning (:grenadine/warnings basis)]
        (fmt.Fprintln os.Stderr
                      (str "grenadine: warning: " (pr-str warning))))
      (fmt.Fprintln os.Stdout
                    (str "=> Installed: " installed
                         "  Missing: " missing
                         "  Total: " (count statuses))))))

(defn- directory-operand
  [operands host operation]
  (let [directories (filter #((:directory? host) %) operands)]
    (when (seq directories)
      (when-not (and (= 1 (count operands)) (= 1 (count directories)))
        (fail! (str operation
                    " accepts a Maven repository directory only as its "
                    "single item")))
      (first directories))))

(defn- inventory-options
  [parsed host operation]
  (if-let [directory (directory-operand (:operands parsed) host operation)]
    (do
      (when (:repository parsed)
        (fail! (str operation
                    " cannot combine a repository directory with "
                    "--repository")))
      (assoc parsed :repository directory :operands []))
    parsed))

(defn- list!
  [parsed]
  (let [host (glojure-host/host)
        parsed (inventory-options parsed host "--list")]
    (if (empty? (:operands parsed))
      (do
        (when (:mediator parsed)
          (fail! "--mediator is only valid with --list when dependency items are supplied"))
        (list-repository! parsed))
      (list-expanded! parsed))))

(defn- current-rows
  [entries {:keys [host repos]}]
  (let [cache (atom {})
        warnings (atom [])
        rows
        (mapv
         (fn [[lib coordinate]]
           (let [row (coordinate-row lib coordinate)]
             (if (= :mvn (coordinate/coordinate-type coordinate))
               (let [[group artifact] (coordinate/split-lib lib)
                     key [group artifact]
                     result
                     (if (contains? @cache key)
                       (get @cache key)
                       (let [result
                             (try
                               {:version
                                (repo/latest-version
                                 {:group group :artifact artifact}
                                 {:host host :repos repos})}
                               (catch Exception error
                                 {:error (fmt.Sprint error)}))]
                         (swap! cache assoc key result)
                         (when-let [message (:error result)]
                           (swap! warnings conj
                                  {:name (str group "/" artifact)
                                   :message message}))
                         result))
                     latest (:version result)]
                 (if (and latest
                          (version/newer? latest (:mvn/version coordinate)))
                   (conj row latest)
                   row))
               row)))
         entries)]
    {:rows rows :warnings @warnings}))

(defn- print-current!
  [entries options basis-warnings]
  (let [{:keys [rows warnings]} (current-rows entries options)]
    (print-table! rows)
    (doseq [warning basis-warnings]
      (fmt.Fprintln os.Stderr
                    (str "grenadine: warning: " (pr-str warning))))
    (doseq [{:keys [name message]} warnings]
      (fmt.Fprintln os.Stderr
                    (str "grenadine: warning: cannot determine latest "
                         "version for " name ": " message)))))

(defn- inventory-entries
  [root]
  (mapv
   (fn [{:keys [group artifact version]}]
     [(symbol (str group "/" artifact)) {:mvn/version version}])
   (repository-coordinates root)))

(defn- current!
  [{:keys [quiet] :as parsed}]
  (let [host (glojure-host/host)
        parsed (inventory-options parsed host "--current")]
    (if (empty? (:operands parsed))
      (do
        (when (:mediator parsed)
          (fail! "--mediator is only valid with --current when items are supplied"))
        (when-not quiet
          (let [root (repo/local-repo
                      {:host host :local-repo (:repository parsed)})]
            (print-current!
             (inventory-entries root)
             {:host host :repos lock/default-repos}
             []))))
      (let [{:keys [options basis entries]} (expand-result parsed)]
        (when-not quiet
          (print-current! entries options (:grenadine/warnings basis)))))))

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

(defn- delete-requests!
  [requests root host quiet action]
  (let [requests (mapv #(removal-target root %)
                       (validate-delete-requests! requests))
        result
        (reduce
         (fn [counts {:keys [target] :as request}]
           (if ((:exists? host) target)
             (do
               ((:delete! host) target)
               (when-not quiet (print-removal! action request))
               (update counts :removed inc))
             (do
               (when-not quiet (print-removal! "Missing" request))
               (update counts :missing inc))))
         {:removed 0 :missing 0}
         requests)]
    (when-not quiet
      (fmt.Fprintln os.Stdout
                    (str "=> " action ": " (:removed result)
                         "  Missing: " (:missing result)
                         "  Total: " (+ (:removed result)
                                        (:missing result)))))))

(defn- delete!
  [{:keys [quiet] :as parsed}]
  (let [host (glojure-host/host)
        bundle (input-bundle (:operands parsed) host quiet)
        root (absolute-path
              (repo/local-repo
               {:host host
                :local-repo (or (:repository parsed)
                                (:local-repo bundle))}))]
    (validate-maven-removal! (:requests bundle))
    (delete-requests! (:requests bundle) root host quiet "Deleted")))

(defn- coordinate-request
  [{:keys [group artifact version]}]
  {:name (str group "/" artifact)
   :group group
   :artifact artifact
   :version version})

(defn- selected-coordinates
  [resolution]
  (->> (:selected resolution)
       vals
       (map :coords)))

(defn- remove!
  [{:keys [quiet] :as parsed}]
  (let [host (glojure-host/host)
        bundle (input-bundle (:operands parsed) host quiet)
        _ (validate-maven-removal! (:requests bundle))
        options (operation-options parsed bundle host)
        root (absolute-path (repo/local-repo options))
        installed (repository-coordinates root)
        installed-set
        (set (map (juxt :group :artifact :version) installed))
        selected-requests (last-requests (:requests bundle))
        explicit
        (filter :version selected-requests)
        implicit
        (remove :version selected-requests)
        present-explicit
        (filter #(contains? installed-set
                            [(:group %) (:artifact %) (:version %)])
                explicit)
        missing-explicit
        (remove #(contains? installed-set
                            [(:group %) (:artifact %) (:version %)])
                explicit)
        implicit-roots
        (mapcat
         (fn [request]
           (let [matches
                 (filter #(and (= (:group request) (:group %))
                               (= (:artifact request) (:artifact %)))
                         installed)]
             (if (seq matches)
               (map coordinate-request matches)
               [request])))
         implicit)
        missing-implicit (filter (complement :version) implicit-roots)
        concrete-implicit (filter :version implicit-roots)
        warnings (atom [])
        fetch-pom (repo/pom-fetcher (assoc options :repos []))
        pom-fn
        (fn [coordinate]
          (try
            (grenadine/effective-pom coordinate {:fetch-pom fetch-pom})
            (catch Exception error
              (swap! warnings conj
                     {:warning :missing-local-pom
                      :coordinate coordinate
                      :message (fmt.Sprint error)})
              {:coords coordinate :deps []})))
        resolve-one
        (fn [requests]
          (when (seq requests)
            (grenadine/resolve-graph
             (reduce (fn [deps request]
                       (assoc deps (symbol (:name request))
                              (or (:coordinate request)
                                  {:mvn/version (:version request)})))
                     {}
                     requests)
             {:pom-fn pom-fn :mediation (:mediation parsed)})))
        combined (resolve-one present-explicit)
        separate (keep #(resolve-one [%]) concrete-implicit)
        coordinates
        (->> (concat (selected-coordinates combined)
                     (mapcat selected-coordinates separate))
             (reduce (fn [selected coordinate]
                       (assoc selected
                              [(:group coordinate)
                               (:artifact coordinate)
                               (:version coordinate)]
                              coordinate))
                     {})
             vals
             (sort-by (juxt :group :artifact :version)))
        requests
        (concat (map coordinate-request coordinates)
                missing-explicit
                missing-implicit)]
    (when-not quiet
      (doseq [warning @warnings]
        (fmt.Fprintln os.Stderr
                      (str "grenadine: warning: " (pr-str warning)))))
    (delete-requests! (vec requests) root host quiet "Removed")))

(defn -main [& argv]
  (try
    (github.com:glojurelang:glojure:pkg:stdlib:clojure:core:protocols.LoadNS)
    (let [{:keys [help version list current install delete remove expand mediators
                  operands repository deprecated-add]
           :as parsed}
          (parse-options argv)
          _ (when deprecated-add
              (fmt.Fprintln
               os.Stderr
               "grenadine: warning: --add is deprecated; use --install"))
          modes (filter identity
                        [list current install delete remove expand mediators])
          mediation (mediation-strategy (:mediator parsed))
          options (assoc parsed
                         :mediation mediation)]
      (cond
        help (fmt.Fprintln os.Stdout usage)
        version (fmt.Fprintln os.Stdout
                              (str "grenadine v" build-info/version))
        (> (count modes) 1)
        (fail! (str "--list, --current, --install, --delete, --remove, "
                    "--expand, and --mediators "
                    "are mutually exclusive"))
        (and (:mediator parsed)
             (not (or install remove expand
                      (and list (seq operands))
                      (and current (seq operands)))))
        (fail! (str "--mediator is only valid with --install, --remove, "
                    "--expand, --list with items, or --current with items"))
        mediators
        (cond
          (seq operands) (fail! "--mediators does not accept items")
          repository (fail! "--mediators does not use --repository")
          (:gitlibs parsed) (fail! "--mediators does not use --gitlibs")
          (:quiet parsed) (fail! "--mediators does not use --quiet")
          :else (list-mediators! options))
        list (list! options)
        current (current! options)
        install (if (empty? operands)
                  (fail! "--install requires at least one item")
                  (install! options))
        delete (if (empty? operands)
                 (fail! "--delete requires at least one item")
                 (delete! options))
        remove (if (empty? operands)
                 (fail! "--remove requires at least one item")
                 (remove! options))
        expand (if (empty? operands)
                 (fail! "--expand requires at least one item")
                 (print-expansion! options))
        (empty? operands) (fmt.Fprintln os.Stdout usage)
        :else (fail! "an explicit operation is required")))
    (catch Exception error
      (fmt.Fprintln os.Stderr (str "grenadine: " (fmt.Sprint error)))
      (exit! 1))))
