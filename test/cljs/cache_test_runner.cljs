(ns cljs.cache-test-runner
  (:require [cljs.cache-test]
            [cljs.test :as test]))

(defmethod test/report [::test/default :end-run-tests] [summary]
  (set! (.-exitCode js/process)
        (if (test/successful? summary) 0 1)))

(defn -main []
  (test/run-tests 'cljs.cache-test))

(set! *main-cli-fn* -main)
