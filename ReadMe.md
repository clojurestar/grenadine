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
{:deps {cc.clojure/grenadine {:mvn/version "0.1.3"}}}
```

Or to your Leiningen project file:

```clojure
[cc.clojure/grenadine "0.1.3"]
```


## Portable dynamic dependencies

Grenadine defines the ClojureStar dependency facade for code that should run
unchanged across Clojure dialects:

```clojure
(require '[clojurestar.deps :as deps])

(deps/add-deps
 '{:deps
   {dev.weavejester/medley {:mvn/version "1.10.0"}}})

(require '[medley.core :as medley])
```

`clojurestar.deps/add-deps` always returns `nil`. Use `babashka.deps`,
`glojure.deps`, `jolt.deps`, `let-go.deps`, or `grenadine.jvm` when code needs
backend-specific options, operations, or result data.


## Command line

The native `grenadine` command installs the Maven dependencies from a local or
remote `deps.edn` source without Java:

```sh
grenadine --add deps.edn
grenadine --repository=my-m2 --add deps.edn
grenadine --quiet --add deps.edn
grenadine --list
grenadine --list deps.edn
grenadine --repository=my-m2 --list
grenadine --add nrepl/bencode 1.1.0 clj-commons/clj-yaml
grenadine --delete nrepl/bencode 1.1.0
grenadine --remove clj-commons/clj-yaml
grenadine --expand org.yamlscript/ys.v0
grenadine -M newest -X org.yamlscript/ys.v0
grenadine --mediators
grenadine --help
grenadine --version
```

HTTP and HTTPS URLs are accepted directly. GitHub `blob` links are
automatically fetched as raw content:

```sh
grenadine --repository=my-m2 --add \
  https://github.com/seancorfield/honeysql/blob/develop/deps.edn
```

By default, Grenadine prints each dependency immediately after installing it,
then reports installed, already-present, and total counts. Already-present
dependencies are not listed individually. Use `-q` or `--quiet` to suppress
non-error output. The repository used by `-R` or `--repository` takes
precedence over `:mvn/local-repo` in the deps source,
`GRENADINE_LOCAL_REPOSITORY`, and the default
`$HOME/.m2/repository`, in that order.

`--add`, `--list`, `--delete`, `--remove`, and `--expand` accept mixed lists of
`group/artifact [version]` requests and local or remote deps sources. `--delete`
removes only explicit coordinates; `--remove` expands and removes complete
dependency closures. `--expand` prints the selected graph without installing
JARs. Use `-M` or `--mediator` to select `newest`, `nearest`, or `tools-deps`.
The default is `tools-deps`; `--mediators` describes all three.

On Linux and macOS, install with Homebrew:

```sh
brew install clojurestar/grenadine/grenadine
```

On Bash and Zsh, install the latest release under `$HOME/.local/bin` with:

```sh
source <(curl -sL clojurestar.github.io/grenadine/install)
```

Set `PREFIX` to choose another installation prefix:

```sh
PREFIX=/opt/grenadine \
  source <(curl -sL clojurestar.github.io/grenadine/install)
```

On Bash and Zsh, the current release can also be downloaded, verified, and run
from a temporary cache without installing it on `PATH`:

```sh
$(source <(curl -sL clojurestar.github.io/grenadine/get)) \
  -X https://github.com/yaml/yamlscript/blob/main/core/deps.edn
```

PowerShell users can run:

```powershell
& ([scriptblock]::Create((Invoke-RestMethod https://clojurestar.github.io/grenadine/get.ps1))) `
  -X https://github.com/yaml/yamlscript/blob/main/core/deps.edn
```

Release binaries cover Linux on amd64, arm64, and armv6; macOS and Windows on
amd64 and arm64; and FreeBSD, OpenBSD, and NetBSD on amd64 and arm64.

To build and install the command from a source checkout:

```sh
make install
```

This installs `grenadine` under `$HOME/.local/bin` for a regular user or
`/usr/local/bin` when run as root. Set `PREFIX` to choose another location:

```sh
make install PREFIX=/opt/grenadine
```


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

`make release VERSION=X.Y.Z` publishes the Clojars artifact, native archives,
website, and Homebrew formulas. To publish or retry only the Homebrew tap after
the GitHub release assets exist:

```sh
make release-homebrew VERSION=X.Y.Z
```

The updater pushes to `clojurestar/homebrew-grenadine` by default. Set
`GRENADINE_HOMEBREW_URL` to use another tap remote, or set
`GRENADINE_HOMEBREW_PUSH=0` to prepare and inspect the tap checkout without
pushing it.

The Gloat-compiled command includes its Glojure effect host from this
repository and builds against released Glojure. JVM Clojure, Babashka,
Glojure, Jolt, and let-go each expose a dynamic dependency backend; the
`clojurestar.deps` namespace is the intentionally small common API over them.


## Copyright and License

Copyright 2026 - Ingy dot Net

MIT License - See [License](License) file.
