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

The portable API guarantees only the one-argument `add-deps` operation and
always returns `nil`.
The dialect namespaces below retain their richer APIs.
Glojure and Jolt embed this facade and their integration sources in the dialect
binary.

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

## Glojure

Namespace: `glojure.deps`

Glojure's facade appends extracted roots through
`clojure.core/add-load-path` and uses Glojure's native Grenadine host by
default.
If `add-load-path` is unavailable, the operation fails with
`:grenadine.runtime/missing-load-path-hook`.

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

```clojure
(require '[jolt.deps :as deps])

(deps/add-deps
 '{:deps {org.clojure/data.csv {:mvn/version "1.1.0"}}})
```
