Grenadine
=========

Grenadine is a portable Maven dependency resolver library in pure Clojure:
“pomegranate, with the JVM pressed out.”

See the [Grenadine website](https://clojurestar.github.io/grenadine/) for the
published documentation and install helpers.

The resolver core runs unchanged on JVM Clojure, Babashka, Glojure, Jolt, and
let-go. It parses and builds effective POMs, walks dependency graphs, supports
newest / Maven-nearest / tools.deps mediation, emits deterministic locks,
fetches artifacts, verifies checksums, and prepares extracted source roots for
non-JVM runtimes.


## Installation

Add the following dependency to your deps.edn file:

```clojure
{:deps {cc.clojure/grenadine {:mvn/version "0.1.2"}}}
```

Or to your Leiningen project file:

```clojure
[cc.clojure/grenadine "0.1.2"]
```


## Command line

The native `grenadine` command installs the Maven dependencies from a local or
remote `deps.edn` source without Java:

```sh
grenadine deps.edn
grenadine --repository=my-m2 deps.edn
grenadine --quiet deps.edn
grenadine --list
grenadine --repository=my-m2 --list
grenadine --add nrepl/bencode 1.1.0 clj-commons/clj-yaml
grenadine --remove nrepl/bencode 1.1.0 clj-commons/clj-yaml
grenadine --resolve org.yamlscript/ys.v0
grenadine --resolver=newest --resolve org.yamlscript/ys.v0
grenadine --resolvers
grenadine --help
grenadine --version
```

HTTP and HTTPS URLs are accepted directly. GitHub `blob` links are
automatically fetched as raw content:

```sh
grenadine --repository=my-m2 \
  https://github.com/seancorfield/honeysql/blob/develop/deps.edn
```

By default, Grenadine prints each dependency immediately after installing it,
then reports installed, already-present, and total counts. Already-present
dependencies are not listed individually. Use `-q` or `--quiet` to suppress
non-error output. The repository used by `-R` or `--repository` takes
precedence over `:mvn/local-repo` in the deps source,
`GRENADINE_LOCAL_REPOSITORY`, and the default
`$HOME/.m2/repository`, in that order.

`--add` accepts one or more `group/artifact` names, each optionally followed
by a version. When a version is omitted, Grenadine selects the Maven metadata
release (or latest non-SNAPSHOT version) and installs its transitive
dependencies. `--remove` removes only the requested library version; omit the
version to remove every locally installed version of that library. It does not
garbage-collect transitive dependencies.

`--resolve` performs the same transitive graph resolution without installing
JARs. It caches required POMs and prints the selected `group/artifact VERSION`
coordinates in sorted order. Use `--resolver` with deps-source installation,
`--add`, or `--resolve` to select `newest`, `nearest`, or `tools-deps`
mediation. The default is `tools-deps`; `--resolvers` describes all three.

On Bash and Zsh, the current release can also be downloaded, verified, and run
from a temporary cache without installing it on `PATH`:

```sh
$(source <(curl -sL clojurestar.github.io/grenadine/get)) deps.edn
```

PowerShell users can run:

```powershell
& ([scriptblock]::Create((Invoke-RestMethod https://clojurestar.github.io/grenadine/get.ps1))) deps.edn
```

Release binaries cover Linux on amd64, arm64, and armv6; macOS and Windows on
amd64 and arm64; and FreeBSD, OpenBSD, and NetBSD on amd64 and arm64.


## Configuration

Grenadine uses the standard Maven local repository at
`$HOME/.m2/repository`. Set `GRENADINE_LOCAL_REPOSITORY` to use another
repository:

```sh
export GRENADINE_LOCAL_REPOSITORY=/path/to/maven/repository
```

An explicit `:local-repo` option takes precedence over the environment.
Artifacts are tried against each configured remote repository in order when
the lock's preferred repository does not contain them. Non-JVM hosts can pass
`:source-libs` as a set of library symbols to extract and expose only selected
source roots while still installing the full dependency graph.


## Development

```sh
make build
make test
make test-all
make oracle
```

`test-all` runs the same portable suite on all five runtimes. `oracle` compares
Grenadine with JVM tools.deps and Maven `ComparableVersion`.

The Gloat-compiled command includes its Glojure effect host from this
repository and builds against released Glojure. JVM Clojure and Babashka
retain their existing hosts. Jolt and let-go already run the complete pure
core and have dependency facades; their native effect hosts still need the
remaining runtime primitives described in the plan.


## Copyright and License

Copyright 2026 - Ingy dot Net

MIT License - See [License](License) file.
