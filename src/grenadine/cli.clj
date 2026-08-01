(ns grenadine.cli
  (:require [grenadine.build-info :as build-info]
            [grenadine.core :as grenadine]
            [grenadine.host.glojure :as glojure-host]
            [grenadine.lock :as lock]))

(def usage
  (str
   "Usage: grenadine [--repo DIR|--repo=DIR] DEPS-FILE\n"
   "       grenadine --help\n"
   "       grenadine --version\n\n"
   "Install the Maven dependencies in DEPS-FILE.\n\n"
   "Options:\n"
   "  --repo DIR       Install into this Maven repository\n"
   "  -h, --help       Show this help\n"
   "  -V, --version    Show the Grenadine version"))

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

        (= "--repo" argument)
        (if-let [repository (second remaining)]
          (recur (drop 2 remaining) (assoc options :repo repository))
          (fail! "--repo requires a directory"))

        (.startsWith argument "--repo=")
        (let [repository (subs argument (count "--repo="))]
          (when (empty? repository)
            (fail! "--repo requires a directory"))
          (recur (next remaining) (assoc options :repo repository)))

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

(defn- read-config [file]
  (let [[content error] (os.ReadFile file)]
    (when error
      (fail! (str "cannot read " file ": " (fmt.Sprint error))))
    (let [config
          (try
            (read-string (go/string content))
            (catch Exception error
              (fail! (str "cannot parse " file ": " (fmt.Sprint error)))))]
      (when-not (map? config)
        (fail! (str file " must contain an EDN map")))
      (when-not (contains? config :deps)
        (fail! (str file " does not contain :deps")))
      (when-not (map? (:deps config))
        (fail! (str file " :deps must be a map")))
      config)))

(defn- install! [{:keys [file repo]}]
  (let [config (read-config file)
        local-repo (or repo (:mvn/local-repo config))
        result
        (grenadine/install!
         (:deps config)
         (cond-> {:host (glojure-host/host)
                  :repos (configured-repos config)
                  :mediation :tools-deps}
           local-repo (assoc :local-repo local-repo)))]
    (doseq [warning (:warnings result)]
      (fmt.Fprintln os.Stderr
                    (str "grenadine: warning: " (pr-str warning))))))

(defn -main [& argv]
  (try
    (github.com:glojurelang:glojure:pkg:stdlib:clojure:core:protocols.LoadNS)
    (let [{:keys [help version file] :as options} (parse-options argv)]
      (cond
        help (fmt.Fprintln os.Stdout usage)
        version (fmt.Fprintln os.Stdout
                              (str "grenadine v" build-info/version))
        (nil? file) (fail! "a deps file is required")
        :else (install! options)))
    (catch Exception error
      (fmt.Fprintln os.Stderr (str "grenadine: " (fmt.Sprint error)))
      (exit! 1))))
