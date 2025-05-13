(ns weave.squint-test
  (:require [clojure.test :refer [deftest testing is]]
            [weave.squint :as squint]
            [clojure.string :as str]))

(deftest test-compile
  (testing "basic compilation"
    (is (= "squint.core.println(\"Hello World!\")"
           (squint/compile '((println "Hello World!"))))))

  (testing "multiple expressions"
    (is (= "squint.core.println(\"Hello World!\"); squint.core.println(\"Hello again!\")"
           (squint/compile '((println "Hello World!")
                             (println "Hello again!"))))))

  (testing "namespaced symbols are removed"
    (is (= "Math.max(1, 2)"
           (squint/compile '((js/Math.max 1 2)))))))

(deftest test-clj->js-macro
  (testing "clj->js macro with simple expression"
    (is (string? (squint/clj->js (println "test")))))

  (testing "clj->js macro with options"
    (is (string? (squint/clj->js {:elide-imports true} (println "test"))))))

(deftest test-clj->js-macro-variable-capture
  (testing "clj->js macro can capture variables")
  (is (= "var f = function () {\nreturn 43;;\n};\n"
         (let [x 43]
           (squint/clj->js
            (defn f [] ~x))))))
