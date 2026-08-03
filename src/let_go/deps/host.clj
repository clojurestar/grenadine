(ns let-go.deps.host
  "Source fallback for let-go releases predating the built-in native host.")

(defn host [] {})

(defn add-source-roots!
  [_]
  (throw
   (ex-info "This let-go build does not include dynamic dependency support"
            {:type :grenadine.runtime/unsupported-host})))
