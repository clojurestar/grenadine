(ns grenadine.cli
  (:require [grenadine.build-info :as build-info]
            [grenadine.core :as grenadine]
            [grenadine.host.glojure :as glojure-host]
            [grenadine.lock :as lock]
            [grenadine.source :as source]))

(def usage
  (str
   "Usage: grenadine [OPTIONS] DEPS-SOURCE\n"
   "       grenadine --help\n"
   "       grenadine --version\n\n"
   "Install the Maven dependencies in a local or remote DEPS-SOURCE.\n\n"
   "Options:\n"
   "  -R, --repository DIR  Install into this Maven repository\n"
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

        (:file options)
        (fail! (str "unexpected argument: " argument))

        :else
        (recur (next remaining) (assoc options :file argument)))
      options)))

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
      (when-not (contains? config :deps)
        (fail! (str input " does not contain :deps")))
      (when-not (map? (:deps config))
        (fail! (str input " :deps must be a map")))
      config)))

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

(defn- install! [{:keys [file repository quiet]}]
  (let [host (glojure-host/host)
        config (read-config file host)
        local-repo (or repository (:mvn/local-repo config))
        result
        (grenadine/install!
         (:deps config)
         (cond-> {:host host
                  :repos (configured-repos config)
                  :mediation :tools-deps}
           local-repo (assoc :local-repo local-repo)
           (not quiet) (assoc :on-install print-installed!)))]
    (when-not quiet
      (doseq [warning (:warnings result)]
        (fmt.Fprintln os.Stderr
                      (str "grenadine: warning: " (pr-str warning))))
      (print-summary! result))))

(defn -main [& argv]
  (try
    (github.com:glojurelang:glojure:pkg:stdlib:clojure:core:protocols.LoadNS)
    (let [{:keys [help version file] :as options} (parse-options argv)]
      (cond
        help (fmt.Fprintln os.Stdout usage)
        version (fmt.Fprintln os.Stdout
                              (str "grenadine v" build-info/version))
        (nil? file) (fail! "a deps source is required")
        :else (install! options)))
    (catch Exception error
      (fmt.Fprintln os.Stderr (str "grenadine: " (fmt.Sprint error)))
      (exit! 1))))
