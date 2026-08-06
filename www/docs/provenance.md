# Provenance and license

Grenadine is a portable dependency resolver that follows and adapts the
dependency model implemented by the Clojure `tools.deps` family.

## Upstream projects

The current source audit is pinned to:

| Project | Revision | License |
| --- | --- | --- |
| [`tools.deps`](https://github.com/clojure/tools.deps/tree/v0.31.1642) | `v0.31.1642` | EPL 1.0 |
| [`tools.deps.edn`](https://github.com/clojure/tools.deps.edn/tree/v0.9.48) | `v0.9.48` | EPL 1.0 |
| [`tools.gitlibs`](https://github.com/clojure/tools.gitlibs/tree/v2.6.217) | `v2.6.217` | EPL 1.0 |
| [Apache Maven](https://github.com/apache/maven/tree/maven-3.9.16) | `maven-3.9.16` | Apache 2.0 |

The portable expander, coordinate handling, basis construction, and Git cache
are generated from exact files in the Clojure projects plus unified
portability patches. `make src` verifies the pinned files and assembles the
complete tree; `make patch` records edits back into the patch set. Maven
version ordering and range parsing adapt Apache Maven's `ComparableVersion`
and `VersionRange`. Original notices are retained in the affected source
files. The repository's
[`Provenance.md`](https://github.com/clojurestar/grenadine/blob/main/Provenance.md)
contains the complete file-by-file audit and exact commit identifiers.

Grenadine gratefully acknowledges the Clojure team and contributors who built
and maintain these projects, particularly Alex Miller, and the original
Clojure copyright holder Rich Hickey. We also acknowledge the Apache Maven
contributors. Grenadine contributor Yogthos improved portable
incomparable-version warning handling.

## License and source

Grenadine is distributed under the
[Eclipse Public License 1.0](https://github.com/clojurestar/grenadine/blob/main/License).
Apache-derived portions retain their Apache License 2.0 notices. All applicable
license and notice files are included in source, JAR, and native release
distributions.

Grenadine releases through tag `v0.1.5` were distributed under the MIT
License. That license remains included for code received under its terms; the
current project license is EPL 1.0.

Grenadine source is available from the
[project repository](https://github.com/clojurestar/grenadine). Each release
includes a complete generated source archive. Its Git tag contains the pinned
manifest and patches needed to reproduce that archive with `make src`.
