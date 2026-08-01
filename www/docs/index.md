---
title: Grenadine
description: A portable Maven dependency installer without Java
hide:
  - navigation
  - toc
---

# Grenadine

> Pomegranate, with the JVM pressed out.

Grenadine is a portable Maven dependency installer compiled with
[Gloat](https://gloathub.org/). It resolves the Maven dependencies in a local
or remote `deps.edn` source and installs them into your local Maven repository
without Java.

## Use it

```sh
grenadine deps.edn
grenadine --repository=my-m2 deps.edn
grenadine --quiet deps.edn
grenadine --help
grenadine --version
```

HTTP and HTTPS URLs are accepted directly, including GitHub `blob` links:

```sh
grenadine --repository=my-m2 \
  https://github.com/yaml/yamlscript/blob/main/core/deps.edn
```

Grenadine lists each newly installed dependency immediately, then prints the
installed, already-present, and total counts. Quiet mode suppresses non-error
output.

## Run without installing

On Bash and Zsh, the current release can be downloaded, verified, and run from
a temporary cache without installing it on `PATH`:

```sh
$(source <(curl -s clojurestar.github.io/grenadine/get)) deps.edn
```

PowerShell users can run:

```powershell
& ([scriptblock]::Create((Invoke-RestMethod https://clojurestar.github.io/grenadine/get.ps1))) deps.edn
```

## Platforms

Release binaries cover Linux on amd64, arm64, and armv6; macOS and Windows on
amd64 and arm64; and FreeBSD, OpenBSD, and NetBSD on amd64 and arm64.

Full documentation is coming later. For now, see the
[Grenadine repository](https://github.com/clojurestar/grenadine) for source,
development instructions, and releases.
