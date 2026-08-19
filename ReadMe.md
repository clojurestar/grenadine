Grenadine
=========

Grenadine is a portable dependency resolver library in pure Clojure:
“pomegranate, with the JVM pressed out.”

See the [Grenadine website](https://clojurestar.github.io/grenadine/) for the
published documentation and install helpers.

The resolver core is portable source used by Glojure and Jolt and tested under
Gobb.
It resolves Maven, Git, and local coordinates, builds tools.deps-shaped bases,
supports newest / Maven-nearest / tools.deps mediation, emits deterministic
locks, and prepares source roots for non-JVM runtimes.


## Source artifact

Grenadine publishes a vendorable source JAR to Clojars as:

```text
cc.clojure/grenadine:0.1.9
```

The artifact does not declare a Clojure runtime dependency.
Dialect and tool authors can vendor its portable sources and provide the host
effects required by their runtime.


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

`clojurestar.deps/add-deps` always returns `nil`.
Use `glojure.deps` or `jolt.deps` when code needs backend-specific options,
operations, or result data.

Use `require-deps` when a script should acquire a dependency and immediately
import its namespace:

```clojure
(require '[clojurestar.deps :refer [require-deps]])

(require-deps
 ["mvn:dev.weavejester/medley@1.10.0/medley.core" :as medley])

(medley/index-by :id [{:id 1} {:id 2}])
```

Literal libspec vectors do not need quoting; quoted vectors remain compatible.
Libspecs support `:as` and explicit `:refer [...]`. Maven, Gist, and GitHub
source-file coordinates are supported. A pinned Gist file can be written as either
`gist:<owner>/<id>/<file>@<revision>` or
`gist:<owner>/<id>/<revision>/<file>`.

A single GitHub source file uses
`github:<owner>/<repo>/<ref>/<path.clj|cljc>`. The GitHub URL-style
`github:<owner>/<repo>/blob/<ref>/<path.clj|cljc>` form is equivalent. For
example:

```clojure
(require-deps
 ["github:weavejester/medley/1.7.0/src/medley/core.cljc" :as medley])
```

GitHub refs are one path segment. Full 40-character commit SHAs reuse the
persistent cache; named refs are refreshed when loaded in a new process.

An optional leading map accepts `:mvn/local-repo` and `:gitlibs/dir`.
`:cache-dir` remains a compatibility alias for the source-file cache root.
Gist and GitHub source are stored beneath `gist/` and `github/` in the same
effective Gitlibs directory used for Git dependencies. Selected source files
must be self-contained and begin with an `ns` form.

Non-JVM runtime facades treat `org.clojure/clojure` and
`org.clojure/clojurescript` as terminal host-provided libraries: their
artifacts and transitive trees are not acquired. Directly declared libraries,
including `org.clojure/spec.alpha` and `org.clojure/core.specs.alpha`, remain
ordinary dependencies.


## Command line

The native `grenadine` command resolves and installs dependencies from a local
or remote `deps.edn` source without Java:

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

HTTP and HTTPS URLs are accepted directly.
GitHub `blob` links are automatically fetched as raw content:

```sh
grenadine --repository=my-m2 --add \
  https://github.com/seancorfield/honeysql/blob/develop/deps.edn
```

By default, Grenadine prints each dependency immediately after installing it,
then reports installed, already-present, and total counts.
Already-present dependencies are not listed individually.
Use `-q` or `--quiet` to suppress non-error output.
The repository used by `-R` or `--repository` takes precedence over
`:mvn/local-repo` in the deps source, `GRENADINE_MAVEN_REPOSITORY`, and the
default `$HOME/.m2/repository`, in that order.

Deps sources may contain Maven, Git, and local coordinates.
Git coordinates and Gist source use a tools.gitlibs-compatible cache selected
by `-G/--gitlibs`, top-level `:gitlibs/dir`, `GRENADINE_GITLIBS_DIR`,
`GITLIBS`, or `$HOME/.gitlibs`, in that order.
Git is only required when a Git coordinate is encountered.
Unqualified Maven names mean `name/name`, classifiers use
`group/artifact$classifier`, and Maven version ranges are resolved to concrete
versions before expansion.
Relative `:local/root` values require a local deps source; an HTTP source must
use an absolute root.

`--add`, `--list`, `--delete`, `--remove`, and `--expand` accept mixed lists of
`group/artifact [version]` requests and local or remote deps sources.
`--delete` removes only explicit coordinates; `--remove` expands and removes
complete dependency closures.
`--expand` prints the selected graph without installing JARs.
Use `-M` or `--mediator` to select `newest`, `nearest`, or `tools-deps`.
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
`/usr/local/bin` when run as root.
Set `PREFIX` to choose another location:

```sh
make install PREFIX=/opt/grenadine
```


## Configuration

Grenadine uses the standard Maven local repository at `$HOME/.m2/repository`.
Set `GRENADINE_MAVEN_REPOSITORY` to use another repository:

```sh
export GRENADINE_MAVEN_REPOSITORY=/path/to/maven/repository
```

An explicit `:local-repo` option takes precedence over the environment.
Artifacts are tried against each configured remote repository in order when the
lock's preferred repository does not contain them.
Non-JVM hosts can pass `:source-libs` as a set of library symbols to extract and
expose only selected source roots while still installing the full dependency
graph.


## Development

```sh
make src
make build
make test
make test-all
make test-ecosystem
make oracle
```

`make src` downloads the revisions pinned in `patch/sources.yaml`, verifies
their source checksums, applies the unified patches in `patch/`, and assembles
the complete portable tree under `src/`.
Upstream-backed generated files are not committed.
After editing one of them, run `make patch` to regenerate the reviewable project
patches; `make src-check` verifies an exact round trip.

`test-all` runs the portable suite on Glojure and Jolt.
`test-ecosystem` also builds the adjacent Gobb checkout and runs the portable
suite through it; set `GOBB_SOURCE_DIR` when that checkout is elsewhere.
`oracle` compares Grenadine bases with JVM tools.deps and version ordering with
Maven `ComparableVersion`.
Its deterministic generated cases are configured with `GRENADINE_ORACLE_SEED`
and `GRENADINE_ORACLE_CASES`.

`make release VERSION=X.Y.Z` publishes the Clojars artifact, native archives,
website, and Homebrew formulas.
The release gate uses the pinned Jolt release rather than an adjacent checkout.
Local `main` may be ahead of `origin/main`; after validation and deployment, the
workflow pushes the release commit and tag to `origin` atomically.
To publish or retry only the Homebrew tap after the GitHub release assets exist:

```sh
make release-homebrew VERSION=X.Y.Z
```

The updater pushes to `clojurestar/homebrew-grenadine` by default.
Set `GRENADINE_HOMEBREW_URL` to use another tap remote, or set
`GRENADINE_HOMEBREW_PUSH=0` to prepare and inspect the tap checkout without
pushing it.

The Gloat-compiled command includes its Glojure effect host from this
repository and builds against released Glojure.
Glojure embeds the Grenadine-backed `glojure.deps` facade and supplies its
native host from the Glojure binary.
Jolt owns its `jolt.deps` implementation and vendors the Grenadine sources it
uses into the Jolt binary.
Gobb owns its separate `gobb.deps` implementation and `clojurestar.deps`
facade. Grenadine's portable facade selects that backend under the `:gobb`
reader feature, but Gobb does not load a Grenadine runtime adapter from this
repository.


## Acknowledgements and Provenance

Grenadine's portable dependency expansion, coordinate handling, basis
construction, and Git cache are generated from pinned source files in the
Clojure `tools.deps`, `tools.deps.edn`, and `tools.gitlibs` projects plus
reviewable portability patches.
We gratefully credit the Clojure team and contributors who created and maintain
them, particularly Alex Miller, and the original Clojure copyright holder Rich
Hickey.
Grenadine contributor Yogthos improved portable incomparable-version warning
handling.

Portable Maven version ordering and range parsing adapt Apache Maven code.
[Provenance.md](Provenance.md) records the exact upstream revisions, source file
mappings, licenses, and Grenadine-specific changes.
Each release includes a complete generated source archive; the matching Git tag
contains everything needed to reproduce it with `make src`.


## Copyright and License

Copyright 2026 - Ingy dot Net

Eclipse Public License 1.0 - See the [License](License) file.
Apache-derived portions retain their Apache License 2.0 notices; see
[ThirdPartyNotices.md](ThirdPartyNotices.md).
