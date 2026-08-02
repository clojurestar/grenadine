# Troubleshooting

## A dependency requires `:mvn/version`

Grenadine 0.1 resolves Maven coordinates only. Replace Git, local, or other
coordinate types with a Maven version, or let another deps.edn implementation
handle those entries.

## A POM or artifact cannot be downloaded

Check the coordinate and repository URLs. The library defaults to Maven
Central and Clojars. The CLI merges `:mvn/repos` into those defaults. Private
repository authentication is not currently part of the public repository
contract.

For an isolated installation, select an explicit directory:

```sh
grenadine --repository=/tmp/grenadine-m2 --add deps.edn
```

## Checksum mismatch

When a lock contains SHA-256, the downloaded or cached artifact must match it.
A mismatch is reported as `:checksum-mismatch` and the invalid download is not
installed. Remove or replace a corrupt cached artifact only after confirming
the exact path reported in the failure.

## Unverified artifact warning

If neither lock SHA-256 nor a remote SHA-1 sidecar is available, Grenadine can
install the artifact but returns an `:unverified-artifact` warning. Generate
an enriched lock from a trusted installation when reproducible integrity is
required.

## Host is missing a function

Portable repository operations require a host map. An incomplete map throws
`:grenadine.repo/incomplete-host` and identifies the missing key. Compare the
map with the [custom host contract](api-reference.md#custom-host-contract).

## Missing load-path hook

Runtime facades install dependencies and then ask the runtime to append JARs
or extracted roots. Glojure and Jolt require their native hooks; let-go also
requires an `:add-roots!` callback. See [Runtime integrations](runtime-integrations.md).

## A loaded library was not upgraded

Runtime facades are deliberately add-only. Requesting another coordinate for
an already-added top-level library retains the loaded coordinate and returns
`:loaded-lib-not-upgraded`. Start a fresh process to load a different version.

## The no-install launcher fails

The Bash/Zsh launcher needs `curl`, an extractor (`tar`, or `unzip` for
Windows environments), and a supported SHA-256 utility. The PowerShell
launcher needs `Invoke-WebRequest`, `Get-FileHash`, and `Expand-Archive`.
Unsupported OS/architecture combinations fail before downloading an archive.

## Report a problem

Include the Grenadine version, operating system, architecture, dependency
source with secrets removed, selected repository path, and complete error
output in a [GitHub issue](https://github.com/clojurestar/grenadine/issues).
