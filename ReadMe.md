Grenadine
=========

Grenadine is a portable Maven dependency resolver in pure Clojure:
“pomegranate, with the JVM pressed out.”

The resolver core runs unchanged on JVM Clojure, Babashka, Glojure, Jolt, and
let-go. It parses and builds effective POMs, walks dependency graphs, supports
newest / Maven-nearest / tools.deps mediation, emits deterministic locks,
fetches artifacts, verifies checksums, and prepares extracted source roots for
non-JVM runtimes.


## Development

```sh
make test-all
make oracle
```

`test-all` runs the same portable suite on all five runtimes. `oracle` compares
Grenadine with JVM tools.deps and Maven `ComparableVersion`.

The currently usable end-to-end host implementations are JVM Clojure and
Babashka. Glojure, Jolt, and let-go already run the complete pure core and have
dependency facades; their native effect hosts need the remaining runtime
primitives described in the plan.


## Copyright and License

Copyright 2026 - Ingy dot Net

MIT License - See [License](License) file.
