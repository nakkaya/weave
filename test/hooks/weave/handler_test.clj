(ns hooks.weave.handler-test
  (:require [clj-kondo.core :as clj-kondo]
            [clojure.string :as str]
            [clojure.test :refer [deftest testing is]]))

(defn- findings
  "Lint forms with weave's exported clj-kondo config, in a namespace
   that aliases weave.core as weave."
  [& forms]
  (let [src (binding [*print-namespace-maps* false]
              (->> (map pr-str forms)
                   (cons "(ns probe (:require [clojure.string :as str] [weave.core :as weave]))")
                   (str/join "\n")))]
    (:findings (with-in-str src
                 (clj-kondo/run! {:lint ["-"]
                                  :config-dir "resources/clj-kondo.exports/weave/weave"
                                  :cache false
                                  :repro true})))))

(defn- uncaptured
  "Names of the symbols reported as uncaptured."
  [& forms]
  (->> (apply findings forms)
       (filter #(= :weave/uncaptured-symbol (:type %)))
       (map #(second (re-find #"'(.+)'" (:message %))))
       set))

(deftest test-threading-steps
  (testing "a var in a bare threading step is not a capture"
    (is (= #{} (uncaptured '(defn h [] (weave/handler [] (some-> "1" str/trim parse-long)))
                           '(defn h [] (weave/handler [] (->> "1" str/trim parse-long)))
                           '(defn h [] (weave/handler [] (cond-> "1" true parse-long)))
                           '(defn h [] (weave/handler [] (map parse-long ["1"])))))))

  (testing "a local around the handler is a capture in any step"
    (is (= #{"uid"} (uncaptured '(defn h [uid] (weave/handler [] (-> {} (assoc :k uid)))))))
    (is (= #{"fmt"} (uncaptured '(defn h [fmt] (weave/handler [] (-> "1" str/trim fmt))))))
    (is (= #{"admin?"} (uncaptured '(defn h [admin?]
                                      (weave/handler [] (cond-> {} admin? (assoc :a 1)))))))
    (is (= #{"parse-long"} (uncaptured '(defn h [parse-long]
                                          (weave/handler [] (some-> "1" parse-long)))))))

  (testing "as-> binds its name over the forms"
    (is (= #{} (uncaptured '(defn h [] (weave/handler [] (as-> 1 $ (inc $) (str $)))))))
    (is (= #{"uid"} (uncaptured '(defn h [uid] (weave/handler [] (as-> uid $ (str $)))))))
    (is (= #{"uid"} (uncaptured '(defn h [uid] (weave/handler [] (as-> 1 $ (+ $ uid)))))))))

(deftest test-namespace-vars
  (testing "a top-level var of the namespace is not a capture"
    (is (= #{} (uncaptured '(def projection [:db/id])
                           '(defn- load! [x] x)
                           '(defn h [] (weave/handler [] (-> 1 load!)))
                           '(defn h [] (weave/handler [] (mapv load! projection)))))))

  (testing "a local shadowing a top-level var is a capture"
    (is (= #{"projection"} (uncaptured '(def projection [:db/id])
                                       '(defn h [projection]
                                          (weave/handler [] (str projection))))))
    (is (= #{"load!"} (uncaptured '(defn- load! [x] x)
                                  '(defn h [load!] (weave/handler [] (-> 1 load!))))))
    (is (= #{"projection"} (uncaptured '(def projection [:db/id])
                                       '(defn h []
                                          (let [projection [:user/email]]
                                            (weave/handler [] (str projection)))))))))

(deftest test-destructuring
  (testing "every destructured name is bound in the body"
    (is (= #{} (uncaptured
                '(defn h [m] (weave/handler [m] (let [{sold :plan} m] sold)))
                '(defn h [m] (weave/handler [m] (let [{:keys [a :b c/d]} m] [a b d])))
                '(defn h [m] (weave/handler [m] (let [{:user/keys [id]} m] id)))
                '(defn h [m] (weave/handler [m] (let [{:strs [s] :syms [y]} m] [s y])))
                '(defn h [m] (weave/handler [m] (let [{:keys [e] :as all :or {e 1}} m] [all e])))
                '(defn h [m] (weave/handler [m] (let [{[p q] :pair {:keys [z]} :in} m] [p q z])))
                '(defn h [m] (weave/handler [m] (let [[f [g] & more :as all] m] [f g more all])))
                '(defn h [m] (weave/handler [m] (when-let [{n :n} m] n)))))))

  (testing "a pattern binds only its own names"
    (is (= #{"plan"} (uncaptured
                      '(defn h [plan m] (weave/handler [m] (let [{sold :plan} m] [sold plan]))))))
    (is (= #{"b"} (uncaptured
                   '(defn h [b m] (weave/handler [m] (let [{:keys [a]} m] [a b]))))))
    (is (= #{"q"} (uncaptured
                   '(defn h [q m] (weave/handler [m] (let [{[p] :pair :as all} m] [p q all]))))))))

(deftest test-missing-capture-vector
  (is (= [:weave/missing-capture-vector]
         (->> (findings '(defn h [] (weave/handler (str 1))))
              (map :type)
              (filter #{:weave/missing-capture-vector :weave/uncaptured-symbol})))))
