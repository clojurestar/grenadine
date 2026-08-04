(ns grenadine.cli
  (:require [clojure.string :as str]
            [grenadine.build-info :as build-info]
            [grenadine.coordinate :as coordinate]
            [grenadine.core :as grenadine]
            [grenadine.gitlibs :as gitlibs]
            [grenadine.host.glojure :as glojure-host]
            [grenadine.lock :as lock]
            [grenadine.repo :as repo]
            [grenadine.source :as source]))

(def usage
  (str
   "Usage: grenadine\n"
   "       grenadine [OPTIONS] --list [ITEM...]\n"
   "       grenadine [OPTIONS] [-M MODE] --add ITEM...\n"
   "       grenadine [OPTIONS] --delete ITEM...\n"
   "       grenadine [OPTIONS] [-M MODE] --remove ITEM...\n"
   "       grenadine [OPTIONS] [-M MODE] --expand ITEM...\n"
   "       grenadine --mediators\n"
   "       grenadine --help\n"
   "       grenadine --version\n\n"
   "ITEM is NAME [VERSION] or a local/remote DEPS-SOURCE.\n\n"
   "Options:\n"
   "  -R, --repository DIR  Use this Maven repository\n"
   "  -G, --gitlibs DIR     Use this Git library cache\n"
   "  -M, --mediator MODE   Use newest, nearest, or tools-deps\n"
   "      --list            List the repository or an expanded graph\n"
   "      --add             Install an expanded dependency graph\n"
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

        (= "--add" argument)
        (recur (next remaining) (assoc options :add true))

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

(def library-pattern #"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
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
  (doseq [{:keys [name coordinate]} requests]
    (when (and coordinate
               (not= :mvn (coordinate/coordinate-type coordinate)))
      (fail! (str "--delete and --remove support only Maven coordinates: "
                  name))))
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

(defn- explicit-source?
  [value host]
  (or (source/remote? value)
      (str/ends-with? (str/lower-case value) ".edn")
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
              (when (and following
                         (not (explicit-source? following host))
                         (not (re-matches library-pattern following)))
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
  [coordinate input]
  (if-let [root (:local/root coordinate)]
    (do
      (when (and (source/remote? input)
                 (not (path:filepath.IsAbs root)))
        (fail! (str input " contains a relative :local/root")))
      (assoc coordinate :local/root
             (if (path:filepath.IsAbs root)
               root
               (path:filepath.Join (source-base-dir input) root))))
    coordinate))

(defn- dependency-request
  [lib coordinate input]
  (let [request (library-coordinate (str lib))]
    (when-not (map? coordinate)
      (fail! (str input " dependency " lib " must use a coordinate map")))
    (let [coordinate (input-coordinate coordinate input)]
    (assoc request
           :version (:mvn/version coordinate)
           :coordinate coordinate
           :input input))))

(defn- input-bundle
  [operands host]
  (reduce
   (fn [bundle item]
     (if (= :library (:kind item))
       (update bundle :requests conj (:request item))
       (let [input (:source item)
             config (read-config input host)
             requests
             (mapv (fn [[lib coordinate]]
                     (dependency-request lib coordinate input))
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
        bundle (input-bundle (:operands parsed) host)]
    {:host host
     :bundle bundle
     :deps (resolved-deps bundle host)
     :options (operation-options parsed bundle host)}))

(defn- add!
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

(defn- coordinate-line
  [lib coordinate]
  (case (coordinate/coordinate-type coordinate)
    :mvn (str lib " " (:mvn/version coordinate))
    :git (str lib " "
              (or (:git/tag coordinate)
                  (subs (:git/sha coordinate)
                        0 (min 12 (count (:git/sha coordinate))))))
    :local (str lib " " (:local/root coordinate))))

(defn- print-expansion!
  [{:keys [quiet] :as parsed}]
  (let [{:keys [basis entries]} (expand-result parsed)]
    (when-not quiet
      (doseq [[lib coordinate] entries]
        (fmt.Fprintln os.Stdout (coordinate-line lib coordinate)))
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
              (let [[group artifact] (coordinate/split-lib lib)]
                (installed-coordinate?
                 host local-repo
                 {:group group :artifact artifact
                  :version (:mvn/version coordinate)}))
              :git
              ((:exists? host)
               (gitlibs/checkout-dir
                lib (:git/sha coordinate) options))
              :local ((:exists? host) (:local/root coordinate)))])
         entries)
        installed (count (filter second statuses))
        missing (- (count statuses) installed)]
    (when-not quiet
      (doseq [[[lib coordinate] present?] statuses]
        (fmt.Fprintln os.Stdout
                      (str (coordinate-line lib coordinate)
                           (when-not present? " MISSING"))))
      (doseq [warning (:grenadine/warnings basis)]
        (fmt.Fprintln os.Stderr
                      (str "grenadine: warning: " (pr-str warning))))
      (fmt.Fprintln os.Stdout
                    (str "=> Installed: " installed
                         "  Missing: " missing
                         "  Total: " (count statuses))))))

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
        bundle (input-bundle (:operands parsed) host)
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
        bundle (input-bundle (:operands parsed) host)
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
    (let [{:keys [help version list add delete remove expand mediators operands
                  repository]
           :as parsed}
          (parse-options argv)
          modes (filter identity [list add delete remove expand mediators])
          mediation (mediation-strategy (:mediator parsed))
          options (assoc parsed
                         :mediation mediation)]
      (cond
        help (fmt.Fprintln os.Stdout usage)
        version (fmt.Fprintln os.Stdout
                              (str "grenadine v" build-info/version))
        (> (count modes) 1)
        (fail! (str "--list, --add, --delete, --remove, --expand, and "
                    "--mediators "
                    "are mutually exclusive"))
        (and (:mediator parsed)
             (not (or add remove expand (and list (seq operands)))))
        (fail! (str "--mediator is only valid with --add, --remove, "
                    "--expand, or --list with items"))
        mediators
        (cond
          (seq operands) (fail! "--mediators does not accept items")
          repository (fail! "--mediators does not use --repository")
          (:gitlibs parsed) (fail! "--mediators does not use --gitlibs")
          (:quiet parsed) (fail! "--mediators does not use --quiet")
          :else (list-mediators! options))
        list (if (seq operands)
               (list-expanded! options)
               (list-repository! options))
        add (if (empty? operands)
              (fail! "--add requires at least one item")
              (add! options))
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
