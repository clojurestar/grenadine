# Library guide

Grenadine separates pure Maven model and graph operations from filesystem,
network, digest, and archive effects. A host map supplies those effects, so
the same resolver can run across Clojure implementations.

## Resolution pipeline

The high-level `grenadine.core/install!` operation performs these steps:

1. Fetch and parse POMs, including parents and imported BOMs.
2. Interpolate Maven properties and apply dependency management.
3. Walk the transitive graph while honoring exclusions, scopes, and optional
   dependencies.
4. Mediate version conflicts.
5. Emit a deterministic lock and install its artifacts.
6. Optionally extract portable Clojure source roots from installed JARs.

The individual stages are also public through `effective-pom`,
`expand-deps`, `resolve-graph`, `emit-lock`, `fetch-lock!`, and
`prepare-source-roots!`.

## Portable dependency expansion

`expand-deps` is the coordinate-neutral tools.deps expansion engine shared by
Grenadine and Jolt. Callers supply functions that identify coordinates, load
their children, and compare versions, so Maven, Git, local, and in-memory test
coordinates use the same breadth-first traversal. It implements direct-root
precedence, newest transitive selection, path-scoped exclusions, exclusion
intersection, orphan cutting, overrides, defaults, stable inclusion order, and
optional trace output.

Grenadine's Maven graph uses this engine for `:tools-deps` mediation. The
`:newest` and `:nearest` modes retain their distinct selection policies over
Grenadine's path-aware Maven occurrence graph.

## Dependency coordinates

Grenadine accepts a deps.edn-style map. Version 0.1 supports Maven coordinates:

```clojure
'{org.clojure/data.csv
  {:mvn/version "1.1.0"}

  example/application
  {:mvn/version "2.0.0"
   :exclusions [example/unwanted]}}
```

Library names may be symbols or strings. An unqualified name uses the same
value for the Maven group and artifact. Coordinates without `:mvn/version`,
including Git and local coordinates, are rejected.

Compile and runtime dependencies are included. Test, provided, system, and
other non-runtime scopes are omitted. Optional transitive dependencies are
omitted unless `:include-optional? true` is passed.

## Effective POMs

Grenadine resolves parent POM inheritance, properties, dependency management,
and imported BOMs. Parent, BOM, and property cycles produce structured
exceptions rather than looping. Packaging defaults to `jar`; dependencies
whose effective packaging is `pom` remain part of resolution but do not emit
JAR artifacts.

## Mediation modes

Pass one of these values as `:mediation`:

| Mode | Selection rule |
| --- | --- |
| `:newest` | Select the highest Maven-compatible version. This is the core API default. |
| `:nearest` | Select the occurrence with the shortest path, then declaration order. |
| `:tools-deps` | Preserve a direct dependency; otherwise select the newest transitive version. This is the CLI and runtime-facade default. |

The resolution result includes selected coordinates, the active graph,
omitted occurrences, warnings, and active occurrences. Omitted reasons include
`:excluded`, `:scope`, `:optional`, `:cycle`, and `:version-conflict`.

## Repositories and the local cache

The default remote repositories are Maven Central followed by Clojars. Supply
`:repos` as ordered URL strings or maps with `:id` and `:url`:

```clojure
{:repos [{:id "central"
          :url "https://repo.maven.apache.org/maven2/"}
         {:id "company"
          :url "https://maven.example.com/releases/"}]}
```

The local repository is selected in this order:

1. `:local-repo` in the operation options;
2. `GRENADINE_LOCAL_REPOSITORY` from the host environment;
3. `$HOME/.m2/repository`.

POMs and artifacts already present locally are reused. If an artifact is
missing from its preferred remote, the remaining configured repositories are
tried in order.

## Integrity and atomic installation

A lock may supply SHA-256 and size metadata. Supplied SHA-256 values are
mandatory: a mismatch fails the artifact. Without SHA-256, Grenadine verifies
a remote SHA-1 sidecar when one exists. Downloads with no available checksum
are installed with an `:unverified-artifact` warning.

Downloads are written to a temporary `.grenadine.part` file and moved into
place after verification. Source extraction uses digest-keyed directories and
a completion marker so a host can perform safe, repeatable extraction.

## Locks and source roots

A version 1 lock is ordinary data:

```clojure
{:lock/version 1
 :repos ["https://repo.maven.apache.org/maven2/"]
 :artifacts
 [{:group "org.clojure"
   :artifact "data.csv"
   :version "1.1.0"
   :packaging "jar"
   :path "org/clojure/data.csv/1.1.0/data.csv-1.1.0.jar"
   :repo 0
   :sha256 "..."
   :size 12345}]}
```

Pass `:source-roots? true` to `install!` to extract installed JARs and return
`:source-roots`. Pass `:source-libs` as a set of library symbols to restrict
extraction while still resolving and installing the complete graph.
