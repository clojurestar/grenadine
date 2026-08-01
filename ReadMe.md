Grenadine
=========

Grenadine is a portable Maven dependency resolver in pure Clojure:
“pomegranate, with the JVM pressed out.”

The resolver core runs unchanged on JVM Clojure, Babashka, Glojure, Jolt, and
let-go. It parses and builds effective POMs, walks dependency graphs, supports
newest / Maven-nearest / tools.deps mediation, emits deterministic locks,
fetches artifacts, verifies checksums, and prepares extracted source roots for
non-JVM runtimes.


## Command line

The native `grenadine` command installs the Maven dependencies from a
`deps.edn` file without Java:

```sh
grenadine deps.edn
grenadine --repo=my-m2 deps.edn
grenadine --help
grenadine --version
```

The repository used by `--repo` takes precedence over `:mvn/local-repo` in the
deps file, `GRENADINE_LOCAL_REPOSITORY`, and the default
`$HOME/.m2/repository`, in that order.

On Bash and Zsh, the current release can also be downloaded, verified, and run
from a temporary cache without installing it on `PATH`:

```sh
$(source <(curl -s clojurestar.github.io/grenadine/get)) deps.edn
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

The Gloat-compiled command uses Glojure's native Grenadine effect host. JVM
Clojure and Babashka retain their existing hosts. Jolt and let-go already run
the complete pure core and have dependency facades; their native effect hosts
still need the remaining runtime primitives described in the plan.


## Copyright and License

Copyright 2026 - Ingy dot Net

MIT License - See [License](License) file.
