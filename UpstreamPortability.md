# Draft: portable dependency tooling seams

Status: draft for an Ask Clojure design discussion and possible Clojure JIRA
issues. This document has not been posted or submitted upstream.

## Problem

Clojure dialects that do not run on the JVM need to resolve `deps.edn` data,
Maven artifacts, Git coordinates, and local coordinates without requiring Java
as a separate installation step. Grenadine currently does this by generating
portable source from exact revisions of `tools.deps`, `tools.deps.edn`, and
`tools.gitlibs` plus EPL 1.0 patches.

The long-term goal is to reduce those patches by moving generally useful
portability seams upstream while preserving the existing JVM APIs and
behavior.

## Candidate seams

### tools.deps.edn

- Keep canonicalization and alias/map operations independent of filesystem and
  JVM validation code.
- Pass text/EDN reading and path discovery through small functions so dialects
  can supply native implementations.
- Preserve current JVM entry points as adapters over the pure operations.

### tools.gitlibs

- Separate URL cleaning, revision selection, cache layout, and ancestry rules
  from `java.io.File`, `ProcessBuilder`, threads, and global configuration.
- Accept explicit path, environment, and process-runner operations.
- Preserve the existing JVM namespace as a default adapter using those
  operations.

### tools.deps

- Expose dependency expansion and version selection as a synchronous pure
  engine parameterized by coordinate operations.
- Keep concurrency as an optional outer optimization rather than a requirement
  of the expansion algorithm.
- Allow callers to provide canonicalization, identity, comparison, child
  dependency, manifest, and procurement functions without loading Maven/JVM
  extension implementations.
- Preserve the current extension API and basis API as JVM adapters over the
  portable engine where practical.

### Maven

Maven resolution is the deliberately separate part. Grenadine supplies a
portable Clojure implementation of the required repository, POM, version, and
range behavior. It can act as a non-JVM Maven extension if `tools.deps` exposes
the seams above; it is not proposed as a replacement for the JVM Maven
implementation.

## Compatibility and validation

- Existing JVM behavior and public APIs should remain unchanged.
- Proposed pure entry points should have no dependencies beyond the Clojure
  language/runtime available to a dialect.
- Grenadine's differential corpus can compare complete `:libs`, `:classpath`,
  and `:classpath-roots` results against JVM `tools.deps`.
- The same portable tests should run on JVM Clojure, Babashka, Glojure, Jolt,
  and let-go.

## Possible issue split

1. Extract pure `tools.deps.edn` map operations from JVM I/O.
2. Add explicit filesystem/process operations to `tools.gitlibs`.
3. Extract a synchronous coordinate-parameterized expansion engine from
   `tools.deps`.
4. Define how alternate Maven extensions can supply manifests, child
   dependencies, paths, and version comparison without loading JVM Maven code.

The exact upstream revisions and Grenadine's current delta are recorded in
`patch/sources.yaml`, `patch/*.patch`, and `Provenance.md` so each candidate
change can be evaluated against concrete working code rather than a new
independent implementation.
