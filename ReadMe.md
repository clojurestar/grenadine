Grenadine
=========

Grenadine is a portable Maven dependency resolver in pure Clojure:
“pomegranate, with the JVM pressed out.”

See the [Grenadine website](https://clojurestar.github.io/grenadine/) for the
published documentation and install helpers.

The resolver core runs unchanged on JVM Clojure, Babashka, Glojure, Jolt, and
let-go. It parses and builds effective POMs, walks dependency graphs, supports
newest / Maven-nearest / tools.deps mediation, emits deterministic locks,
fetches artifacts, verifies checksums, and prepares extracted source roots for
non-JVM runtimes.


## Command line

The native `grenadine` command installs the Maven dependencies from a local or
remote `deps.edn` source without Java:

```sh
grenadine deps.edn
grenadine --repository=my-m2 deps.edn
grenadine --quiet deps.edn
grenadine --help
grenadine --version
```

HTTP and HTTPS URLs are accepted directly. GitHub `blob` links are
automatically fetched as raw content:

```sh
grenadine --repository=my-m2 \
  https://github.com/yaml/yamlscript/blob/main/core/deps.edn
```

By default, Grenadine prints each dependency immediately after installing it,
then reports installed, already-present, and total counts. Already-present
dependencies are not listed individually. Use `-q` or `--quiet` to suppress
non-error output. The repository used by `-R` or `--repository` takes
precedence over `:mvn/local-repo` in the deps source,
`GRENADINE_LOCAL_REPOSITORY`, and the default
`$HOME/.m2/repository`, in that order.

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


## Clojars

Grenadine releases are published as `cc.clojure/grenadine` for use as a
library:

```clojure
{:deps {cc.clojure/grenadine {:mvn/version "0.1.1"}}}
```

Release credentials are read from `~/.publish-secrets` by default, using the
`.clojure.user` and `.clojure.token` YAML paths:

```yaml
clojure:
  user: ingy
  token: CLOJARS_DEPLOY_TOKEN
```

`PUBLISH_SECRETS` can name a different file. If complete credentials cannot be
read from the YAML file, Grenadine falls back to the existing
`CLOJARS_USERNAME` and `CLOJARS_PASSWORD` environment variables.

From a clean `main` branch synchronized with `origin/main`, run:

```sh
make release VERSION=0.1.1
```

The command updates `VERSION`, `project.clj`, and `Changes`; commits those
three files as `Release 0.1.1`; validates the library JAR and native archives;
creates the tag; deploys to Clojars; atomically pushes `main` and the tag;
publishes the GitHub release; and deploys the website. A successful Clojars
deployment is recorded locally so a later failure can safely resume with the
same command.

If Clojars reports an ambiguous failure, first verify that the version was
published. Then record it without redeploying and resume the release:

```sh
make release-mark-deployed VERSION=0.1.1 DEPLOYED=1
make release VERSION=0.1.1
```


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
