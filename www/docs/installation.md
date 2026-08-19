# Installation

Grenadine publishes a Clojure library and [native command
archives](https://github.com/clojurestar/grenadine/releases).
The installer and one-shot launchers select the current archive and verify its
SHA-256 checksum before using the binary.

## Vendored source

Grenadine publishes a source JAR to Clojars as:

```text
cc.clojure/grenadine:0.1.9
```

The artifact contains the portable core and Glojure integration sources.
It does not declare a Clojure runtime dependency or provide a JVM runtime host.
It is intended for dialect and tool authors to vendor into their builds.
Start with [Getting started](getting-started.md) and the [Core API
reference](api-reference.md).

## Install the native command

### Homebrew

On Linux and macOS, on Intel or ARM:

```sh
brew install clojurestar/grenadine/grenadine
```

To install a specific published version:

```sh
brew install clojurestar/grenadine/grenadine@0.1.9
```

### Bash and Zsh installer

On Bash and Zsh, download and verify the current release and install it under
`$HOME/.local/bin` with:

```sh
source <(curl -sL clojurestar.github.io/grenadine/install)
```

The installer uses `/usr/local` when run as root.
Set `PREFIX` to choose another installation prefix:

```sh
PREFIX=/opt/grenadine \
  source <(curl -sL clojurestar.github.io/grenadine/install)
```

This installs `/opt/grenadine/bin/grenadine`.
Ensure the selected `bin` directory is on `PATH`.

## Run without installing

### Bash and Zsh

Download, verify, and run the command from a temporary cache:

```sh
$(source <(curl -sL clojurestar.github.io/grenadine/get)) \
  -X https://github.com/yaml/yamlscript/blob/main/core/deps.edn
```

The sourced script prints the verified executable path.
Command substitution runs that path with the arguments that follow it.
`curl`, an archive extractor, and one supported SHA-256 command are required.

Set `GRENADINE_RELEASE_URL` to test or mirror the release assets from another
base URL.

### PowerShell

```powershell
& ([scriptblock]::Create((Invoke-RestMethod https://clojurestar.github.io/grenadine/get.ps1))) `
  -X https://github.com/yaml/yamlscript/blob/main/core/deps.edn
```

The PowerShell launcher verifies the ZIP with `Get-FileHash`, expands it below
the temporary directory, and forwards all arguments to `grenadine.exe`.

## Release archives

Archives and `grenadine-checksums.txt` are published on the [GitHub releases
page](https://github.com/clojurestar/grenadine/releases).
Extract the archive for your platform and place `grenadine` or `grenadine.exe`
on `PATH`.

Each release also publishes `grenadine-VERSION-src.tar.gz`, containing the
complete generated portable source tree, the pinned upstream manifest,
portability patches, licenses, and provenance ledger.

| Operating system | Architectures |
| --- | --- |
| Linux | amd64, arm64, armv6 |
| macOS | amd64, arm64 |
| Windows | amd64, arm64 |
| FreeBSD | amd64, arm64 |
| OpenBSD | amd64, arm64 |
| NetBSD | amd64, arm64 |

Linux armv6 builds target GOARM=6.
Other operating systems do not publish an armv6 archive.

## Build from source

From a Grenadine source checkout, build the native command with Gloat and
install it on `PATH` with:

```sh
make install
```

The build runs `make src` automatically to verify and assemble the
upstream-backed namespaces.

For a regular user this installs `grenadine` in `$HOME/.local/bin`.
When run as root it defaults to `/usr/local/bin`.
Override the prefix when needed:

```sh
make install PREFIX=/opt/grenadine
```

The resulting command is installed as `/opt/grenadine/bin/grenadine`.

## Verify the installation

```sh
grenadine --version
grenadine --help
```
