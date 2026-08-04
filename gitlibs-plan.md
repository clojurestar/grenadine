# Add Git and Local Coordinates

## Summary

Extend Grenadine’s resolver, CLI, locks, and dialect APIs to support tools.deps-compatible Maven, Git, and local coordinates. Move Jolt’s generic Git/local resolution into Grenadine, then make Jolt delegate to it while retaining Jolt-specific project and runtime behavior.

## Implementation Changes

- Add a coordinate layer that recognizes exactly one coordinate type:
  - Maven: `:mvn/version`
  - Git: `:git/url`, `:git/sha`, optional `:git/tag`, `:deps/root`, and `:deps/manifest`
  - Local: `:local/root`, optional `:deps/root`, and `:deps/manifest`
  - Continue accepting legacy Git keys `:sha` and `:tag`.
  - Reject ambiguous, incomplete, or unknown coordinates with structured errors.

- Implement tools.deps-compatible resolution:
  - Infer common Git hosting URLs from library symbols when `:git/url` is absent.
  - Resolve tags and abbreviated SHAs to a verified full commit SHA.
  - Prefer descendant Git commits; fail for unrelated histories.
  - Canonicalize local paths relative to the declaring deps file.
  - Accept local directories and JAR files.
  - Read `deps.edn` or `pom.xml`, in that order, unless `:deps/manifest` selects one explicitly.
  - Require a manifest for Git and local directories.
  - Read embedded Maven metadata from local JARs for transitive dependencies.
  - Honor dependency `:paths`, `:deps/root`, exclusions, overrides, and defaults.
  - Fail when the same library resolves to incomparable coordinate types, different local roots, or unrelated Git commits.

- Add Git procurement compatible with tools.gitlibs:
  - Use `_repos/<clean-url>` for mirrors and `libs/<namespace>/<name>/<full-sha>` for worktrees.
  - Clone/fetch mirrors, create detached worktrees, and initialize submodules.
  - Safely reuse a shared read-write cache and avoid exposing partial checkouts.
  - Require a usable Git executable only when a Git coordinate is encountered.
  - Honor `GITLIBS_COMMAND` for selecting the Git executable.

- Configure the Git cache with this precedence:
  1. `-G DIR` or `--gitlibs DIR`
  2. top-level `:gitlibs/dir` in a deps map
  3. `GRENADINE_GITLIBS`
  4. `GITLIBS`
  5. `~/.gitlibs`

- Extend the portable host contract with safe argument-vector process execution, canonical paths, file-type checks, directory traversal, and atomic directory publication. Implement it for the JVM, Babashka, Glojure, Jolt, and Let-Go hosts without routing commands through a shell.

## Public APIs, CLI, and Locks

- Make `grenadine.core/resolve-graph`, `install!`, `emit-lock`, `fetch-lock!`, `prepare-source-roots!`, and the dialect `add-deps` facades operate on mixed Maven/Git/local graphs.
- Return classpath roots as:
  - installed Maven JARs;
  - Git manifest paths beneath the cached checkout;
  - canonical local directories or JARs.
- Extract local JAR sources for runtimes that cannot load JARs directly, using the existing safe extraction rules.

- Extend CLI dependency-file handling:
  - `--add` procures Git dependencies, validates local dependencies, and installs Maven artifacts.
  - `--expand` may clone/fetch Git repositories and cache Maven POMs, but does not install Maven JARs.
  - `--list ITEMS` reports Maven installation, Git checkout, and local-path availability, marking absent entries `MISSING`.
  - Bare `--list` continues to list the Maven repository.
  - `--delete` and `--remove` reject requests whose expanded graph contains Git or local coordinates before deleting anything.
  - Direct Git/local map syntax is not added to the command line; users supply those coordinates through a local or remote deps file.

- Introduce lock version 2:
  - `:libs` records every selected library, its canonical coordinate, manifest type, and effective relative classpath paths.
  - Git coordinates store the canonical URL and full SHA.
  - Local coordinates store the canonical absolute root.
  - `:artifacts` and `:repos` continue to describe Maven downloads and integrity.
  - Classpath reconstruction derives Git roots from the configured Git cache and local roots from their canonical paths.
  - Continue reading, fetching, and constructing classpaths from version 1 Maven-only locks.
  - Always emit version 2 for new resolutions with deterministic library and path ordering.

- Update README, website, API docs, CLI help, and examples to describe all three coordinate types, cache configuration, Git prerequisites, manifest requirements, and mixed-coordinate limitations.

## Jolt Migration

- Replace Jolt’s generic coordinate identification, Git cache, local dependency, graph expansion, and comparison implementations with Grenadine APIs.
- Preserve Jolt-specific alias/task processing, intrinsic-library filtering, `:jolt/native`, prep warnings, and runtime classpath mutation.
- Treat `JOLT_GITLIBS` as a final Jolt compatibility fallback after the standard Grenadine/tools.gitlibs configuration sources.
- Make Jolt follow Grenadine’s manifest requirement instead of retaining a separate bare-source fallback.
- Commit Grenadine and Jolt changes separately so the Jolt change can be reviewed as a focused migration.

## Test Plan

- Add pure coordinate tests for detection, canonicalization, URL inference, legacy Git keys, configuration precedence, and every incomparable-coordinate error.
- Test Git behavior using temporary local repositories: full and abbreviated SHAs, tags, ancestry mediation, unrelated histories, cache reuse, submodules, `:deps/root`, interrupted publication, and concurrent access.
- Test local directories and JARs: relative paths, canonical identity, nested local dependencies, deps and POM manifests, explicit manifest selection, embedded JAR POMs, missing paths, missing manifests, and safe extraction.
- Test mixed Maven/Git/local graphs, exclusions, cycles, overrides/defaults, stable ordering, and each mediator.
- Test lock v2 round trips, deterministic output, classpath reconstruction, integrity fetching, and v1 compatibility.
- Test CLI add/expand/list behavior, Git cache flags and environment precedence, missing statuses, quiet output, and remove/delete preflight rejection.
- Run Grenadine’s complete suite across supported dialects, native Gloat-built CLI scenarios, and Jolt’s full suite plus migration regressions without network-dependent tests.
