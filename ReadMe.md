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
grenadine -M newest --expand org.yamlscript/ys.v0
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
$(source <(curl -sL clojurestar.github.io/grenadine/get)) --add deps.edn
```

PowerShell users can run:

```powershell
& ([scriptblock]::Create((Invoke-RestMethod https://clojurestar.github.io/grenadine/get.ps1))) --add deps.edn
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

The Gloat-compiled command includes its Glojure effect host from this
repository and builds against released Glojure. JVM Clojure and Babashka
retain their existing hosts. Jolt and let-go already run the complete pure
core and have dependency facades; their native effect hosts still need the
remaining runtime primitives described in the plan.


## Copyright and License

Copyright 2026 - Ingy dot Net

MIT License - See [License](License) file.
