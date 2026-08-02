# Installation

Grenadine publishes a Clojure library and
[native command archives](https://github.com/clojurestar/grenadine/releases).
The one-shot launchers download the current archive, verify its SHA-256
checksum, cache it in a temporary directory, and run the binary.

## Clojure library

Add the Clojars coordinate to `deps.edn`:

```clojure
{:deps {cc.clojure/grenadine {:mvn/version "0.1.2"}}}
```

The library contains the portable core, JVM host, and runtime integration
namespaces. Start with [Getting started](getting-started.md) and the
[Core API reference](api-reference.md).

## Bash and Zsh

Run without installing:

```sh
$(source <(curl -sL clojurestar.github.io/grenadine/get)) --add deps.edn
```

The sourced script prints the verified executable path. Command substitution
runs that path with the arguments that follow it. `curl`, an archive extractor,
and one supported SHA-256 command are required.

Set `GRENADINE_RELEASE_URL` to test or mirror the release assets from another
base URL.

## PowerShell

```powershell
& ([scriptblock]::Create((Invoke-RestMethod https://clojurestar.github.io/grenadine/get.ps1))) --add deps.edn
```

The PowerShell launcher verifies the ZIP with `Get-FileHash`, expands it below
the temporary directory, and forwards all arguments to `grenadine.exe`.

## Release archives

Archives and `grenadine-checksums.txt` are published on the
[GitHub releases page](https://github.com/clojurestar/grenadine/releases).
Extract the archive for your platform and place `grenadine` or
`grenadine.exe` on `PATH`.

| Operating system | Architectures |
| --- | --- |
| Linux | amd64, arm64, armv6 |
| macOS | amd64, arm64 |
| Windows | amd64, arm64 |
| FreeBSD | amd64, arm64 |
| OpenBSD | amd64, arm64 |
| NetBSD | amd64, arm64 |

Linux armv6 builds target GOARM=6. Other operating systems do not publish an
armv6 archive.

## Verify the installation

```sh
grenadine --version
grenadine --help
```
