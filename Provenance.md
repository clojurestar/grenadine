# Grenadine provenance

Grenadine is a portable dependency resolver that follows and adapts the
dependency model implemented by the Clojure `tools.deps` family.
This document records the upstream sources used to generate Grenadine and
identifies the committed portability patches and derived modules.

## Audited upstream revisions

| Project | Revision | License |
| --- | --- | --- |
| [tools.deps](https://github.com/clojure/tools.deps/tree/v0.31.1642) | `v0.31.1642` | EPL 1.0 |
| [tools.deps.edn](https://github.com/clojure/tools.deps.edn/tree/v0.9.48) | `v0.9.48` | EPL 1.0 |
| [tools.gitlibs](https://github.com/clojure/tools.gitlibs/tree/v2.6.217) | `v2.6.217` | EPL 1.0 |
| [Apache Maven](https://github.com/apache/maven/tree/maven-3.9.16) | `maven-3.9.16` | Apache 2.0 |

## Source mapping

### EPL 1.0 generated code

`patch/sources.yaml` is the machine-readable source ledger.
`make src` downloads the pinned Git revisions, verifies the listed source-file
SHA-256 digests, and applies one unified patch per upstream project.
The resulting files under `src/grenadine/` are complete release sources but are
not committed to this repository.
`make patch` regenerates the patches from edits to those working files, and
`make src-check` proves that the patches reproduce them byte-for-byte.

- `patch/tools.deps.patch` transforms
  `tools.deps/src/main/clojure/clojure/tools/deps.clj`, especially the code from
  `excluded?` through `expand-deps`, into `src/grenadine/expander.cljc`.
  It also produces `src/grenadine/basis.cljc` from the upstream basis
  organization.
  Grenadine replaces JVM queues, concurrency, extension multimethods, and
  filesystem bindings with portable data structures and injected coordinate
  functions.
  It also adds portable warnings and a stable inclusion order.
- `patch/tools.gitlibs.patch` transforms
  `tools.gitlibs/src/main/clojure/clojure/tools/gitlibs/impl.clj` into
  `src/grenadine/gitlibs.cljc`.
  Grenadine retains the upstream cache layout, URL cleaning, Git commands, and
  ancestry comparison while routing effects through a host map.
- `patch/tools.deps.edn.patch` transforms
  `tools.deps.edn/src/main/clojure/clojure/tools/deps/edn.clj` into
  `src/grenadine/coordinate.cljc`.
  It retains simple-library canonicalization and incorporates coordinate-type
  and Git/local/Maven extension behavior while preserving Grenadine's public
  spelling for unqualified library keys.

The original source notices remain in every generated file.
The patches carry the Grenadine portability delta and are distributed under EPL
1.0.

`src/grenadine/expander.cljc` includes portable incomparable-version warning
handling contributed by Yogthos.

### Apache Maven derived code

- `src/grenadine/version.cljc` is a portable Clojure adaptation of the ordering
  rules in Apache Maven's
  `maven-artifact/src/main/java/org/apache/maven/artifact/versioning/ComparableVersion.java`
  and the interval grammar in `VersionRange.java`.

The Apache source notice is retained in that file.
Apache License 2.0 and the Apache Maven NOTICE are distributed in `licenses/`.

### Derived tests

- `test/grenadine/expander_test.cljc` adapts dependency graphs and expected
  selections from
  `tools.deps/src/test/clojure/clojure/tools/deps/test_deps.clj`.
- `test/grenadine/coordinate_test.cljc` adapts Git URL and cache-layout cases
  from the `tools.deps` and `tools.gitlibs` test suites.
- `test/grenadine/version_test.cljc` adapts ordering and range examples from
  Apache Maven's `ComparableVersionTest.java` and `VersionRangeTest.java`.

### Behavioral references without copied source

- `src/grenadine/pom.cljc` implements a deliberately smaller, portable
  effective-POM model.
  It is tested against Maven behavior but does not copy Maven Model Builder
  source.
- `src/grenadine/graph.cljc`, `repo.cljc`, `lock.cljc`, and `xml.cljc` implement
  Grenadine-specific mediation, portable repository access, deterministic locks,
  and a restricted XML parser.
  Maven repository layout and POM/XML data formats are interoperability
  requirements rather than copied source.
- Runtime hosts, dialect facades, the CLI, and source-loading code are
  Grenadine-specific.
- `test/grenadine/oracle.clj` executes the pinned `tools.deps` and Maven
  implementations as differential oracles; it does not embed their source.

If future code is copied or translated from an upstream project, add its exact
input and checksum to `patch/sources.yaml`, update this ledger, and retain its
upstream notice in the generated source in the same change.

## Credits and source availability

Grenadine gratefully acknowledges the Clojure team and contributors who built
and maintain `tools.deps`, `tools.deps.edn`, and `tools.gitlibs`, particularly
Alex Miller, and the original Clojure copyright holder Rich Hickey.
The Maven version semantics are the work of Apache Maven contributors, including
the authors named in the upstream `ComparableVersion` and `VersionRange`
sources.

Grenadine source is available at <https://github.com/clojurestar/grenadine>.
Every release includes a complete generated source archive.
Its matching Git tag contains the pinned manifest, patches, and generator needed
to reproduce that archive with `make src`.
