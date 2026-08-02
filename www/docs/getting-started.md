# Getting started

Grenadine can be used as a pure Clojure library or as a standalone native
command. The library is intended for Clojure dialects and tools that need
Maven resolution. The command is convenient when you only need to populate a
local Maven repository.

## Add the library

Grenadine is published to Clojars as `cc.clojure/grenadine`:

```clojure
{:deps {cc.clojure/grenadine {:mvn/version "0.1.1"}}}
```

The smallest JVM-hosted installation looks like this:

```clojure
(require '[grenadine.core :as grenadine]
         '[grenadine.host.jvm :as jvm])

(def result
  (grenadine/install!
   '{org.clojure/data.csv {:mvn/version "1.1.0"}}
   {:host (jvm/host)
    :mediation :tools-deps}))

(select-keys result [:classpath :fetched :cached :warnings])
```

`install!` resolves the transitive graph, downloads missing POMs and JARs,
verifies available checksums, and returns data describing the installation.
It does not mutate the JVM classpath. Dialect integrations decide how the
returned classpath or extracted source roots become loadable.

See the [Library guide](library-guide.md) for the resolution pipeline and the
[Core API reference](api-reference.md) for every public operation.

## Use the command

Given a `deps.edn` file:

```clojure
{:deps {org.clojure/data.csv {:mvn/version "1.1.0"}}}
```

an installed binary populates the standard Maven repository with:

```sh
grenadine deps.edn
```

To use another repository:

```sh
grenadine --repository=my-m2 deps.edn
```

Or use a deps.edn URL:

```sh
grenadine --repository=my-m2 \
  https://github.com/seancorfield/honeysql/blob/develop/deps.edn
```

You can also run the latest release without installing `grenadine` on your PATH:

```sh
$ $(source <(curl -sL clojurestar.github.io/grenadine/get)) \
  https://github.com/seancorfield/honeysql/blob/develop/deps.edn
Installed org.clojure/clojure 1.10.3
Installed org.clojure/core.specs.alpha 0.2.56
Installed org.clojure/spec.alpha 0.2.194
=> Installed: 3  Already: 0  Total: 3
```

Continue with [Installation](installation.md) or the complete
[CLI reference](cli-reference.md).
