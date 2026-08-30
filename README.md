# cljs-cache

A maintained ClojureScript port of `clojure.core.cache`, forked from
[`pkpkpk/cljs-cache`](https://github.com/pkpkpk/cljs-cache).

This fork tracks the cache protocol and policies from
`org.clojure/core.cache` 1.2.263. It hardens the bounded LRU implementation for
modern ClojureScript and adds the bounded FIFO cache that was missing from the
original port.

## Dependency

The next release is:

```clojure
com.github.theronic/cljs-cache {:mvn/version "1.1.0"}
```

Until that artifact is published, depend on a tested commit from
[`theronic/cljs-cache`](https://github.com/theronic/cljs-cache) using a Git
dependency:

```clojure
com.github.theronic/cljs-cache
{:git/url "https://github.com/theronic/cljs-cache.git"
 :git/sha "677745d8041898a1cad1a9af1b42319e29ce79b2"}
```

## LRU usage

`lookup` reads a value and `hit` records recency; the protocol deliberately
keeps those operations separate. A caller must perform both for an LRU hit.
Ordinary `get`, keyword lookup, and `cljs.cache.wrapped/lookup` are read-only
and do not make a resident key more recent.

```clojure
(require '[cljs.cache :as cache])

(def c0 (cache/lru-cache-factory {} :threshold 256))
(def c1 (cache/miss c0 [:scope :key] {:completed true}))
(def value (cache/lookup c1 [:scope :key]))
(def c2 (cache/hit c1 [:scope :key]))
```

The threshold must be a positive JavaScript safe integer. Seed entries are
bounded immediately; their iteration order defines least-to-most-recent order.
Frequently hit entries survive the admission of colder entries. The internal
access clock is normalized before JavaScript integer precision can affect LRU
ordering.

The wrapped namespace stores the immutable cache in an atom. `wrapped/hit`,
`wrapped/miss`, and `wrapped/evict` update it atomically. Applications that
require independent computation, cancellation, or deadlines should compute
outside the cache and publish the completed value explicitly instead of using
`lookup-or-miss`.

## FIFO usage

```clojure
(require '[cljs.cache :as cache])

(def c0 (cache/fifo-cache-factory {} :threshold 256))
(def c1 (cache/miss c0 [:scope :key] {:completed true}))
(cache/lookup c1 [:scope :key])
```

`miss` follows the `CacheProtocol` contract: call it only when the key is
absent. `hit` is a no-op for FIFO. Its queue contains resident keys only: it
does not preallocate sentinel slots, and updating an existing key does not
evict a different entry or duplicate queue state.

## Verification

The test suite targets ClojureScript 1.12.42 and Node:

```sh
clojure -M:test -m cljs.main -co '{:target :nodejs :main cljs.cache-test-runner :output-to "target/test.js" :output-dir "target/test-out" :optimizations :none :warnings-as-errors true}' -c cljs.cache-test-runner
node target/test.js
```

CI runs the tests with both `:none` and `:advanced` optimizations and verifies
the generated POM and JAR metadata. The advanced leg elides assertions and
also runs an intentional-failure canary so a renamed Node property cannot turn
a failing test run green.

## Provenance and license

The fork starts from `pkpkpk/cljs-cache` commit
`4a2a3c8c6af93f0c69c09cdca99ebfa44552c34c`. FIFO code is adapted from
`clojure/core.cache` tag `v1.2.263`; `cljs.cache.wrapped` is adapted from
`clojure.core.cache.wrapped`. Both retain their original Eclipse Public
License 1.0 notices; see `epl-v10.html`. This corrects the inherited package's
incompatible MIT metadata rather than attempting to relicense EPL-derived
source.
