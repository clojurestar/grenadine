# Troubleshooting

## A coordinate is rejected

Grenadine accepts Maven, Git, and local coordinates.
A coordinate must identify exactly one type, and Git or local directories must
contain `deps.edn` or `pom.xml`.
Use `:deps/manifest :deps` or `:deps/manifest :pom` to select one
explicitly.

## A POM or artifact cannot be downloaded

Check the coordinate and repository URLs.
The library defaults to Maven Central and Clojars.
The CLI merges `:mvn/repos` into those defaults.
Private repository authentication is not currently part of the public
repository contract.

Grenadine resolves Maven version ranges from repository metadata, but the
selected concrete POM and artifact must still exist.
It does not replace an
unavailable `-SNAPSHOT` with a nearby release or prerelease.

## A remote deps source skips a relative local root

Remote HTTP sources are fetched as individual files, not as repository
checkouts, so Grenadine cannot materialize a relative `:local/root`. It warns,
skips that dependency, and continues resolving the rest of the source. Use a
filesystem-backed deps source, an absolute root, or a Git coordinate when the
skipped dependency is needed.

For an isolated installation, select an explicit directory:

```sh
grenadine --repository=/tmp/grenadine-m2 --add deps.edn
```

## A project.clj value is rejected

Grenadine's CLI reads a safe, literal subset of `project.clj`; it does not run
Leiningen or evaluate project code. The project name and version must be
literal, and `:dependencies`, `:repositories`, `:local-repo`, and
`:exclusions` must use literal values in the supported shapes. Profiles,
managed dependencies, plugins, and reader evaluation are intentionally not
applied.

Use a generated deps.edn source when dependency selection relies on dynamic
forms, profile merging, user configuration, or managed dependency versions.

## Checksum mismatch

When a lock contains SHA-256, the downloaded or cached artifact must match it.
A mismatch is reported as `:checksum-mismatch` and the invalid download is not
installed.
Remove or replace a corrupt cached artifact only after confirming
the exact path reported in the failure.

## Unverified artifact warning

If neither lock SHA-256 nor a remote SHA-1 sidecar is available, Grenadine can
install the artifact but returns an `:unverified-artifact` warning.
Generate
an enriched lock from a trusted installation when reproducible integrity is
required.

## Host is missing a function

Portable repository operations require a host map.
An incomplete map throws `:grenadine.repo/incomplete-host` and identifies the
missing key.
Compare the map with the
[custom host contract](api-reference.md#custom-host-contract).

## Missing load-path hook

Runtime facades install dependencies and then ask the runtime to append
extracted roots.
Glojure and Jolt require their native hooks.
See [Runtime integrations](runtime-integrations.md).

## A loaded library was not upgraded

Runtime facades are deliberately add-only.
Requesting another coordinate for
an already-added top-level library retains the loaded coordinate and returns
`:loaded-lib-not-upgraded`.
Start a fresh process to load a different version.

## The no-install launcher fails

The Bash/Zsh launcher needs `curl`, an extractor (`tar`, or `unzip` for
Windows environments), and a supported SHA-256 utility.
The PowerShell
launcher needs `Invoke-WebRequest`, `Get-FileHash`, and `Expand-Archive`.
Unsupported OS/architecture combinations fail before downloading an archive.

## Report a problem

Include the Grenadine version, operating system, architecture, dependency
source with secrets removed, selected repository path, and complete error
output in a [GitHub issue](https://github.com/clojurestar/grenadine/issues).
