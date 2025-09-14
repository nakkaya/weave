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

(defn- form-type
  "Get the type symbol of a list form, or nil if not applicable"
  [node]
  (when (api/list-node? node)
    (let [first-child (first (:children node))]
      (when (api/token-node? first-child)
        (api/sexpr first-child)))))

(defn- vector->symbols
  "Extract all symbols from a vector node"
  [vec-node]
  (when (api/vector-node? vec-node)
    (->> (:children vec-node)
         (keep (fn [node]
                 (when (api/token-node? node)
                   (let [sym (api/sexpr node)]
                     (when (symbol? sym) sym)))))
         set)))

(defn- extract-destructured-symbols
  "Extract all symbols from a destructuring pattern"
  [pattern]
  (cond
    ;; Simple symbol binding
    (api/token-node? pattern)
    (let [sym (api/sexpr pattern)]
      (if (and (symbol? sym) (not= '_ sym) (not= '& sym))
        #{sym}
        #{}))

    ;; Vector destructuring [a b c] or [a [b c]]
    (api/vector-node? pattern)
    (reduce into #{} (map extract-destructured-symbols (:children pattern)))

    ;; Map destructuring {:keys [a b c]} or {x :x}
    (api/map-node? pattern)
    (let [children (:children pattern)
          pairs (partition 2 children)]
      (reduce (fn [syms [k v]]
                (cond
                  ;; {:keys [foo bar]}
                  (and (api/token-node? k)
                       (= :keys (api/sexpr k))
                       (api/vector-node? v))
                  (->> (:children v)
                       (map api/sexpr)
                       (filter symbol?)
                       (into syms))

                  ;; {:strs [...]} or {:syms [...]}
                  (and (api/token-node? k)
                       (#{:strs :syms} (api/sexpr k))
                       (api/vector-node? v))
                  (->> (:children v)
                       (map api/sexpr)
                       (filter symbol?)
                       (into syms))

                  ;; {foo :foo} or {bar "bar"} - k is the bound symbol
                  (api/token-node? k)
                  (let [sym (api/sexpr k)]
                    (if (and (symbol? sym) (not= '_ sym))
                      (conj syms sym)
                      syms))

                  ;; Nested destructuring in the key position
                  :else
                  (into syms (extract-destructured-symbols k))))
              #{}
              pairs))

    ;; Other nodes
    :else #{}))

(defn- extract-let-bindings
  "Extract binding symbols from a let-style binding vector.
   Simple approach focusing on the most common cases."
  [bindings-vec]
  (when (api/vector-node? bindings-vec)
    ;; For let bindings: [pattern1 value1 pattern2 value2 ...]
    ;; We want to extract symbols from patterns (positions 0, 2, 4, ...)
    (let [patterns (->> (:children bindings-vec)
                        (take-nth 2))]  ; Take every pattern, skip values
      (reduce (fn [syms pattern]
                (cond
                  ;; Simple symbol: x
                  (api/token-node? pattern)
                  (let [sym (api/sexpr pattern)]
                    (if (symbol? sym)
                      (conj syms sym)
                      syms))

                  ;; Map destructuring: {:keys [a b c]}
                  (api/map-node? pattern)
                  ;; Simple approach: look for any vector inside the map that contains symbols
                  ;; This is a fallback that should work for {:keys [transaction-id]}
                  (->> (:children pattern)
                       (filter api/vector-node?)  ; Find all vectors in the map
                       (mapcat (fn [vec-node]      ; Extract symbols from each vector
                                 (->> (:children vec-node)
                                      (map api/sexpr)
                                      (filter symbol?))))
                       (into syms))

                  ;; Vector destructuring: [a b c]
                  (api/vector-node? pattern)
                  (->> (:children pattern)
                       (map api/sexpr)
                       (filter symbol?)
                       (into syms))

                  ;; Other patterns - skip for now
                  :else syms))
              #{}
              patterns))))

(defn- extract-catch-binding
  "Extract the exception binding symbol from a catch form"
  [catch-node]
  (when (= 'catch (form-type catch-node))
    (let [children (:children catch-node)]
      ;; catch form: (catch ExceptionType binding-symbol body...)
      ;; We want the third element (index 2) which is the binding symbol
      (when (>= (count children) 3)
        (let [binding-node (nth children 2)]
          (when (api/token-node? binding-node)
            (let [sym (api/sexpr binding-node)]
              (if (and (symbol? sym) (not= '_ sym))
                #{sym}
                #{}))))))))

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

    ;; Try form - handle catch clauses specially
    (= 'try (form-type node))
    (let [children (:children node)
          [_try & body-and-catches] children]
      (mapcat (fn [child]
                (if (= 'catch (form-type child))
                  ;; For catch clauses, add the exception binding to local scope
                  (let [catch-binding (or (extract-catch-binding child) #{})
                        extended-scope (into local-bindings catch-binding)
                        [_catch _exception-type _binding & catch-body] (:children child)]
                    (mapcat #(find-uncaptured-symbols % captured-syms extended-scope) catch-body))
                  ;; For non-catch children (try body, finally), use normal processing
                  (find-uncaptured-symbols child captured-syms local-bindings)))
              body-and-catches))

    ;; Binding form - extract new bindings and continue with extended scope
    (contains? binding-forms (form-type node))
    (let [[_binding-form bindings-vec & body] (:children node)]
      (if (api/vector-node? bindings-vec)
        ;; Process bindings sequentially, building scope as we go
        (let [binding-pairs (->> (:children bindings-vec)
                                 (partition 2))  ; [pattern value] pairs
              ;; Analyze each binding value with progressively extended scope
              [final-scope binding-violations]
              (reduce (fn [[current-scope violations] [pattern value]]
                        ;; First analyze the value with current scope
                        (let [value-violations (find-uncaptured-symbols value captured-syms current-scope)
                              ;; Then extract bindings from pattern using the working function
                              pattern-bindings (extract-let-bindings
                                               (api/vector-node [pattern (api/token-node 'dummy)]))
                              new-scope (into current-scope (or pattern-bindings #{}))]
                          [new-scope (concat violations value-violations)]))
                      [local-bindings []]
                      binding-pairs)]
          (concat binding-violations
                  (mapcat #(find-uncaptured-symbols % captured-syms final-scope) body)))
        ;; Fallback if not a vector (shouldn't happen)
        (mapcat #(find-uncaptured-symbols % captured-syms local-bindings) body)))

    ;; Fn form - handle function parameter binding
    (= 'fn (form-type node))
    (let [children (:children node)
          [_fn params & body] children]
      (if (api/vector-node? params)
        ;; Extract parameter symbols and add to local scope
        (let [param-symbols (vector->symbols params)
              extended-scope (into local-bindings (or param-symbols #{}))]
          (mapcat #(find-uncaptured-symbols % captured-syms extended-scope) body))
        ;; Handle multi-arity fn - more complex, skip for now
        (mapcat #(find-uncaptured-symbols % captured-syms local-bindings) (rest children))))

    ;; List node - recurse into children, but skip function position
    (api/list-node? node)
    (let [children (:children node)]
      (if (seq children)
        ;; Skip the first child (function position) and only analyze arguments
        (mapcat #(find-uncaptured-symbols % captured-syms local-bindings)
                (rest children))
        []))

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
