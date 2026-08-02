---
title: Grenadine
description: Pure Clojure Maven dependency resolution across Clojure dialects
hide:
  - navigation
  - toc
---

# Grenadine

> Pomegranate, with the JVM pressed out.

Grenadine is first a **pure Clojure library** for resolving and installing
Maven dependencies. Its resolver core runs unchanged on JVM Clojure,
[Babashka](https://babashka.org/),
[Glojure](https://github.com/glojurelang/glojure),
[Jolt](https://github.com/jolt-lang/jolt), and
[let-go](https://github.com/nooga/let-go).

It gives Clojure dialects a shared way to support `deps.edn`-style Maven
dependencies without reimplementing Maven resolution for every runtime.

## One resolver, many dialects

The portable `grenadine.core` library:

- parses and builds effective POMs;
- resolves transitive dependency graphs;
- supports newest, Maven-nearest, and tools.deps mediation;
- downloads artifacts and verifies checksums;
- emits deterministic locks; and
- prepares extracted Clojure source roots for non-JVM runtimes.

The portable API lives in `grenadine.core`. Small runtime integrations provide
filesystem, HTTP, digest, and load-path operations. Dialect-facing facades add
familiar `add-lib`, `add-libs`, `add-deps`, and `sync-deps` entry points.

[Get started with the library](getting-started.md){ .md-button .md-button--primary }
[Read the core API reference](api-reference.md){ .md-button }

## Also a standalone CLI

Grenadine also ships as a native command compiled with
[Gloat](https://gloathub.org/). It is useful when you want to install the
dependencies from a local or remote `deps.edn` by hand, without first running
a Clojure dialect or a JVM.

Install the latest release on Bash or Zsh:

```sh
source <(curl -sL clojurestar.github.io/grenadine/install)
```

Use an installed `grenadine` binary like this:

```sh
grenadine --add deps.edn
grenadine --repository=my-m2 --add deps.edn
grenadine --quiet --add deps.edn
grenadine --list
grenadine --list deps.edn
grenadine --add nrepl/bencode 1.1.0 clj-commons/clj-yaml
grenadine --delete nrepl/bencode 1.1.0
grenadine --remove clj-commons/clj-yaml
grenadine --expand org.yamlscript/ys.v0
grenadine -M newest --expand org.yamlscript/ys.v0
grenadine --mediators
grenadine --help
grenadine --version
```

HTTP and HTTPS URLs are accepted directly, including GitHub `blob` links:

```sh
grenadine --repository=my-m2 --add \
  https://github.com/seancorfield/honeysql/blob/develop/deps.edn
```

The command lists each newly installed dependency immediately, then prints
the installed, already-present, and total counts. Quiet mode suppresses
non-error output.

[Install Grenadine](installation.md){ .md-button .md-button--primary }
[Read the CLI reference](cli-reference.md){ .md-button }

## Run without installing

On Bash and Zsh, the current release can be downloaded, verified, and run from
a temporary cache without installing it on `PATH`:

```sh
$(source <(curl -sL clojurestar.github.io/grenadine/get)) --add deps.edn
```

PowerShell users can run:

```powershell
& ([scriptblock]::Create((Invoke-RestMethod https://clojurestar.github.io/grenadine/get.ps1))) --add deps.edn
```

## Platforms

Release binaries cover Linux on amd64, arm64, and armv6; macOS and Windows on
amd64 and arm64; and FreeBSD, OpenBSD, and NetBSD on amd64 and arm64.

See [Installation](installation.md#release-archives) for the complete platform
table and archive instructions.

For source, development instructions, and releases, see the
[Grenadine repository](https://github.com/clojurestar/grenadine).
