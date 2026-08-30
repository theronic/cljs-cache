;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns cljs.cache-test
  (:require [cljs.cache :refer [BasicCache FIFOCache TTLCache LRUCache
                                fifo-cache-factory ttl-cache-factory lru-cache-factory
                                lookup has? hit miss evict seed]]
            [cljs.cache.wrapped :as wrapped]
            [cljs.test :refer-macros [deftest run-tests testing is are async]]))

(enable-console-print!)

(deftest test-basic-cache-lookup
  (testing "that the BasicCache can lookup as expected"
    (is (= :robot (lookup (miss (BasicCache. {}) '(servo) :robot) '(servo))))))

(defn do-dot-lookup-tests [c]
  (are [expect actual] (= expect actual)
       1   (.lookup c :a)
       2   (.lookup c :b)
       42  (.lookup c :c 42)
       nil (.lookup c :c)))

(defn do-ilookup-tests [c]
  (are [expect actual] (= expect actual)
       1   (:a c)
       2   (:b c)
       42  (:X c 42)
       nil (:X c)))

(defn do-the-assoc [c]
  (are [expect actual] (= expect actual)
       1   (:a (assoc c :a 1))
       nil (:a (assoc c :b 1))))

(defn do-dissoc [c]
  (are [expect actual] (= expect actual)
       2   (:b (dissoc c :a))
       nil (:a (dissoc c :a))
       nil (:b (-> c (dissoc :a) (dissoc :b)))
       0   (count (-> c (dissoc :a) (dissoc :b)))))

(defn do-getting [c]
  (are [actual expect] (= expect actual)
       (get c :a) 1
       (get c :e) nil
       (get c :e 0) 0
       (get c :b 0) 2
       (get c :f 0) nil

       (get-in c [:c :e]) 4
       (get-in c '(:c :e)) 4
       (get-in c [:c :x]) nil
       (get-in c [:f]) nil
       (get-in c [:g]) false
       (get-in c [:h]) nil
       (get-in c []) c
       (get-in c nil) c

       (get-in c [:c :e] 0) 4
       (get-in c '(:c :e) 0) 4
       (get-in c [:c :x] 0) 0
       (get-in c [:b] 0) 2
       (get-in c [:f] 0) nil
       (get-in c [:g] 0) false
       (get-in c [:h] 0) 0
       (get-in c [:x :y] {:y 1}) {:y 1}
       (get-in c [] 0) c
       (get-in c nil 0) c))

(defn do-finding [c]
  (are [expect actual] (= expect actual)
       (find c :a) [:a 1]
       (find c :b) [:b 2]
       (find c :c) nil
       (find c nil) nil))

(defn do-contains [c]
  (are [expect actual] (= expect actual)
       (contains? c :a) true
       (contains? c :b) true
       (contains? c :c) false
       (contains? c nil) false))


(def big-map {:a 1 :b 2 :c {:d 3 :e 4} :f nil :g false nil {:h 5}})
(def small-map {:a 1 :b 2})

(defn fifo-model-step [{:keys [entries order limit] :as model}
                       [operation key value]]
  (case operation
    :hit model
    :evict {:entries (dissoc entries key)
            :order (vec (remove #(= key %) order))
            :limit limit}
    :put (if (contains? entries key)
           (assoc model :entries (assoc entries key value))
           (let [full? (>= (count entries) limit)
                 evicted (when full? (first order))]
             {:entries (cond-> entries
                         full? (dissoc evicted)
                         true (assoc key value))
              :order (cond-> (if full? (subvec order 1) order)
                       true (conj key))
              :limit limit}))))

(defn fifo-cache-step [cache [operation key value]]
  (case operation
    :hit (hit cache key)
    :evict (evict cache key)
    :put (miss cache key value)))

(def fifo-model-keys
  [nil false true 0 1 :a :b :cljs.cache/free [:scope 1] {:basis 2}])

(def fifo-model-values
  [nil false true 0 1 :value {:completed true} [:answer 3]])

(defn fifo-model-operations [n]
  (map (fn [state]
         (let [operation (case (mod state 5)
                           0 :hit
                           1 :evict
                           :put)]
           [operation
            (nth fifo-model-keys (mod state (count fifo-model-keys)))
            (nth fifo-model-values (mod (* state 7) (count fifo-model-values)))]))
       (take n (rest (iterate #(mod (+ (* % 73) 41) 9973) 17)))))

(deftest test-basic-cache-ilookup
  (testing "counts"
    (is (= 0 (count (BasicCache. {}))))
    (is (= 1 (count (BasicCache. {:a 1})))))
  (testing "that the BasicCache can lookup via keywords"
    (do-ilookup-tests (BasicCache. small-map)))
  #_(testing "that the BasicCache can .lookup"
    (do-dot-lookup-tests (BasicCache. small-map)))
  (testing "assoc and dissoc for BasicCache"
    (do-the-assoc (BasicCache. {}))
    (do-dissoc (BasicCache. {:a 1 :b 2})))
  (testing "that get and cascading gets work for BasicCache"
    (do-getting (BasicCache. big-map)))
  (testing "that finding works for BasicCache"
    (do-finding (BasicCache. small-map)))
  (testing "that contains? works for BasicCache"
    (do-contains (BasicCache. small-map))))

(deftest test-fifo-cache-ilookup
  (let [empty-q (.-EMPTY cljs.core/PersistentQueue)]
    (testing "that the FIFOCache can lookup via keywords"
      (do-ilookup-tests (fifo-cache-factory small-map :threshold 2)))
    (testing "assoc and dissoc for FIFOCache"
      (do-the-assoc (fifo-cache-factory {} :threshold 2))
      (do-dissoc (fifo-cache-factory {:a 1 :b 2} :threshold 2)))
    (testing "that get and cascading gets work for FIFOCache"
      (do-getting (fifo-cache-factory big-map :threshold (count big-map))))
    (testing "that finding works for FIFOCache"
      (do-finding (fifo-cache-factory small-map :threshold 2)))
    (testing "that contains? works for FIFOCache"
      (do-contains (fifo-cache-factory small-map :threshold 2)))
    (testing "raw constructors fail fast when policy metadata is inconsistent"
      (is (thrown? js/Error
                   (miss (FIFOCache. {:a 1} empty-q 1) :b 2)))
      (is (thrown? js/Error
                   (miss (FIFOCache. {:a 1 :b 2}
                                     (into empty-q [:a :b])
                                     1)
                         :c
                         3))))))

(deftest test-fifo-cache
  (testing "FIFO eviction ignores hits"
    (let [cache (fifo-cache-factory {} :threshold 2)]
      (is (= {:b 2 :c 3}
             (-> cache
                 (assoc :a 1)
                 (assoc :b 2)
                 (hit :a)
                 (assoc :c 3)
                 .-cache)))
      (is (identical? cache (hit cache :missing)))))

  (testing "updating a resident key neither evicts nor changes insertion order"
    (is (= {:b 20 :c 3}
           (-> (fifo-cache-factory {} :threshold 2)
               (assoc :a 1)
               (assoc :b 2)
               (assoc :b 20)
               (assoc :c 3)
               .-cache))))

  (testing "underfull, full, and overfull seeds remain bounded"
    (is (= {:a 1 :b 2 :c 3}
           (-> (fifo-cache-factory (sorted-map :a 1 :b 2) :threshold 3)
               (assoc :c 3)
               .-cache)))
    (is (= {:b 2 :c 3}
           (.-cache (fifo-cache-factory (sorted-map :a 1 :b 2 :c 3)
                                        :threshold 2))))
    (is (= {:b 2}
           (-> (fifo-cache-factory {} :threshold 1)
               (assoc :a 1)
               (assoc :b 2)
               .-cache))))

  (testing "evict followed by misses preserves FIFO occupancy (CCACHE-39)"
    (let [cache (fifo-cache-factory (sorted-map :a 1 :b 2) :threshold 2)
          after-one (-> cache (evict :b) (miss :c 42))
          after-two (miss after-one :d 43)]
      (is (= #{:a :c} (set (.-q after-one))))
      (is (= #{:c :d} (set (.-q after-two))))
      (is (= {:c 42 :d 43} (.-cache after-two)))))

  (testing "every key value is usable and queue metadata contains only resident keys"
    (let [special-key :cljs.cache/free
          cache (-> (fifo-cache-factory (array-map special-key 1 :b 2)
                                        :threshold 2)
                    (evict special-key)
                    (miss :c 3)
                    (miss :d 4))]
      (is (= {:c 3 :d 4} (.-cache cache)))
      (is (= (set (keys (.-cache cache))) (set (.-q cache))))
      (is (= (count (.-cache cache)) (count (.-q cache))))))

  (testing "eviction uses the map's actual comparator-equivalent key"
    (let [case-insensitive
          (fn [left right]
            (compare (.toLowerCase left) (.toLowerCase right)))
          cache (fifo-cache-factory
                 (sorted-map-by case-insensitive "A" 1 "B" 2)
                 :threshold 2)
          evicted (evict cache "a")
          final (-> evicted (miss "C" 3) (miss "D" 4))]
      (is (= ["B"] (vec (.-q evicted))))
      (is (= #{"C" "D"} (set (keys (.-cache final)))))
      (is (= (count (.-cache final)) (count (.-q final))))
      (is (= 2 (count final)))))

  (testing "capacity must be a positive integer"
    (is (thrown? js/Error (fifo-cache-factory {} :threshold 0)))
    (is (thrown? js/Error (fifo-cache-factory {} :threshold 1.5)))
    (is (thrown? js/Error
                 (fifo-cache-factory {}
                                     :threshold js/Number.MAX_VALUE))))

  (testing "wrapped factory exposes the same FIFO implementation"
    (let [cache-atom (wrapped/fifo-cache-factory {} :threshold 2)]
      (wrapped/miss cache-atom :a 1)
      (wrapped/miss cache-atom :b 2)
      (wrapped/miss cache-atom :c 3)
      (is (instance? FIFOCache @cache-atom))
      (is (= {:b 2 :c 3} (.-cache @cache-atom))))))

(deftest test-fifo-cache-against-reference-model
  (let [limit 7]
    (reduce (fn [[cache model step] operation]
              (let [next-cache (fifo-cache-step cache operation)
                    next-model (fifo-model-step model operation)]
                (is (= (:entries next-model) (.-cache next-cache))
                    (str "resident entries at step " step))
                (is (= (:order next-model) (vec (.-q next-cache)))
                    (str "FIFO order at step " step))
                (is (<= (count next-cache) limit)
                    (str "capacity at step " step))
                [next-cache next-model (inc step)]))
            [(fifo-cache-factory {} :threshold limit)
             {:entries {} :order [] :limit limit}
             0]
            (fifo-model-operations 500))))

(defn get-time []
  (.getTime (js/Date.)))

(deftest test-ttl-cache-ilookup
  (let [five-secs (+ 5000 (get-time))
        big-time   (into {} (for [[k _] big-map] [k five-secs]))
        small-time (into {} (for [[k _] small-map] [k five-secs]))]
    (testing "that the TTLCache can lookup via keywords"
      (do-ilookup-tests (TTLCache. small-map small-time 2000)))
    #_(testing "that the TTLCache can lookup via keywords"
      (do-dot-lookup-tests (TTLCache. small-map small-time 2000)))
    (testing "assoc and dissoc for TTLCache"
      (do-the-assoc (TTLCache. {} {} 2000))
      (do-dissoc (TTLCache. {:a 1 :b 2} {:a five-secs :b five-secs} 2000)))
    (testing "that get and cascading gets work for TTLCache"
      (do-getting (TTLCache. big-map big-time 2000)))
    (testing "that finding works for TTLCache"
        (do-finding (TTLCache. small-map small-time 2000)))
    (testing "that contains? works for TTLCache"
        (do-contains (TTLCache. small-map small-time 2000)))))

(deftest test-ttl-cache
  (let [C (ttl-cache-factory {} :ttl 500)]
    (testing "TTL-ness with empty cache"
      (is (= {:a 1 :b 2} (-> C (assoc :a 1) (assoc :b 2) .-cache))))
    (async done
      (let [C1 (-> C (assoc :a 1) (assoc :b 2))
            C2 (-> C (assoc :a 1))]
        (js/setTimeout
         #(do
            (testing "TTL-ness with empty cache, expired"
              (is (= {:c 3} (-> C1 (assoc :c 3) .-cache))))
            (testing "TTL cache does not return a value that has expired"
              (is (nil? (-> C2 (lookup :a)))))
            (done))
         700)))))

(deftest test-lru-cache-ilookup
  (testing "that the LRUCache can lookup via keywords"
    (do-ilookup-tests (lru-cache-factory small-map :threshold 2)))
  #_(testing "that the LRUCache can lookup via keywords"
    (do-dot-lookup-tests (LRUCache. small-map {} 0 2)))
  (testing "assoc and dissoc for LRUCache"
    (do-the-assoc (lru-cache-factory {} :threshold 2))
    (do-dissoc (lru-cache-factory {:a 1 :b 2} :threshold 2)))
  (testing "that get and cascading gets work for LRUCache"
    (do-getting (lru-cache-factory big-map :threshold (count big-map))))
  (testing "that finding works for LRUCache"
    (do-finding (lru-cache-factory small-map :threshold 2)))
  (testing "that contains? works for LRUCache"
    (do-contains (lru-cache-factory small-map :threshold 2))))

(deftest test-lru-cache
  (testing "LRU-ness with empty cache and threshold 2"
    (let [C (lru-cache-factory {} :threshold 2)]
      (are [x y] (= x y)
           {:a 1, :b 2} (-> C (assoc :a 1) (assoc :b 2) .-cache)
           {:b 2, :c 3} (-> C (assoc :a 1) (assoc :b 2) (assoc :c 3) .-cache)
           {:a 1, :c 3} (-> C (assoc :a 1) (assoc :b 2) (hit :a) (assoc :c 3) .-cache))))
  (testing "LRU-ness with seeded cache and threshold 4"
    (let [C (lru-cache-factory {:a 1, :b 2} :threshold 4)]
      (are [x y] (= x y)
           {:a 1, :b 2, :c 3, :d 4} (-> C (assoc :c 3) (assoc :d 4) .-cache)
           {:a 1, :c 3, :d 4, :e 5} (-> C (assoc :c 3) (assoc :d 4) (hit :c) (hit :a) (assoc :e 5) .-cache))))
  (testing "an overfull seed is reduced to the configured threshold"
    (let [cache (lru-cache-factory (sorted-map :a 1 :b 2 :c 3)
                                   :threshold 2)]
      (is (= {:b 2 :c 3} (.-cache cache)))
      (is (= 2 (count cache)))
      (is (= 2 (count (.-lru cache))))))
  (testing "updates and eviction use the map's comparator-equivalent key"
    (let [case-insensitive
          (fn [left right]
            (compare (.toLowerCase left) (.toLowerCase right)))
          cache (lru-cache-factory
                 (sorted-map-by case-insensitive "A" 1 "B" 2)
                 :threshold 2)
          updated (miss cache "a" 10)
          evicted (evict updated "b")]
      (is (= {"A" 10 "B" 2} (into {} (.-cache updated))))
      (is (= ["A"] (vec (keys (.-cache evicted)))))
      (is (= 1 (count (.-lru evicted))))))
  (testing "capacity must be a positive safe integer"
    (is (thrown? js/Error (lru-cache-factory {} :threshold 0)))
    (is (thrown? js/Error (lru-cache-factory {} :threshold 1.5)))
    (is (thrown? js/Error
                 (lru-cache-factory {}
                                    :threshold js/Number.MAX_VALUE))))
  (testing "regressions against LRU eviction before threshold met"
    (is (= {:b 3 :a 4}
           (-> (lru-cache-factory {} :threshold 2)
               (assoc :a 1)
               (assoc :b 2)
               (assoc :b 3)
               (assoc :a 4)
               .-cache)))

    (is (= {:e 6, :d 5, :c 4}
           (-> (lru-cache-factory {} :threshold 3)
               (assoc :a 1)
               (assoc :b 2)
               (assoc :b 3)
               (assoc :c 4)
               (assoc :d 5)
               (assoc :e 6)
               .-cache)))

    (is (= {:a 1 :b 3}
           (-> (lru-cache-factory {} :threshold 2)
               (assoc :a 1)
               (assoc :b 2)
               (assoc :b 3)
               .-cache))))

  (is (= {:d 4 :e 5}
         (-> (lru-cache-factory {} :threshold 2)
             (hit :x)
             (hit :y)
             (hit :z)
             (assoc :a 1)
             (assoc :b 2)
             (assoc :c 3)
             (assoc :d 4)
             (assoc :e 5)
             .-cache))))
