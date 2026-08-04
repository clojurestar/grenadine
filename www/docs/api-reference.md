# Core API reference

The supported portable API is exposed by `grenadine.core`. Lower-level
namespaces implement it and should not be required for ordinary use. Host
constructors such as `grenadine.host.jvm/host` provide platform effects.

## High-level operations

### `calc-basis`

```clojure
(calc-basis deps-map opts) ;=> basis
```

Resolves Maven, Git, and local coordinates and returns tools.deps-compatible
`:libs`, `:classpath`, and `:classpath-roots` keys. `:resolve-args` accepts
`:extra-deps`, `:override-deps`, and `:default-deps`; `:classpath-args` accepts
`:extra-paths`, `:replace-paths`, and `:classpath-overrides`. Grenadine adds
namespaced lock, procurement, warning, and source-root details.

### `expand-deps`

```clojure
(expand-deps deps opts) ;=> expansion
```

Runs portable tools.deps-style tree expansion for arbitrary coordinate types.
The required `:coord-id`, `:coord-deps`, and `:compare-versions` functions
identify a coordinate, return its child dependency entries, and order two
coordinates for the same library. `:known-coordinate?` and `:base-lib` may
customize validation and exclusion matching.

`:override-deps` replaces coordinates at every occurrence, `:default-deps`
fills missing coordinates, and `:trace? true` includes the traversal log and
version map. The result contains selected `:libs`, stable first-inclusion
`:order`, structured `:warnings`, and optional `:trace`. `:on-warning` receives
each warning as it occurs.

### `install!`

```clojure
(install! deps opts) ;=> result
```

Resolves and installs a deps.edn-style Maven, Git, and local dependency map.
Important options:

| Option | Meaning |
| --- | --- |
| `:host` | Required effect-function map unless all repository stages are replaced. |
| `:mediation` | `:newest`, `:nearest`, or `:tools-deps`. |
| `:repos` | Ordered remote repositories; defaults to Central and Clojars. |
| `:local-repo` | Maven repository path. |
| `:gitlibs-dir` | tools.gitlibs-compatible Git cache path. |
| `:base-dir` | Directory for relative local coordinates. |
| `:include-optional?` | Include optional transitive dependencies. |
| `:exclusions` | Global exclusions as symbols, strings, or coordinate maps. |
| `:source-roots?` | Extract installed JARs and return source roots. |
| `:source-libs` | Restrict extraction to a set of library symbols. |
| `:on-install` | Callback invoked after each newly installed artifact. |
| `:fetch-pom` | Coordinate-to-POM function replacing repository POM lookup. |
| `:pom-fn` | Coordinate-to-effective-POM function replacing model construction. |

The result contains:

| Key | Value |
| --- | --- |
| `:classpath` | Local JAR paths in lock order. |
| `:fetched` | Artifacts downloaded by this call. |
| `:cached` | Artifacts already present locally. |
| `:source-roots` | Extracted roots, or `nil` when extraction was not requested. |
| `:lock` | Enriched deterministic lock data. |
| `:resolution` | Full graph-resolution result. |
| `:basis` | Complete tools.deps-shaped basis. |
| `:warnings` | Resolution and repository warnings. |

Artifact or extraction failures throw `ExceptionInfo` with type
`:grenadine.core/install-failed` or `:grenadine.core/extraction-failed` and a
`:failed` vector in `ex-data`.

### `effective-pom`

```clojure
(effective-pom coords opts) ;=> effective-model
```

Builds one effective POM. `coords` is a map containing `:group`, `:artifact`,
and `:version`. Supply either `:fetch-pom` or repository options including a
host. The returned model contains `:coords`, `:packaging`, `:properties`,
`:dep-management`, and `:deps`.

### `resolve-graph`

```clojure
(resolve-graph deps opts) ;=> resolution
```

Resolves and mediates without installing. Supply `:pom-fn`, `:fetch-pom`, or a
repository-backed host. The result contains `:selected`, `:graph`, `:omitted`,
`:warnings`, and `:occurrences`.

## Model and lock operations

### `parse-pom`

```clojure
(parse-pom xml-text) ;=> raw-model
```

Parses Maven POM XML into Grenadine's canonical raw model without inheritance
or interpolation.

### `interpolate`

```clojure
(interpolate text properties) ;=> string
```

Interpolates Maven-style `${property}` references. Property cycles are
reported as structured exceptions.

### `emit-lock`

```clojure
(emit-lock resolution opts) ;=> lock
```

The low-level Maven graph form produces a stable version 1 lock for backward
compatibility. `calc-basis` and `install!` emit version 2 locks containing all
selected Maven, Git, and local libraries. `:repos` controls the repository
list, and `:integrity` may supply GAV-keyed SHA-256 and size values.

### `fetch-lock!`

```clojure
(fetch-lock! lock opts) ;=> fetch-result
```

Installs every lock artifact. Returns `:lock`, `:fetched`, `:cached`, `:failed`,
and `:warnings`; this lower-level function reports failures as data.

### `lock->classpath`

```clojure
(lock->classpath lock {:local-repo "/path/to/m2"
                       :gitlibs-dir "/path/to/gitlibs"}) ;=> [paths...]
```

Returns Maven artifact, Git checkout, and local coordinate paths without
touching the filesystem. Version 1 Maven locks remain supported.

### `prepare-source-roots!`

```clojure
(prepare-source-roots! lock opts) ;=> {:roots [...] :failed [...]}
```

Extracts installed JARs into digest-keyed directories. The host is responsible
for safe and atomic archive extraction.

## Custom host contract

A host is a map of effect functions. Repository-backed installation uses:

| Key | Contract |
| --- | --- |
| `:http-get` | URL to `{:status integer :headers map :body bytes}`. |
| `:read-bytes` | Path to bytes. |
| `:write-bytes!` | Path and bytes to a write effect. |
| `:bytes->utf8` | Bytes to text. |
| `:digest` | `:sha1` or `:sha256` and bytes to lowercase hexadecimal text. |
| `:byte-count` | Bytes to integer size. |
| `:exists?` | Path existence predicate. |
| `:mkdirs!` | Create a directory tree. |
| `:atomic-move!` | Move a completed temporary path into place. |
| `:delete!` | Remove a path. |
| `:home-dir` | Zero-argument home directory lookup. |
| `:getenv` | Environment lookup used for repository configuration. |
| `:extract-jar!` | Safely extract a JAR to a destination. Required only for source roots. |

Missing required functions produce `:grenadine.repo/incomplete-host` with the
missing key in `ex-data`.
