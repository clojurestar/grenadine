(ns grenadine.host.glojure
  "Native effects for the Gloat-compiled Grenadine command.")

(defn- go-error!
  [operation error]
  (when-not (nil? error)
    (throw (Exception. (str operation ": " (fmt.Sprint error))))))

(defn- http-response
  [status body]
  {:status status :headers {} :body body})

(defn- http-get
  [url]
  (let [[request request-error]
        (net:http.NewRequest net:http.MethodGet url nil)]
    (if-not (nil? request-error)
      (http-response 0 nil)
      (do
        (.Set (.Header request) "User-Agent"
              "grenadine")
        (let [[response response-error] (.Do net:http.DefaultClient request)]
          (if-not (nil? response-error)
            (http-response 0 nil)
            (let [[body body-error] (io.ReadAll (.Body response))]
              (.Close (.Body response))
              (if-not (nil? body-error)
                (http-response 0 nil)
                (http-response (.StatusCode response) body)))))))))

(defn- read-bytes
  [path]
  (let [[data error] (os.ReadFile path)]
    (go-error! (str "read " path) error)
    data))

(defn- write-bytes!
  [path data]
  (go-error! (str "write " path) (os.WriteFile path data 0644))
  nil)

(defn- digest
  [algorithm data]
  (case algorithm
    :sha1 (fmt.Sprintf "%x" (crypto:sha1.Sum data))
    :sha256 (fmt.Sprintf "%x" (crypto:sha256.Sum256 data))
    (throw (Exception. (str "unsupported digest algorithm " algorithm)))))

(defn- mkdirs!
  [path]
  (go-error! (str "create directory " path) (os.MkdirAll path 0755))
  nil)

(defn- atomic-move!
  [source destination]
  (let [remove-error (os.Remove destination)]
    (when (and (not (nil? remove-error))
               (not (os.IsNotExist remove-error)))
      (go-error! (str "remove " destination) remove-error)))
  (go-error! (str "move " source " to " destination)
             (os.Rename source destination))
  nil)

(defn- delete!
  [path]
  (go-error! (str "delete " path) (os.RemoveAll path))
  nil)

(defn- home-dir
  []
  (let [[home error] (os.UserHomeDir)]
    (go-error! "find home directory" error)
    home))

(defn- canonical-path
  [value]
  (let [[real error] (path:filepath.EvalSymlinks value)]
    (let [[absolute absolute-error]
          (path:filepath.Abs (if (nil? error) real value))]
      (go-error! (str "resolve path " value) absolute-error)
      (path:filepath.Clean absolute))))

(defn- file-info [value]
  (let [[info error] (os.Stat value)]
    (when (nil? error) info)))

(defn- run-process
  [{:keys [args]}]
  (let [[command-name & command-args] args
        command (apply os:exec.Command command-name (vec command-args))
        [stdout stdout-error] (.StdoutPipe command)
        [stderr stderr-error] (.StderrPipe command)]
    (if (or (not (nil? stdout-error)) (not (nil? stderr-error)))
      {:exit 1 :out "" :err "Unable to create process pipes"}
      (let [start-error (.Start command)]
        (if-not (nil? start-error)
          {:exit 1 :out "" :err (fmt.Sprint start-error)}
          (let [[out out-error] (io.ReadAll stdout)
                [err err-error] (io.ReadAll stderr)
                wait-error (.Wait command)]
            {:exit (if (nil? wait-error)
                     0
                     (.ExitCode (.ProcessState command)))
             :out (if (nil? out-error) (go/string out) "")
             :err (if (nil? err-error) (go/string err) "")}))))))

(defn- find-files
  [root predicate]
  (let [found (atom [])]
    (path:filepath.Walk
     root
     (fn [path info error]
       (when (and (nil? error) (not (nil? info))
                  (not (.IsDir info)) (predicate path))
         (swap! found conj (canonical-path path)))
       nil))
    @found))

(defn- safe-archive-path
  [root name]
  (let [target (path:filepath.Join root name)
        [relative error] (path:filepath.Rel root target)]
    (go-error! (str "resolve archive entry " name) error)
    (when (or (= relative "..")
              (path:filepath.IsAbs relative)
              (.HasPrefix strings relative
                          (str ".." (go/string os.PathSeparator))))
      (throw (Exception. (str "unsafe archive entry " name))))
    target))

(defn- extract-jar!
  [archive destination]
  (when-not (file-info destination)
    (let [temporary (str destination ".part")
          _ (os.RemoveAll temporary)
          _ (mkdirs! temporary)
          [reader error] (archive:zip.OpenReader archive)]
      (go-error! (str "open " archive) error)
      (doseq [entry (.File reader)]
        (let [name (.Name entry)
              target (safe-archive-path temporary name)
              info (.FileInfo entry)]
          (cond
            (.IsDir info)
            (mkdirs! target)

            (not (.IsRegular (.Mode info)))
            (throw (Exception. (str "unsupported archive entry " name)))

            :else
            (do
              (mkdirs! (path:filepath.Dir target))
              (let [[input input-error] (.Open entry)
                    [output output-error]
                    (os.OpenFile target
                                 (bit-or os.O_CREATE os.O_WRONLY os.O_TRUNC)
                                 0644)]
                (go-error! (str "open archive entry " name) input-error)
                (go-error! (str "create " target) output-error)
                (let [[_ copy-error] (io.Copy output input)]
                  (.Close input)
                  (.Close output)
                  (go-error! (str "extract " name) copy-error)))))))
      (.Close reader)
      (atomic-move! temporary destination)))
  nil)

(defn host
  []
  {:http-get http-get
   :read-bytes read-bytes
   :write-bytes! write-bytes!
   :bytes->utf8 (fn [data] (go/string data))
   :digest digest
   :byte-count (fn [data] (go/len data))
   :exists? (fn [path]
              (let [[_ error] (os.Stat path)]
                (nil? error)))
   :directory? (fn [path]
                 (when-let [info (file-info path)] (.IsDir info)))
   :regular-file? (fn [path]
                    (when-let [info (file-info path)]
                      (.IsRegular (.Mode info))))
   :canonical-path canonical-path
   :absolute-path canonical-path
   :find-files find-files
   :extract-jar! extract-jar!
   :run-process run-process
   :read-edn read-string
   :mkdirs! mkdirs!
   :atomic-move! atomic-move!
   :delete! delete!
   :delete-tree! delete!
   :home-dir home-dir
   :getenv (fn [name] (os.Getenv name))})
