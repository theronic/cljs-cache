# cljs-cache

A maintained ClojureScript port of `clojure.core.cache`, forked from
[`pkpkpk/cljs-cache`](https://github.com/pkpkpk/cljs-cache).

This fork adds the bounded FIFO cache from `org.clojure/core.cache` 1.2.263.
FIFO lookups do not mutate recency metadata, which makes it a useful bounded
cache for immutable, recomputable values.

## Dependency

The next release is:

```clojure
com.github.theronic/cljs-cache {:mvn/version "1.1.0"}
```

Until that artifact is published, depend on a tested commit from
[`theronic/cljs-cache`](https://github.com/theronic/cljs-cache) using a Git
dependency.

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
the generated POM and JAR metadata.

## Provenance and license

The fork starts from `pkpkpk/cljs-cache` commit
`4a2a3c8c6af93f0c69c09cdca99ebfa44552c34c`. FIFO code is adapted from
`clojure/core.cache` tag `v1.2.263`. The inherited and adapted source is
licensed under the Eclipse Public License 1.0; see `epl-v10.html`.
