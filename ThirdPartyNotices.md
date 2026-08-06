# Third-party notices

Grenadine generates portable source files from pinned revisions of the Clojure
`tools.deps` family and reviewable unified patches:

- `org.clojure/tools.deps` 0.31.1642
- `org.clojure/tools.deps.edn` 0.9.48
- `org.clojure/tools.gitlibs` 2.6.217

These projects are Copyright (c) Rich Hickey and contributors and are
distributed under the Eclipse Public License 1.0.
Grenadine gratefully credits the Clojure team and contributors who created and
maintain them, particularly Alex Miller.
Exact source revisions, checksums, generated file mappings, and patch names are
recorded in `patch/sources.yaml` and `Provenance.md`.
The EPL 1.0 text is included in `License`.

`src/grenadine/version.cljc` adapts version ordering and range parsing from
Apache Maven 3.9.16.
Apache Maven is Copyright 2001-2019 The Apache Software Foundation and is
distributed under the Apache License 2.0.
The license and NOTICE are included in `licenses/Apache-2.0.txt` and
`licenses/Apache-Maven-NOTICE.txt`.

Grenadine binaries are compiled with Gloat and include the Glojure runtime and
the Go standard library.
Their license texts and the licenses of embedded Go modules are included in each
release archive under `licenses/`.

Grenadine source is available from <https://github.com/clojurestar/grenadine>.
Each release includes its complete generated source, and the matching Git tag
can reproduce it with `make src`.
Grenadine's project license is the Eclipse Public License 1.0 in `License`.
