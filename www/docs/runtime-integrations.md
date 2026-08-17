# Runtime integrations

Grenadine's portable core does not decide how a dialect changes its load path.
Runtime facades combine resolution and installation with the mutation hook
provided by each implementation.
They are add-only: a top-level library that is already loaded is retained, and
a different requested coordinate produces a `:loaded-lib-not-upgraded`
warning.

## Portable API

Dialect-agnostic code should use `clojurestar.deps`:

```clojure
(require '[clojurestar.deps :as deps])

(deps/add-deps
 '{:deps {org.clojure/data.csv {:mvn/version "1.1.0"}}})
```

The portable API guarantees the one-argument `add-deps` operation and the
`require-deps` macro. Both return `nil`.
The dialect namespaces below retain their richer APIs.
Glojure and Jolt embed this facade and their integration sources in the dialect
binary.
On Gobb, the facade selects Gobb's built-in `gobb.deps` backend through the
`:gobb` reader feature.

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

`add-deps` uses the map's `:deps` value.
`sync-deps` reads `deps.edn` by default.
Both facades default to `:tools-deps` mediation and request extracted source
roots.
`current-basis` returns accumulated tools.deps-shaped `:libs`, `:classpath`,
and `:classpath-roots` data.

Use `require-deps` to acquire and import namespaces in one operation:

```clojure
(require '[clojurestar.deps :refer [require-deps]])

(require-deps
 ["mvn:dev.weavejester/medley@1.10.0/medley.core" :as medley]
 ["mvn:org.clojure/math.combinatorics@0.3.0/clojure.math.combinatorics"
  :as combo])
```

Literal vectors use require-style syntax without quoting. Quoted vectors and
expressions that evaluate to libspec vectors remain compatible. An optional
leading map accepts `:mvn/local-repo` and `:gitlibs/dir`; `:cache-dir` remains
a compatibility alias for the Gist cache root. Libspecs accept `:as` and
explicit `:refer [...]`.

A pinned Gist file can be written as either
`gist:<owner>/<id>/<file>@<revision>` or
`gist:<owner>/<id>/<revision>/<file>`; both forms use the same cache entry.

Gist source is cached under `gist/` in the runtime's effective Gitlibs
directory. The dialect-specific `*_GITLIBS_DIR` setting wins, followed by
`GRENADINE_GITLIBS_DIR`, the tools.gitlibs-compatible `GITLIBS` variable, and
the runtime default.

The non-JVM facades treat `org.clojure/clojure` and
`org.clojure/clojurescript` as terminal libraries supplied by the host. They
are neither acquired nor expanded transitively. Explicit coordinates such as
`org.clojure/spec.alpha` remain ordinary dependencies.

## Glojure

Namespace: `glojure.deps`

Glojure's facade appends extracted roots through
`clojure.core/add-load-path` and uses Glojure's native Grenadine host by
default.
If `add-load-path` is unavailable, the operation fails with
`:grenadine.runtime/missing-load-path-hook`.

`GLOJURE_MAVEN_REPOSITORY` overrides the shared
`GRENADINE_MAVEN_REPOSITORY`. `GLOJURE_GITLIBS_DIR` similarly overrides
`GRENADINE_GITLIBS_DIR`. Explicit operation or deps-map paths take precedence
over environment settings.

```clojure
(require '[glojure.deps :as deps])

(deps/add-lib 'org.clojure/data.csv
              {:mvn/version "1.1.0"})
```

## Jolt

Namespace: `jolt.deps`

Jolt's native dependency implementation preserves existing source roots and
appends newly resolved roots.
It supports Maven, Git, and local coordinates.
The implementation is owned by Jolt and vendors the Grenadine namespaces it
uses into the Jolt binary.

`JOLT_MAVEN_REPOSITORY` and `JOLT_GITLIBS_DIR` override their shared
`GRENADINE_*` counterparts. Explicit `:mvn/local-repo` takes precedence.

```clojure
(require '[jolt.deps :as deps])

(deps/add-deps
 '{:deps {org.clojure/data.csv {:mvn/version "1.1.0"}}})
```

## Gobb

Namespace: `gobb.deps`

Gobb owns its dependency resolver and load-path integration. Portable code can
still call `clojurestar.deps/add-deps`; Grenadine dispatches that facade to
`gobb.deps` when the `:gobb` reader feature is active. Gobb does not use the
Glojure effect host or load a separate Grenadine runtime adapter.

`GOBB_MAVEN_REPOSITORY` and `GOBB_GITLIBS_DIR` override their shared
`GRENADINE_*` counterparts. Explicit `:mvn/local-repo` takes precedence.
