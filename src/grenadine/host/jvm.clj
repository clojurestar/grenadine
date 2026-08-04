(ns grenadine.host.jvm
  "JVM host implementation for Grenadine repository operations."
  (:require [clojure.edn :as edn])
  (:import [java.io ByteArrayOutputStream File FileInputStream]
           [java.net HttpURLConnection URL]
           [java.nio.charset StandardCharsets]
           [java.nio.file Files Path Paths StandardCopyOption]
           [java.security MessageDigest]
           [java.util.zip ZipInputStream]))

(defn- path
  [value]
  (Paths/get value (make-array String 0)))

(defn- read-all
  [stream]
  (with-open [input stream
              output (ByteArrayOutputStream.)]
    (let [buffer (byte-array 16384)]
      (loop []
        (let [read (.read input buffer)]
          (when (pos? read)
            (.write output buffer 0 read)
            (recur)))))
    (.toByteArray output)))

(defn- http-get
  [url]
  (let [connection ^HttpURLConnection (.openConnection (URL. url))]
    (.setInstanceFollowRedirects connection true)
    (.setConnectTimeout connection 15000)
    (.setReadTimeout connection 30000)
    (.setRequestProperty connection "User-Agent" "grenadine/0")
    (let [status (.getResponseCode connection)]
      {:status status
       :headers {}
       :body (when (= status 200)
               (read-all (.getInputStream connection)))})))

(defn- digest
  [algorithm ^bytes bytes]
  (let [name (case algorithm :sha1 "SHA-1" :sha256 "SHA-256")
        result (.digest (MessageDigest/getInstance name) bytes)]
    (apply str (map #(format "%02x" (bit-and 255 %)) result))))

(defn- atomic-move!
  [from to]
  (try
    (Files/move
     (path from)
     (path to)
     (into-array StandardCopyOption
                 [StandardCopyOption/ATOMIC_MOVE
                  StandardCopyOption/REPLACE_EXISTING]))
    ;; Babashka does not expose AtomicMoveNotSupportedException as a resolvable
    ;; class, but it does support Files/move and Throwable. Retrying without the
    ;; atomic option is correct for the JVM exception and keeps this host usable
    ;; from bb's native image.
    (catch Throwable _
      (Files/move
       (path from)
       (path to)
       (into-array StandardCopyOption
                   [StandardCopyOption/REPLACE_EXISTING]))))
  nil)

(defn- delete-tree!
  [target]
  (let [file (File. target)]
    (when (.exists file)
      (doseq [child (reverse (file-seq file))]
        (when-not (.delete ^File child)
          (throw (ex-info (str "Unable to delete " child)
                          {:path (str child)}))))))
  nil)

(defn- run-process
  [{:keys [args dir env]}]
  (let [builder (ProcessBuilder. ^java.util.List (mapv str args))]
    (when dir (.directory builder (File. dir)))
    (when env
      (let [environment (.environment builder)]
        (doseq [[key value] env]
          (.put environment (str key) (str value)))))
    (let [process (.start builder)
          read-background
          (fn [stream]
            (let [result (promise)
                  thread
                  (Thread.
                   (fn []
                     (deliver result
                              (String. ^bytes (read-all stream)
                                       StandardCharsets/UTF_8))))]
              (.setDaemon thread true)
              (.start thread)
              result))
          out (read-background (.getInputStream process))
          err (read-background (.getErrorStream process))
          exit (.waitFor process)]
      {:exit exit :out @out :err @err})))

(defn- extract-jar!
  [jar destination]
  (let [marker (str destination "/.grenadine-complete")]
    (when-not (Files/exists (path marker)
                            (make-array java.nio.file.LinkOption 0))
      (let [temporary (str destination ".part")
            temporary-path (.normalize (.toAbsolutePath (path temporary)))]
        (delete-tree! temporary)
        (Files/createDirectories
         temporary-path
         (make-array java.nio.file.attribute.FileAttribute 0))
        (try
          (with-open [input (ZipInputStream. (FileInputStream. jar))]
            (loop [entry (.getNextEntry input)]
              (when entry
                (let [target (.normalize
                              (.resolve temporary-path (.getName entry)))]
                  (when-not (.startsWith target temporary-path)
                    (throw (ex-info (str "Unsafe JAR entry: " (.getName entry))
                                    {:entry (.getName entry)})))
                  (if (.isDirectory entry)
                    (Files/createDirectories
                     target
                     (make-array java.nio.file.attribute.FileAttribute 0))
                    (do
                      (when-let [parent (.getParent target)]
                        (Files/createDirectories
                         parent
                         (make-array java.nio.file.attribute.FileAttribute 0)))
                      (Files/copy
                       input target
                       (into-array StandardCopyOption
                                   [StandardCopyOption/REPLACE_EXISTING]))))
                  (.closeEntry input)
                  (recur (.getNextEntry input))))))
          (Files/write
           (.resolve temporary-path ".grenadine-complete")
           (byte-array 0)
           (make-array java.nio.file.OpenOption 0))
          (when (Files/exists (path destination)
                              (make-array java.nio.file.LinkOption 0))
            (delete-tree! destination))
          (atomic-move! temporary destination)
          (catch Throwable error
            (delete-tree! temporary)
            (throw error))))))
  destination)

(defn host
  "Return a fresh JVM host function map."
  []
  {:http-get http-get
   :read-bytes #(Files/readAllBytes (path %))
   :write-bytes!
   (fn [target bytes]
     (Files/write (path target) bytes
                  (make-array java.nio.file.OpenOption 0))
     nil)
   :bytes->utf8 #(String. ^bytes % StandardCharsets/UTF_8)
   :utf8->bytes #(.getBytes ^String % StandardCharsets/UTF_8)
   :digest digest
   :byte-count (fn [^bytes value] (alength value))
   :exists? #(Files/exists (path %) (make-array java.nio.file.LinkOption 0))
   :directory? #(.isDirectory (File. %))
   :regular-file? #(.isFile (File. %))
   :find-files
   (fn [root predicate]
     (->> (file-seq (File. root))
          (filter #(.isFile ^File %))
          (map #(.getCanonicalPath ^File %))
          (filter predicate)
          vec))
   :canonical-path #(.getCanonicalPath (File. %))
   :absolute-path #(.getAbsolutePath (File. %))
   :run-process run-process
   :read-edn #(edn/read-string %)
   :mkdirs!
   (fn [target]
     (Files/createDirectories
      (path target)
      (make-array java.nio.file.attribute.FileAttribute 0))
     nil)
   :atomic-move! atomic-move!
   :delete!
   (fn [target] (Files/deleteIfExists (path target)) nil)
   :delete-tree! delete-tree!
   :extract-jar! extract-jar!
   :home-dir #(System/getProperty "user.home")
   :getenv #(System/getenv %)})
