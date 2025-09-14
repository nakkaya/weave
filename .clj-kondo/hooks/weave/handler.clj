(ns hooks.weave.handler
  (:require [clj-kondo.hooks-api :as api]
            [clojure.string :as str]))

(def ^:private clojure-builtins
  #{'let 'fn 'defn 'if 'when 'cond 'case
    'do 'try 'catch 'finally 'throw
    'quote 'unquote 'unquote-splicing
    'and 'or 'not 'nil 'true 'false
    'swap! 'reset! 'deref 'atom
    'println 'print 'prn 'pr-str
    'str 'inc 'dec 'get 'assoc 'dissoc
    'first 'rest 'count 'map 'filter
    'reduce 'into 'merge 'conj 'update
    'doseq 'for 'name 'keyword})

(def ^:private binding-forms
  #{'let 'for 'doseq 'dotimes 'when-let 'if-let 'binding})

(defn- token->symbol
  "Extract the symbol value from a token node"
  [node]
  (when (api/token-node? node)
    (api/sexpr node)))

(defn- vector->symbols
  "Extract all symbols from a vector node"
  [vec-node]
  (when (api/vector-node? vec-node)
    (->> (:children vec-node)
         (map token->symbol)
         (filter symbol?)
         set)))

(defn- extract-let-bindings
  "Extract binding symbols from a let-style binding vector."
  [bindings-vec]
  (when (api/vector-node? bindings-vec)
    (->> (:children bindings-vec)
         (take-nth 2)
         (map api/sexpr)
         (filter symbol?)
         set)))

(defn- binding-form?
  "Check if a node represents a form that introduces local bindings"
  [node]
  (and (api/list-node? node)
       (let [first-child (first (:children node))]
         (and (api/token-node? first-child)
              (contains? binding-forms (api/sexpr first-child))))))

(defn- skip-symbol?
  "Check if a symbol should be skipped from uncaptured analysis"
  [sym]
  (or (not (symbol? sym))
      (namespace sym)                      ; Namespaced symbols like foo/bar
      (str/starts-with? (str sym) "core/") ; core/* dynamic vars
      (contains? clojure-builtins sym)))   ; Built-in functions

(defn- find-uncaptured-symbols
  "Recursively find symbols used in code that aren't captured or locally bound"
  [node captured-syms local-bindings]
  (cond
    ;; Token node - check if it's an uncaptured symbol
    (api/token-node? node)
    (let [sym (api/sexpr node)]
      (when (and (symbol? sym)
                 (not (skip-symbol? sym))
                 (not (contains? captured-syms sym))
                 (not (contains? local-bindings sym)))
        [{:symbol sym :node node}]))

    ;; Binding form - extract new bindings and continue with extended scope
    (binding-form? node)
    (let [[_binding-form bindings-vec & body] (:children node)
          new-bindings (or (extract-let-bindings bindings-vec) #{})
          extended-scope (into local-bindings new-bindings)]
      (mapcat #(find-uncaptured-symbols % captured-syms extended-scope) body))

    ;; List node - recurse into children
    (api/list-node? node)
    (mapcat #(find-uncaptured-symbols % captured-syms local-bindings) 
            (:children node))

    ;; Vector node - recurse into children
    (api/vector-node? node)
    (mapcat #(find-uncaptured-symbols % captured-syms local-bindings) 
            (:children node))

    ;; Map node - recurse into children
    (api/map-node? node)
    (mapcat #(find-uncaptured-symbols % captured-syms local-bindings) 
            (:children node))

    ;; Default - nothing to analyze
    :else nil))

(defn- report-missing-capture-vector!
  "Report an error when handler is missing its capture vector"
  [node]
  (api/reg-finding!
    {:message "weave/handler requires a capture vector as first argument"
     :type :weave/missing-capture-vector
     :row (:row (meta node))
     :col (:col (meta node))}))

(defn- report-uncaptured-symbol!
  "Report a warning for an uncaptured symbol"
  [sym node]
  (api/reg-finding!
    {:message (format "Symbol '%s' should be captured in handler arguments vector" sym)
     :type :weave/uncaptured-symbol
     :row (:row (meta node))
     :col (:col (meta node))}))

(defn- analyze-handler-body
  "Analyze handler body for uncaptured symbols and report findings"
  [capture-vec body]
  (let [captured-syms (vector->symbols capture-vec)]
    (doseq [body-form body]
      (let [uncaptured (find-uncaptured-symbols body-form captured-syms #{})]
        (doseq [{:keys [symbol node]} uncaptured]
          (report-uncaptured-symbol! symbol node))))))

(defn- transform-to-fn
  "Transform handler to fn form to prevent unused binding warnings"
  [capture-vec body]
  (api/list-node
    [(api/token-node 'fn)
     capture-vec
     (api/list-node (cons (api/token-node 'do) body))]))

(defn weave-handler
  "Analyze weave/handler forms for proper variable capture.
   
   Checks that:
   1. Handler has a capture vector as first argument
   2. All external symbols used in body are captured
   3. Transforms to fn to prevent unused binding warnings"
  [{:keys [node]}]
  (let [[capture-vec & body] (rest (:children node))]
    
    ;; Check for missing capture vector
    (when-not (and capture-vec (api/vector-node? capture-vec))
      (report-missing-capture-vector! node))
    
    ;; Analyze body for uncaptured symbols
    (when (and capture-vec body)
      (analyze-handler-body capture-vec body))
    
    ;; Transform to fn form to prevent unused binding warnings
    (if (and capture-vec (api/vector-node? capture-vec) body)
      (transform-to-fn capture-vec body)
      node)))
