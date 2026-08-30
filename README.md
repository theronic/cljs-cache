# cljs-cache

A maintained ClojureScript port of `clojure.core.cache`, forked from
[`pkpkpk/cljs-cache`](https://github.com/pkpkpk/cljs-cache).

This fork adds the bounded FIFO cache from `org.clojure/core.cache` 1.2.263.
FIFO lookups do not mutate recency metadata, which makes it a useful bounded
cache for immutable, recomputable values. Its queue contains resident keys
only: it does not preallocate sentinel slots, and updating an existing key
does not evict a different entry or duplicate queue state.

## Dependency

The next release is:

```clojure
com.github.theronic/cljs-cache {:mvn/version "1.1.0"}
```

Until that artifact is published, depend on a tested commit from
[`theronic/cljs-cache`](https://github.com/theronic/cljs-cache) using a Git
dependency. The exact `:git/sha` will be added here after the hardening review
is merged; do not pin the intermediate pull-request commit.

## FIFO usage

```clojure
(require '[cljs.cache :as cache])

(def c0 (cache/fifo-cache-factory {} :threshold 256))
(def c1 (cache/miss c0 [:scope :key] {:completed true}))
(cache/lookup c1 [:scope :key])
```

`miss` follows the `CacheProtocol` contract: call it only when the key is
absent. `hit` is a no-op for FIFO. The wrapped namespace also exposes
`fifo-cache-factory`, but applications that require independent computation,
cancellation, or deadlines should compute outside the cache and publish a
completed value explicitly instead of using `lookup-or-miss`.

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
