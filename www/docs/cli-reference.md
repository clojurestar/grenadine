# CLI reference

```text
Usage: grenadine
       grenadine [OPTIONS] --list [ITEM...]
       grenadine [OPTIONS] [-M MODE] --add ITEM...
       grenadine [OPTIONS] --delete ITEM...
       grenadine [OPTIONS] [-M MODE] --remove ITEM...
       grenadine [OPTIONS] [-M MODE] --expand ITEM...
       grenadine --mediators
       grenadine --help
       grenadine --version
```

An `ITEM` is either `NAME [VERSION]` or a local/remote deps source. Operations
accept mixed item lists. Bare `grenadine` prints the same help as `--help`;
operands without an explicit operation are rejected.

## Options

| Short | Long | Behavior |
| --- | --- | --- |
| `-R DIR` | `--repository DIR` | Use this local Maven repository. |
| `-G DIR` | `--gitlibs DIR` | Use this tools.gitlibs-compatible Git cache. |
| `-M MODE` | `--mediator MODE` | Use `newest`, `nearest`, or `tools-deps`. |
| | `--list` | List the repository or report an expanded graph's local status. |
| | `--add` | Expand and install all selected dependencies. |
| | `--delete` | Delete only explicitly requested coordinates. |
| | `--remove` | Expand inputs and delete their complete dependency closures. |
| `-X` | `--expand` | Print an expanded graph without installing JARs. |
| | `--mediators` | Describe the available mediation strategies. |
| `-q` | `--quiet` | Suppress non-error operational output. |
| `-h` | `--help` | Print usage. |
| | `--version` | Print `grenadine vVERSION`. |

Exactly one operation is accepted. `--repository=DIR` and
`--mediator=MODE` are equivalent to their separated forms.

## Mixed inputs

```sh
grenadine --add \
  nrepl/bencode 1.1.0 \
  deps.edn \
  clj-commons/clj-yaml \
  https://example.org/other-deps.edn
```

HTTP/HTTPS URLs, existing files, and `.edn` paths are recognized as sources.
Each qualified library name may be followed by a version; omitted versions
select the latest Maven release. Source roots and named roots are combined in
operand order, with a later declaration replacing an earlier declaration of
the same library.

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
```

With items, `--list` composes and expands them like `--expand`, then checks
whether each selected Maven JAR, Git checkout, or local path exists:

```sh
grenadine -M nearest --list deps.edn org.example/library 2.0.0
```

```text
demo/branch 1.0.0
demo/core 2.0.0 MISSING
demo/root 1.0.0
=> Installed: 2  Missing: 1  Total: 3
```

POMs may be cached during expansion, but cached metadata does not count as an
installed JAR.

## Add and expand

```sh
grenadine --add deps.edn org.example/library
grenadine -M newest --expand deps.edn org.example/library 2.0.0
```

Both operations combine all inputs and mediate once. `--add` installs the
selected Maven artifacts and Git checkouts, validates local paths, and prints
streamed installation lines plus its summary. `--expand` prints sorted
coordinates and installs no Maven JARs. Both may cache POM metadata and procure
Git checkouts required to read manifests.

## Delete exact coordinates

```sh
grenadine --delete org.example/library 1.2.3
grenadine --delete org.example/library
grenadine --delete deps.edn additional-deps.edn
```

A versioned name deletes that version. An unversioned name deletes all locally
installed versions. A deps source contributes only its top-level `:deps`; it
is not expanded. Multiple distinct explicit versions are accepted, but an
all-version request cannot be combined with version-specific requests for the
same library.

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

`-M/--mediator` is valid with `--add`, `--expand`, `--remove`, and `--list`
when list items are supplied. It is rejected with plain `--list`, `--delete`,
and `--mediators`. The `--mediators` operation does not accept other options.

## Input format

```clojure
{:mvn/local-repo "/optional/local/repository"
 :mvn/repos
 {"company" {:url "https://maven.example.com/releases/"}}
 :deps
 {org.clojure/data.csv {:mvn/version "1.1.0"}}}
```

The source must contain an EDN map. Missing `:deps` means an empty dependency
set. Coordinates may use `:mvn/version`, `:git/url` with `:git/sha`, or
`:local/root`. Git and local directory coordinates require `deps.edn` or
`pom.xml`; `:deps/root` and `:deps/manifest` refine where and how that manifest
is read. Direct Git/local map syntax is intentionally not parsed as CLI items;
put those coordinates in a local or remote deps source.

## Removed spellings

The pre-0.2 implicit and resolver-oriented forms are no longer accepted:

```text
grenadine deps.edn
grenadine --resolve NAME
grenadine --resolver MODE --resolve NAME
grenadine --resolvers
```

Use `--add`, `--expand`, `-M/--mediator`, and `--mediators` instead.
