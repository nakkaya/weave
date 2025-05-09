;; Modified from Ramblurr/datastar-expressions
;; https://github.com/Ramblurr/datastar-expressions/blob/main/src/starfederation/datastar/clojure/expressions/internal.clj
;;
(ns weave.squint
  (:refer-clojure :exclude [compile])
  (:require
   [clojure.string :as str]
   [clojure.walk :as clojure.walk]
   [squint.compiler :as squint]))

(defn bool-expr [e]
  (vary-meta e assoc :tag 'boolean))

(defn expr-println
  ([_ _ & exprs]
   (let [js (str/join "," (repeat (count exprs) "(~{})"))]
     (concat (list 'js* (str "console.log(" js ")")) exprs))))

(defn expr-do
  ([_ _] nil)
  ([_ _ & exprs]
   (let [js (str/join ", " (repeat (count exprs) "(~{})"))]
     (concat (list 'js* js) exprs))))

(defn expr-if
  [_ _ test & body]
  (list 'if (bool-expr test) (first body) (second body)))

(defn expr-not
  [_ _ x]
  (let [js "(!(~{}))"]
    (bool-expr
     (concat (list 'js* js) (list x)))))

(defn expr-when
  [_ _ test & body]
  (list 'if (bool-expr test) (cons 'expr/do body)))

(defn expr-or
  ([_ _] nil)
  ([_ _ x] x)
  ([_ _ x & next]
   (let [js (str/join " || " (repeat (inc (count next)) "(~{})"))]
     (bool-expr
      (concat (list 'js* js) (cons x next))))))

(defn expr-raw
  ([_ _]
   (list 'js* ""))
  ([_ _ x]
   (let [js (str/join (repeat (count x) "~{}"))]
     (concat (list 'js* js) x))))

(defn expr-and
  ([_ _]
   true)
  ([_ _ x]
   x)
  ([_ _ x & next]
   (let [js (str/join " && " (repeat (inc (count next)) "(~{})"))]
     (bool-expr
      (concat (list 'js* js) (cons x next))))))

(defn replace-deref
  "Post-processes compiled JavaScript to convert squint_core.deref(fn) to @fn"
  [js-string]
  (str/replace js-string
               #"squint_core\.deref\(([a-zA-Z_$][a-zA-Z0-9_$]*)\)\s*\("
               "@$1("))

;; This is how we override squint's special forms (which aren't extensible)
;; We walk the forms before compiling, and replace the special forms with
;; our own macros that compile to the JS that we want.
;; Mainly we want to avoid special forms that rely on the squint core lib
(def macro-replacements {'and      'expr/and
                         'or       'expr/or
                         'if       'expr/if
                         'not      'expr/not
                         'println  'expr/println
                         'when     'expr/when
                         'expr/raw 'expr/raw})

(def compiler-macro-options {'expr {'and         expr-and
                                    'or          expr-or
                                    'when        expr-when
                                    'do          expr-do
                                    'if          expr-if
                                    'not         expr-not
                                    'println     expr-println
                                    'raw         expr-raw}})

(defn js*
  ([form]
   (js* form {}))
  ([form options]
   (->
    (squint.compiler/compile-string
     (str form)
     (merge {:elide-imports true
             :elide-exports true
             :top-level     false
             :context       :expr
             :macros        compiler-macro-options}
            options))
    (replace-deref)
    (str/replace #"\n" " ")
    (str/trim))))

(defn process-macros [form]
  (clojure.walk/postwalk
   (fn [node]
     (if (and (list? node)
              (contains? macro-replacements (first node)))
       (cons (get macro-replacements (first node)) (rest node))
       node)) form))

(defn compile
  ([forms]
   (compile forms {}))
  ([forms options]
   (->> (map process-macros forms)
        (map #(js* % options))
        (str/join "; "))))

(defmacro clj->js [& args]
  (let [[options forms] (if (and (map? (first args)) (next args))
                          [(first args) (rest args)]
                          [{} args])]
    (str (weave.squint/compile forms options) ";")))

(comment
  (compile '((println "Hello World!")))

  (compile '((when (> x 100) 1)))

  (compile '((println "Hello World!")
             (println "Hello World!")))

  (compile  '((set! (.-config js/tailwind) {:darkMode "class"})))

  ;;
  )
