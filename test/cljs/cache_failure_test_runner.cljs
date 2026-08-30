(ns cljs.cache-failure-test-runner
  (:require [cljs.test :as test :refer-macros [deftest is]]))

(deftest intentional-failure
  (is false "CI must observe this intentional failure as a non-zero Node exit."))

(defmethod test/report [::test/default :end-run-tests] [summary]
  (aset js/process "exitCode"
        (if (test/successful? summary) 0 86)))

(defn -main []
  (test/run-tests 'cljs.cache-failure-test-runner))

(set! *main-cli-fn* -main)
