(ns weave.squint
  (:refer-clojure :exclude [compile])
  (:require
   [clojure.string :as str]
   [clojure.walk :as clojure.walk]
   [squint.compiler :as squint]
   [backtick :as b]))

(defn js*
  ([form]
   (js* form {}))
  ([form options]
   (squint.compiler/compile-string
    (str form)
    (merge {:elide-imports true
            :elide-exports true
            :top-level     false
            :context       :expr
            :core-alias    "squint.core"}
           options))))

(defn remove-namespaces [form]
  (clojure.walk/postwalk
   (fn [x]
     (if (and (symbol? x) (namespace x))
       (symbol (name x))
       x))
   form))

(defn compile
  ([forms]
   (compile forms {}))
  ([forms options]
   (->> (map remove-namespaces forms)
        (map #(js* % options))
        (str/join "; "))))

(defmacro clj->js [& args]
  (let [[options forms] (if (and (map? (first args)) (next args))
                          [(first args) (rest args)]
                          [{} args])]
    `(-> (b/template ~forms)
         (compile ~options))))
