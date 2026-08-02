# Grenadine dialect bindings: loading Medley

These examples investigate one concrete question across Grenadine's five
supported execution environments:

> Can running code install `dev.weavejester/medley` 1.10.0, require
> `medley.core`, and call `map-vals`?

Run every example from this directory:

```sh
make
```

Or run one dialect at a time:

```sh
make run-clojure
make run-babashka
make run-glojure
make run-jolt
make run-jolt2
make run-let-go
```

The Makefile uses [Makes](https://github.com/makeplus/makes) to install pinned
local copies of all five runtimes. Runtime downloads stay under the
repository's `.cache/` directory, and every example uses `./m2` as its local
Maven repository.

## Common operation

Every example requests the current Medley coordinate:

```clojure
{dev.weavejester/medley {:mvn/version "1.10.0"}}
```

The successful examples produce exactly:

```clojure
{:one 2, :two 3}
```

## Results

| Dialect | Result | Integration path |
| --- | --- | --- |
| JVM Clojure | Works | `grenadine.core/install!`, then a dedicated `DynamicClassLoader` |
| Babashka | Works | `grenadine.bb/add-lib`, which updates Babashka's classpath |
| Glojure | Blocked | The facade adds Grenadine's extracted root, but Glojure 0.7.4 rejects Medley's reader-conditional binding form |
| Jolt | Works with a prepared root | `grenadine.runtime/add-libs!` plus Jolt's source-root mutation hook |
| Jolt 2 | Works directly | Jolt's built-in `jolt.deps/add-deps` installs and loads Medley |
| let-go | Blocked | `let-go.deps` has no language-level load-path mutation hook |

`make` treats the two blocked cases as expected-failure probes. It verifies
their exact diagnostic instead of silently skipping them.

## JVM Clojure

Grenadine's portable core installs artifacts but intentionally does not mutate
the JVM classpath. The example creates a child `DynamicClassLoader`, adds every
path returned by `install!`, binds it while requiring Medley, and then calls
Medley normally.

This is useful for embedders that own classloader policy. An ordinary Clojure
application should usually declare Medley before process startup instead of
changing its classpath at runtime.

## Babashka

`grenadine.bb` is the complete dynamic experience. Its `add-lib` operation
installs the graph and calls `babashka.classpath/add-classpath`, so the next
top-level form can require and use Medley.

## Glojure

The example supplies a source root already extracted by Grenadine to isolate
the dialect-facing part of the integration. `glojure.deps/add-lib` successfully
calls `clojure.core/add-load-path`. Loading Medley 1.10.0 then fails at
`medley.core` line 458 with:

```text
let requires an even number of forms in binding vector
```

The triggering binding uses a `#?` reader conditional. This is a Glojure/Medley
source-compatibility issue after dependency installation, not a Maven resolver
failure. The checked-in example is intentionally the desired code and the
Makefile verifies the current failure.

## Jolt

Jolt can load and run Medley 1.10.0. Its own runtime already owns the
`jolt.deps` namespace, so the example calls Grenadine's shared
`grenadine.runtime/add-libs!` seam directly. The Makefile first asks Grenadine
to install and extract only Medley's source root; the example appends that root
through `jolt.host/set-source-roots!`, requires Medley, and uses it.

This lower-level example isolates Grenadine's portable runtime seam. Jolt
applications should normally use the simpler built-in API shown next.

## Jolt 2

Jolt already embeds Grenadine in its own dependency resolver. The simpler
example uses the public `jolt.deps/add-deps` function, including `./m2` as the
local Maven repository. Jolt resolves, installs, extracts, and adds Medley's
source root in one call, so no Grenadine host or loader hook is needed in the
application.

## let-go

Grenadine's `let-go.deps` facade accepts both an installation host and an
`:add-roots!` callback. The let-go embedding API has `SetLoadPath`, but let-go
1.11.1 does not expose an equivalent language function. The example therefore
reaches the facade's explicit `:grenadine.runtime/missing-load-path-hook`
failure after Grenadine has produced the source root.

The missing hook must be exposed by let-go or supplied by an embedding before
code can dynamically require Medley in the same process.
