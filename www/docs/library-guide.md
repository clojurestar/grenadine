# Library guide

Grenadine separates dependency model and graph operations from filesystem,
network, digest, and archive effects. A host map supplies those effects, so
the same resolver can run across Clojure implementations.

## Resolution pipeline

The high-level `grenadine.core/install!` operation performs these steps:

1. Canonicalize Maven, Git, and local coordinates.
2. Fetch POMs and Git checkouts and read dependency manifests.
3. Walk the transitive graph while honoring exclusions, scopes, and optional
   dependencies.
4. Mediate version conflicts.
5. Build a tools.deps-shaped basis and deterministic version 2 lock.
6. Optionally extract portable Clojure source roots from installed JARs.

The individual stages are also public through `calc-basis`, `effective-pom`,
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

Grenadine accepts Maven, Git, and local coordinates in a deps.edn-style map:

```clojure
'{org.clojure/data.csv
  {:mvn/version "1.1.0"}

  example/application
  {:mvn/version "2.0.0"
   :exclusions [example/unwanted]}

  io.github.example/tool
  {:git/url "https://github.com/example/tool.git"
   :git/sha "0123456789abcdef0123456789abcdef01234567"}

  example/local
  {:local/root "../local-lib"}}
```

Library names may be symbols or strings. An unqualified name uses the same
value for the Maven group and artifact. Maven classifiers use tools.deps
`group/artifact$classifier` syntax. Maven version ranges such as `[1.0,2.0)`
are resolved from configured repository metadata before graph expansion and
locks contain the selected concrete version. Legacy Git `:sha` and `:tag` keys
are accepted. A tag may be paired with an abbreviated SHA; the resolved commit
must match that prefix. Common GitHub, GitLab, Bitbucket, Codeberg, Beanstalk,
and SourceHut URLs can be inferred from reverse-domain library names.

Relative local roots are resolved from a filesystem-backed deps file that
declares them and then canonicalized. Grenadine warns and skips relative local
roots in remote HTTP deps sources because it does not check out the source
repository to materialize adjacent directories. Other dependencies in the
source continue to resolve. Git and local directories must contain
`deps.edn` or `pom.xml`; `deps.edn` wins when both exist. Use
`:deps/manifest :deps` or `:deps/manifest :pom` to choose explicitly.
`:deps/root` selects a safe nested project root. Local JAR coordinates read
embedded Maven metadata when present.

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
2. `GRENADINE_MAVEN_REPOSITORY` from the host environment;
3. `$HOME/.m2/repository`.

POMs and artifacts already present locally are reused. If an artifact is
missing from its preferred remote, the remaining configured repositories are
tried in order.

Git checkouts use the tools.gitlibs layout: mirrors live under `_repos` and
detached worktrees under `libs/<namespace>/<name>/<full-sha>`. Gist source
loaded by `require-deps` lives under `gist/<owner>/<id>/<revision>/`. Their
shared cache root is selected in this order:

1. `:gitlibs-dir` in operation options or `-G/--gitlibs` in the CLI;
2. top-level `:gitlibs/dir` in a deps source;
3. `GRENADINE_GITLIBS_DIR`;
4. `GITLIBS`;
5. `$HOME/.gitlibs`.

For `require-deps`, `:gitlibs/dir` is the primary explicit option and
`:cache-dir` is retained as a compatibility alias.

Set `GITLIBS_COMMAND` to select another Git executable. Git is invoked with an
argument vector, never through a shell, and is only required when a Git
coordinate is resolved.

## Integrity and atomic installation

A lock may supply SHA-256 and size metadata. Supplied SHA-256 values are
mandatory: a mismatch fails the artifact. Without SHA-256, Grenadine verifies
a remote SHA-1 sidecar when one exists. Downloads with no available checksum
are installed with an `:unverified-artifact` warning.

Downloads are written to a temporary `.grenadine.part` file and moved into
place after verification. Source extraction uses digest-keyed directories and
a completion marker so a host can perform safe, repeatable extraction.

## Bases, locks, and source roots

`calc-basis` returns the familiar tools.deps keys `:libs`, `:classpath`, and
`:classpath-roots`, plus namespaced Grenadine procurement and lock details.
The oracle compares the complete public basis shape with JVM tools.deps for a
curated corpus and deterministic generated Maven graphs.

A new resolution emits a version 2 lock. Each selected library records its
canonical coordinate, manifest, and effective relative classpath entries;
Git SHAs are full commits and local roots are absolute. Maven artifacts and
repository integrity remain in `:artifacts` and `:repos`:

```clojure
{:lock/version 2
 :repos ["https://repo.maven.apache.org/maven2/"]
 :libs [{:lib org.clojure/data.csv
         :coord {:mvn/version "1.1.0"}
         :deps/manifest :mvn
         :classpath [{:type :mvn
                      :path "org/clojure/data.csv/1.1.0/data.csv-1.1.0.jar"}]}]
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

Version 1 Maven-only locks remain readable and fetchable.

Pass `:source-roots? true` to `install!` to extract installed JARs and return
`:source-roots`. Pass `:source-libs` as a set of library symbols to restrict
extraction while still resolving and installing the complete graph.
