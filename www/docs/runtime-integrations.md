# Runtime integrations

Grenadine's portable core does not decide how a dialect changes its load path.
Runtime facades combine resolution and installation with the mutation hook
provided by each implementation. They are add-only: a top-level library that
is already loaded is retained, and a different requested coordinate produces
a `:loaded-lib-not-upgraded` warning.

Every facade provides this shape:

```clojure
(current-basis)
(add-lib lib coordinate)
(add-lib lib coordinate opts)
(add-libs libs)
(add-libs libs opts)
(add-deps deps-map)
(add-deps deps-map opts)
(sync-deps)
(sync-deps path)
(sync-deps path opts)
```

`add-deps` uses the map's `:deps` value. `sync-deps` reads `deps.edn` by
default. All facades default to `:tools-deps` mediation and request extracted
source roots for non-JVM runtimes.

## Glojure

Namespace: `glojure.deps`

Glojure's facade appends extracted roots through
`clojure.core/add-load-path`. The caller must provide a Glojure-native
Grenadine `:host` in the options. If `add-load-path` is unavailable, the
operation fails with `:grenadine.runtime/missing-load-path-hook`.

```clojure
(require '[glojure.deps :as deps])

(deps/add-lib 'org.clojure/data.csv
              {:mvn/version "1.1.0"}
              {:host my-glojure-host})
```

Status: the facade and load-path integration are present; embedding code must
supply the native host effects.

## Jolt

Namespace: `jolt.deps`

Jolt's facade preserves existing source roots and appends the extracted roots
through `jolt.host/source-roots` and `jolt.host/set-source-roots!`. A
Jolt-native Grenadine host must be supplied.

```clojure
(require '[jolt.deps :as deps])

(deps/sync-deps "deps.edn" {:host my-jolt-host})
```

Status: the facade and source-root mutation are present; the Jolt integration
must provide its native repository host.

## let-go

Namespace: `let-go.deps`

let-go exposes load-path mutation to Go embedders through `SetLoadPath`, but
does not currently expose it as a language function. Supply both the native
Grenadine host and an `:add-roots!` callback:

```clojure
(require '[let-go.deps :as deps])

(deps/add-deps
 '{:deps {org.clojure/data.csv {:mvn/version "1.1.0"}}}
 {:host my-let-go-host
  :add-roots! my-embedding-load-path-hook})
```

Status: dependency and add-only facade behavior are present; embedding code
must bridge source roots into let-go's load path.
