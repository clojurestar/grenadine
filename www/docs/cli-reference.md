# CLI reference

```text
Usage: grenadine
       grenadine [OPTIONS] --list [ITEM...]
       grenadine [OPTIONS] --current [ITEM...]
       grenadine [OPTIONS] [-M MODE] [--unzip] --install ITEM...
       grenadine [OPTIONS] [-M MODE] --loadpath ITEM...
       grenadine [OPTIONS] --delete ITEM...
       grenadine [OPTIONS] [-M MODE] --remove ITEM...
       grenadine [OPTIONS] [-M MODE] --expand ITEM...
       grenadine --mediators
       grenadine --help
       grenadine --version
```

An `ITEM` is either `NAME [VERSION]` or a local/remote dependency source. A
dependency source is a deps.edn-style map or a literal Leiningen
`project.clj`. Operations accept mixed item lists. Bare `grenadine` prints the
same help as `--help`; operands without an explicit operation are rejected.

`--list` and `--current` also accept one Maven repository directory in place
of an item list.

## Options

| Short | Long | Behavior |
| --- | --- | --- |
| `-R DIR` | `--repository DIR` | Use this local Maven repository. |
| `-G DIR` | `--gitlibs DIR` | Use this tools.gitlibs-compatible Git cache. |
| `-M MODE` | `--mediator MODE` | Use `newest`, `nearest`, or `tools-deps`. |
| | `--list` | List the repository or report an expanded graph's local status. |
| | `--current` | List installed or selected dependencies and available updates. |
| | `--install` | Expand and install all selected dependencies. |
| | `--unzip` | Also extract Maven JARs installed by `--install`. |
| | `--loadpath` | Install and print a ClojureCLR load path. |
| | `--delete` | Delete only explicitly requested coordinates. |
| | `--remove` | Expand inputs and delete their complete dependency closures. |
| `-X` | `--expand` | Print a dependency tree without installing JARs. |
| | `--mediators` | Describe the available mediation strategies. |
| `-q` | `--quiet` | Suppress non-error operational output. |
| `-h` | `--help` | Print usage. |
| | `--version` | Print `grenadine vVERSION`. |

Exactly one operation is accepted. `--repository=DIR` and
`--mediator=MODE` are equivalent to their separated forms.
`--unzip` is valid only with `--install`.
`--loadpath` is an operation that performs installation and extraction itself.
It cannot be combined with `--quiet` because its standard output is the
requested load path.
The former `--add` spelling remains a deprecated alias for `--install`; it is
omitted from help and emits a warning whenever it is used.

## Mixed inputs

```sh
grenadine --install \
  nrepl/bencode 1.1.0 \
  deps.edn \
  project.clj \
  clj-commons/clj-yaml \
  https://example.org/other-deps.edn
```

HTTP/HTTPS URLs, existing files, `.edn` paths, and `project.clj` paths are
recognized as sources. Each qualified library name may be followed by a
version; omitted versions select the latest Maven release. Source roots and
named roots are combined in operand order, with a later declaration replacing
an earlier declaration of the same library.

Remote repository maps are also merged in operand order. The last source-level
`:mvn/local-repo` wins, while `-R/--repository` overrides every source.
The last top-level `:gitlibs/dir` wins, while `-G/--gitlibs` overrides every
source.

## List

With no items, `--list` prints every conventional main-artifact JAR in the
local repository, sorted by coordinate. It does not contact remotes or print a
summary.

```sh
grenadine --list
grenadine -R my-m2 --list
grenadine --list ~/.m2/repository
```

A repository directory operand is equivalent to selecting the inventory root
directly. It must be the only item and cannot be combined with
`-R/--repository`.

With items, `--list` composes and expands them like `--expand`, then checks
whether each selected Maven JAR, Git checkout, or local path exists:

```sh
grenadine -M nearest --list deps.edn org.example/library 2.0.0
```

```text
demo/branch  1.0.0
demo/core    2.0.0  MISSING
demo/root    1.0.0
=> Installed: 2  Missing: 1  Total: 3
```

POMs may be cached during expansion, but cached metadata does not count as an
installed JAR.

Inventory tables use two spaces between aligned columns.

## Current versions

With no items or with one Maven repository directory, `--current` inventories
the selected local repository like `--list`, omitting `org.clojure/clojure`.
With dependency items, it expands and mediates them like `--expand` and uses
the same tree display:

```sh
grenadine --current
grenadine --current ~/.m2/repository
grenadine --current deps.edn org.example/library 2.0.0
grenadine -M nearest --current deps.edn
```

For each visible Maven library, Grenadine checks the configured remote
repositories and shows an update only when a newer Maven-compatible version
is available.
Trees append `-> VERSION`; inventory tables use a third column and preserve
all installed versions.
Dependency sources can supply custom `:mvn/repos`; inventory mode uses Central
followed by Clojars.
Git and local coordinates are printed but do not have update checks.

With dependency inputs:

```text
└── demo/root 1.0.0 -> 1.1.0
    ├── demo/branch 1.0.0
    │   └── demo/c 2.0.0 -> 2.1.0
    └── demo/c 2.0.0 -> 2.1.0 (already shown)
```

Trees share `--expand`'s sorting, exclusion, repeated-library, and cycle rules.
Version values are cyan on terminal output, including available updates.
Tree connectors, update arrows, and the `(already shown)` marker are comment gray.
Redirected output stays plain; set `NO_COLOR` to disable coloring.
The `org.clojure/clojure` branch and dependencies reachable only through it
are hidden and receive no update checks.

Without dependency inputs:

```text
org.clojure/core.specs.alpha    0.4.74
org.clojure/math.combinatorics  0.3.0    0.3.1
org.clojure/spec.alpha          0.5.238
org.clojure/tools.reader        1.6.0    1.7.2
rewrite-clj/rewrite-clj         1.2.55
```

Failed metadata lookups leave the output row unchanged, emit one warning per
library, and do not make the command fail. Repositories are only read;
`--current` does not install or modify dependencies.

## Install and expand

```sh
grenadine --install deps.edn org.example/library
grenadine -M newest --expand deps.edn org.example/library 2.0.0
```

Both operations combine all inputs and mediate once. `--install` installs the
selected Maven artifacts and Git checkouts, validates local paths, and prints
streamed installation lines plus its summary.
`--expand` prints a dependency tree and installs no Maven JARs.
Both may cache POM metadata and procure
Git checkouts required to read manifests.

The tree starts with the direct dependencies, with roots and siblings sorted
by library name:

```text
└── demo/root 1.0.0
    ├── demo/branch 1.0.0
    │   └── demo/c 2.0.0
    └── demo/c 2.0.0 (already shown)
```

Each line shows the selected version, Git tag/SHA, or local path.
The `org.clojure/clojure` branch is omitted from the display, including its
dependencies unless they are also reachable through another displayed branch.
This does not change dependency resolution or installation.
A shared library's dependencies are expanded once; later occurrences are marked
`(already shown)`.
Cycles are marked `(cycle)` and are not expanded further.
For a shared library, the children combine dependencies allowed by any reachable
path, including when different paths have different exclusions.
Empty dependency trees produce no output.

## ClojureCLR load paths

`--loadpath` is the complete installation operation for ClojureCLR.
It resolves the input graph, installs missing dependencies, extracts Maven
JARs, and prints one path-separated `CLOJURE_LOAD_PATH` value:

```sh
in-1 grenadine cljr
CLOJURE_LOAD_PATH=$(grenadine --loadpath deps.edn) cljr script.clj
```

Installation progress and summaries are suppressed so command substitution
receives only the load path.
Warnings remain on standard error.
The path separator is selected for the current operating system and every
entry is absolute.

Maven JAR entries are replaced in place by Grenadine's safe, digest-keyed
extraction directories.
Git and local dependency directories remain unchanged.
`org.clojure/clojure`, `org.clojure/spec.alpha`, and
`org.clojure/core.specs.alpha` are treated as supplied by ClojureCLR, so their
artifacts and transitive graphs are not installed or included.

Local deps sources contribute their top-level `:paths`.
Literal `project.clj` sources contribute `:source-paths` and
`:resource-paths`, defaulting to `src` and `resources` when those keys are
absent.
Relative project paths are resolved from the source file's directory.
Relative paths in remote sources are skipped with a warning because the
remote project has not been checked out; absolute paths remain usable.
Project and dependency roots retain classpath order, with duplicates removed
after their first occurrence.

Use `--install --unzip` when extraction is wanted without load-path output:

```sh
grenadine --install --unzip deps.edn
```

This keeps the ordinary install progress and summary unchanged.

## Delete exact coordinates

```sh
grenadine --delete org.example/library 1.2.3
grenadine --delete org.example/library
grenadine --delete deps.edn additional-deps.edn
```

A versioned name deletes that version. An unversioned name deletes all locally
installed versions. A dependency source contributes only its direct
dependencies; it is not expanded. Multiple distinct explicit versions are
accepted, but an all-version request cannot be combined with version-specific
requests for the same library.

```text
Deleted org.example/library 1.2.3
=> Deleted: 1  Missing: 0  Total: 1
```

## Remove expanded closures

```sh
grenadine --remove org.example/library 1.2.3
grenadine --remove org.example/library
grenadine -M tools-deps --remove deps.edn org.example/other
```

`--remove` expands versioned roots and deletes every selected coordinate,
including transitives. An unversioned name expands every installed version and
removes the union of those closures. Shared transitives are not protected.

Removal never contacts remote repositories for POM metadata. When a local POM
is unavailable, Grenadine removes that coordinate but warns that its unknown
children could not be included. All inputs and graphs are prepared before the
first deletion.

```text
Removed org.example/library 1.2.3
=> Removed: 1  Missing: 0  Total: 1
```

## Mediation

The default is `tools-deps`. List all strategies with:

```text
$ grenadine --mediators
newest     Select the highest Maven-compatible version
nearest    Select the shortest path, then declaration order
tools-deps Preserve direct dependencies; otherwise select newest (default)
```

`-M/--mediator` is valid with `--install`, `--loadpath`, `--expand`,
`--remove`, and with `--list` or `--current` when dependency items are
supplied. It is rejected with repository inventory, `--delete`, and
`--mediators`. The `--mediators` operation does not accept other options.

## deps.edn input format

```clojure
{:paths ["src" "resources"]
 :mvn/local-repo "/optional/local/repository"
 :mvn/repos
 {"company" {:url "https://maven.example.com/releases/"}}
 :deps
 {org.clojure/data.csv {:mvn/version "1.1.0"}}}
```

The source must contain an EDN map. Missing `:deps` means an empty dependency
set. Top-level `:paths` are used by `--loadpath`.
Coordinates may use `:mvn/version`, `:git/url` with `:git/sha`, or
`:local/root`. Git and local directory coordinates require `deps.edn` or
`pom.xml`; `:deps/root` and `:deps/manifest` refine where and how that manifest
is read. Relative `:local/root` values require a local deps source; Grenadine
warns and skips them in remote HTTP sources while continuing with the other
dependencies. Unqualified Maven names mean `name/name`,
classifiers use `group/artifact$classifier`, and Maven version ranges are
resolved to concrete versions before expansion. Direct Git/local map syntax is
intentionally not parsed as CLI items; put those coordinates in a local or
remote deps source.

## Literal project.clj input

The CLI also accepts a local or remote `project.clj` whose leading form is a
literal `defproject`:

```clojure
(defproject example/application "1.2.3"
  :local-repo "/optional/local/repository"
  :source-paths ["src"]
  :resource-paths ["resources"]
  :repositories
  [["company" {:url "https://maven.example.com/releases/"}]]
  :exclusions [example/unwanted]
  :dependencies
  [[org.clojure/data.csv "1.1.0"]
   [example/tool "2.0.0"
    :classifier "tests"
    :exclusions [example/legacy]]])
```

Grenadine reads the project name, ignores the project version form, and reads
only `:dependencies`, `:repositories`, `:local-repo`, and project-level
`:exclusions` for dependency operations.
For `--loadpath`, it also reads literal `:source-paths` and `:resource-paths`.
The version form is never evaluated, so projects that compute their own
version can still be expanded. Dependency IDs may be symbols or strings.
Dependency options support `:classifier`, `:exclusions`,
`:extension "jar"`, `:scope "compile"` or `"runtime"`, `:optional`, and
`:native-prefix`; the last two do not change dependency selection.

Project files are parsed as data with reader evaluation disabled. Grenadine
does not load Leiningen, execute project code, merge profiles or user
configuration, resolve `:managed-dependencies`, run plugins, or use repository
credentials and policies. Dynamic values in the fields Grenadine reads,
dependencies without literal versions, and unsupported dependency options are
reported as errors. Generate a deps.edn file with Leiningen tooling when the
effective project depends on those features.

## Removed spellings

The pre-0.2 implicit and resolver-oriented forms are no longer accepted:

```text
grenadine deps.edn
grenadine --resolve NAME
grenadine --resolver MODE --resolve NAME
grenadine --resolvers
```

Use `--install`, `--expand`, `-M/--mediator`, and `--mediators` instead.
