# CLI reference

```text
Usage: grenadine [OPTIONS] DEPS-SOURCE
       grenadine [--repository DIR] --list
       grenadine [--repository DIR] --add NAME [VERSION]...
       grenadine [--repository DIR] --remove NAME [VERSION]...
       grenadine [--repository DIR] [--resolver MODE] --resolve NAME [VERSION]...
       grenadine --resolvers
       grenadine --help
       grenadine --version
```

`DEPS-SOURCE` is a local path or an HTTP/HTTPS URL containing a deps.edn-style
EDN map. GitHub `blob` URLs are requested as raw content automatically.

## Options

| Short | Long | Behavior |
| --- | --- | --- |
| `-R DIR` | `--repository DIR` | Use this Maven repository. `--repository=DIR` is also accepted. |
| | `--list` | List libraries in the selected Maven repository. |
| | `--add` | Install one or more libraries. A version after each name is optional. |
| | `--remove` | Remove one or more libraries. A version after each name is optional. |
| | `--resolve` | Resolve complete dependency graphs without installing JARs. |
| | `--resolver MODE` | Use `newest`, `nearest`, or `tools-deps`. `--resolver=MODE` is also accepted. |
| | `--resolvers` | Describe the available resolver methodologies. |
| `-q` | `--quiet` | Suppress installed lines, warnings, and the summary. Errors still use stderr. |
| `-h` | `--help` | Print usage. |
| `-V` | `--version` | Print `grenadine vVERSION`. |

`--list`, `--add`, `--remove`, `--resolve`, `--resolvers`, and a dependency
source are mutually exclusive operations. `--resolver` modifies `--resolve`,
`--add`, or deps-source installation. Unknown options, missing option values,
invalid methodologies, and extra arguments exit with status 1.

## List a repository

```sh
grenadine --list
grenadine --repository=my-m2 --list
```

Listing uses `--repository`, then `GRENADINE_LOCAL_REPOSITORY`, then
`$HOME/.m2/repository`. It prints the sorted Maven coordinate and version of
each conventional main artifact JAR:

```text
org.clojure/clojure 1.10.3
org.clojure/spec.alpha 0.2.194
```

POM-only entries and classified artifacts such as sources and javadoc JARs are
not listed. `--list` does not accept a dependency source. With `--quiet`, it
validates that the repository can be read but prints nothing.

## Add libraries

```sh
grenadine --add nrepl/bencode 1.1.0 \
  clj-commons/clj-yaml org.flatland/ordered
```

Library names must be qualified `group/artifact` names. Each name may be
followed by a version. When the next argument is another qualified name, the
version was omitted. Grenadine resolves all omitted versions before installing
anything, then installs the requested roots and their transitive dependencies
using tools.deps mediation.

For an omitted version, Central and then Clojars Maven metadata are consulted.
The metadata `<release>` value wins, followed by `<latest>`, followed by the
highest listed non-SNAPSHOT version according to Maven version ordering. If
any omitted version cannot be resolved, nothing is installed.

`--add` uses `--repository`, then `GRENADINE_LOCAL_REPOSITORY`, then
`$HOME/.m2/repository`. Its streamed installation lines and final summary have
the same format as deps-source installation. `--quiet` suppresses them.

## Resolve libraries

```sh
grenadine --resolve org.yamlscript/ys.v0
grenadine --resolver=newest --resolve org.yamlscript/ys.v0
```

`--resolve` accepts the same `NAME [VERSION]...` sequence as `--add`. It
resolves omitted root versions from Maven metadata, builds each effective POM,
walks the complete transitive graph, and mediates version conflicts. It prints
every selected coordinate in deterministic `group/artifact VERSION` order:

```text
org.clojure/clojure VERSION
org.yamlscript/ys.v0 VERSION
```

All root versions and the complete graph are resolved before output begins, so
a failure does not produce a partial list. Required POMs are reused from or
cached in the local repository selected by `--repository`,
`GRENADINE_LOCAL_REPOSITORY`, or `$HOME/.m2/repository`. Artifact JARs are not
downloaded. `--quiet` suppresses coordinates and warnings while retaining the
success or failure exit status.

## Resolver methodologies

The CLI defaults to `tools-deps` mediation for deps-source installation,
`--add`, and `--resolve`. Select another methodology with either accepted
option form:

```sh
grenadine --resolver=newest --resolve org.yamlscript/ys.v0
grenadine --resolver nearest deps.edn
```

List the names and selection rules with:

```text
$ grenadine --resolvers
newest     Select the highest Maven-compatible version
nearest    Select the shortest path, then declaration order
tools-deps Preserve direct dependencies; otherwise select newest (default)
```

`--resolver` is rejected with `--list`, `--remove`, and `--resolvers` because
those operations do not perform graph mediation.

## Remove libraries

```sh
grenadine --remove nrepl/bencode 1.1.0 \
  clj-commons/clj-yaml org.flatland/ordered
```

A named version removes that version directory. An omitted version removes
the artifact directory and therefore all its locally installed versions.
Grenadine removes only the requested paths; it does not garbage-collect
transitive dependencies or prune empty group directories. Missing targets are
reported but are not errors.

```text
Removed nrepl/bencode 1.1.0
Missing clj-commons/clj-yaml (all versions)
=> Removed: 1  Missing: 1  Total: 2
```

All names, versions, and repository-descendant paths are validated before the
first deletion. Repeating the exact same request, or combining an all-version
request with a version-specific request for the same library, is rejected.
Multiple distinct explicit versions of one library are accepted. `--quiet`
suppresses removal lines and the summary.

## Input format

```clojure
{:mvn/local-repo "/optional/local/repository"
 :mvn/repos
 {"company" {:url "https://maven.example.com/releases/"}}
 :deps
 {org.clojure/data.csv {:mvn/version "1.1.0"}}}
```

The CLI requires a top-level EDN map. When `:deps` is present, it must be a map.
A valid file without `:deps` is treated as an empty dependency set and reports
zero installed, already-present, and total artifacts. Version 0.1 accepts Maven
coordinates with `:mvn/version`; other deps.edn coordinate types are rejected.

Configured `:mvn/repos` entries are merged with the Central and Clojars
defaults. Central and Clojars are tried first, followed by additional
repository identifiers in sorted order.

## Repository precedence

The destination repository is selected in this order:

1. `-R` or `--repository`;
2. `:mvn/local-repo` in the source;
3. `GRENADINE_LOCAL_REPOSITORY`;
4. `$HOME/.m2/repository`.

## Output

Each newly downloaded artifact is printed after it has been installed:

```text
Installed org.clojure/data.csv 1.1.0
```

Artifacts already present are not listed individually. The final line reports
all installed and cached artifacts:

```text
=> Installed: 1  Already: 3  Total: 4
```

Warnings are written to stderr. Quiet mode suppresses all non-error output.

## Examples

```sh
grenadine deps.edn
grenadine -R my-m2 deps.edn
grenadine --quiet deps.edn
grenadine --list
grenadine --add nrepl/bencode 1.1.0 clj-commons/clj-yaml
grenadine --remove nrepl/bencode 1.1.0 clj-commons/clj-yaml
grenadine --resolve org.yamlscript/ys.v0
grenadine --resolver=newest --resolve org.yamlscript/ys.v0
grenadine --resolvers
grenadine --repository=my-m2 \
  https://github.com/seancorfield/honeysql/blob/develop/deps.edn
```
