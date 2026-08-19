# Getting started

Grenadine supplies portable Clojure source and a standalone native command.
The source is intended for Clojure dialects and tools that need Maven, Git, and
local resolution.
The command prepares dependencies without starting a JVM.

## Use the portable facade

Glojure and Jolt embed their Grenadine integration in the dialect binary.
Portable programs use the same facade in either runtime:

```clojure
(require '[clojurestar.deps :as deps])

(deps/add-deps
 '{:deps
   {dev.weavejester/medley {:mvn/version "1.10.0"}}})
```

Use `glojure.deps` or `jolt.deps` for backend-specific operations and result
data.
Dialect and tool authors can obtain the vendorable source artifact as
`cc.clojure/grenadine:0.1.11` or use a release source archive.

See the [Library guide](library-guide.md) for the resolution pipeline and the
[Core API reference](api-reference.md) for every public operation.

## Use the command

Given a `deps.edn` file:

```clojure
{:deps {org.clojure/data.csv {:mvn/version "1.1.0"}}}
```

an installed binary populates the standard Maven repository with:

```sh
grenadine --install deps.edn
```

To use another repository:

```sh
grenadine --repository=my-m2 --install deps.edn
```

Or use a deps.edn URL:

```sh
grenadine --repository=my-m2 --install \
  https://github.com/seancorfield/honeysql/blob/develop/deps.edn
```

The CLI also accepts literal Leiningen projects without running Leiningen or
a JVM:

```sh
grenadine --expand project.clj
grenadine --expand \
  https://github.com/yaml/yamlscript/blob/main/v0/project.clj
```

Only literal top-level dependencies and repository settings are read. See the
[CLI reference](cli-reference.md#literal-projectclj-input) for the supported
subset.

You can also install coordinates directly.
A version is optional; without one,
Grenadine installs the latest Maven release:

```sh
grenadine --install nrepl/bencode 1.1.0 \
  clj-commons/clj-yaml org.flatland/ordered
```

Delete one exact version, or omit the version to delete every locally
installed version of that library:

```sh
grenadine --delete nrepl/bencode 1.1.0 \
  clj-commons/clj-yaml org.flatland/ordered
```

Use `--remove` instead when the complete expanded dependency closure should be
deleted.

Resolve and inspect a complete dependency graph without installing any JARs:

```sh
grenadine --expand org.yamlscript/ys.v0
```

Required POMs are reused from or cached in the selected local repository.
The result is a sorted list of selected coordinates.
Choose another conflict mediator, or list the available strategies, with:

```sh
grenadine -M newest --expand org.yamlscript/ys.v0
grenadine --mediators
```

You can also run the latest release without installing `grenadine` on your PATH:

```sh
$ $(source <(curl -sL clojurestar.github.io/grenadine/get)) --install \
  https://github.com/seancorfield/honeysql/blob/develop/deps.edn
Installed org.clojure/clojure 1.10.3
Installed org.clojure/core.specs.alpha 0.2.56
Installed org.clojure/spec.alpha 0.2.194
=> Installed: 3  Already: 0  Total: 3
```

Continue with [Installation](installation.md) or the complete
[CLI reference](cli-reference.md).
