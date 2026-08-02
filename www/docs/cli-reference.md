# CLI reference

```text
Usage: grenadine [OPTIONS] DEPS-SOURCE
       grenadine --help
       grenadine --version
```

`DEPS-SOURCE` is a local path or an HTTP/HTTPS URL containing an EDN map with
a `:deps` map. GitHub `blob` URLs are requested as raw content automatically.

## Options

| Short | Long | Behavior |
| --- | --- | --- |
| `-R DIR` | `--repository DIR` | Install into this Maven repository. `--repository=DIR` is also accepted. |
| `-q` | `--quiet` | Suppress installed lines, warnings, and the summary. Errors still use stderr. |
| `-h` | `--help` | Print usage. |
| `-V` | `--version` | Print `grenadine vVERSION`. |

Only one dependency source is accepted. Unknown options, missing option
values, and extra arguments exit with status 1.

## Input format

```clojure
{:mvn/local-repo "/optional/local/repository"
 :mvn/repos
 {"company" {:url "https://maven.example.com/releases/"}}
 :deps
 {org.clojure/data.csv {:mvn/version "1.1.0"}}}
```

The CLI requires a top-level EDN map and a map-valued `:deps`. Version 0.1
accepts Maven coordinates with `:mvn/version`; other deps.edn coordinate types
are rejected.

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
grenadine --repository=my-m2 \
  https://github.com/seancorfield/honeysql/blob/develop/deps.edn
```
