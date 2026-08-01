(ns grenadine.host.glojure
  "Native effects for the Gloat-compiled Grenadine command."
  (:require [grenadine.build-info :as build-info]))

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
              (str "grenadine/" build-info/version))
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
   :mkdirs! mkdirs!
   :atomic-move! atomic-move!
   :delete! delete!
   :home-dir home-dir
   :getenv (fn [name] (os.Getenv name))})
