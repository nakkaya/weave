# JavaScript

Weave provides JavaScript interoperability through
[Squint](https://github.com/squint-cljs/squint), a lightweight
Clojure-to-JavaScript transpiler. This allows you to write Clojure
code that gets converted to JavaScript for client-side execution.

## How JavaScript Support Works

Weave uses Squint to:

1. Transpile Clojure code to JavaScript at runtime
2. Execute the generated JavaScript in the browser
3. Integrate with the DOM and browser APIs

This approach gives you the expressiveness of Clojure with direct
access to browser capabilities.

## Using JavaScript in Weave

There are several ways to use JavaScript in Weave:

### 1. Inline Scripts with `push-script!`

The simplest way to execute JavaScript is with `push-script!`:

```clojure
(weave/push-script!
  (squint/clj->js
    (js/alert "Hello from Clojure!")))
```

This transpiles the Clojure code to JavaScript and sends it to the
current browser tab for execution.

### 2. Broadcasting Scripts to All Sessions

To send JavaScript to all connected browser tabs for the current user:

```clojure
(weave/broadcast-script!
  (squint/clj->js
    (js/console.log "This appears in all tabs")))
```

### 3. Event Handlers with JavaScript

You can combine DOM updates with JavaScript execution:

```clojure
{:data-on-click
 (weave/handler []
  ;; Update the DOM
  (weave/push-html! [:div#status "Processing..."])
  
  ;; Execute JavaScript
  (weave/push-script!
   (squint/clj->js
    (let [result (js/fetch "/api/data")]
      (.then result #(.json %))
      (.then result #(js/console.log "Data received:" %))
      (.catch result #(js/console.error "Error:" %))))))}
```

### 4. DOM Manipulation

You can directly manipulate the DOM:

```clojure
(weave/push-script!
 (squint/clj->js
  (let [element (js/document.getElementById "counter")]
    (set! (.-textContent element) 
          (inc (js/parseInt (.-textContent element)))))))
```

## The `clj->js` Macro

The `clj->js` macro is the primary tool for JavaScript interoperability:

```clojure
(weave/push-script!
 (squint/clj->js
  (defn greet [name]
    (js/alert (str "Hello, " name "!")))
  
  (greet "World")))
```

This transpiles the Clojure code to JavaScript and executes it in the browser.

The `clj->js` macro accepts an optional map of options:

```clojure
(weave/push-script!
 (clj->js
  {:elide-imports true
   :elide-exports true
   :top-level false
   :context :expr
   :core-alias "squint.core"}

  (defn advanced-example []
    (js/console.log "Custom options"))

  (advanced-example)))
```

## Limitations and Differences from ClojureScript

While Squint provides excellent JavaScript interoperability, it's important to understand its limitations:

1. **Not Full ClojureScript**: Squint is a lightweight transpiler, not a complete ClojureScript implementation
2. **Different Data Structures**: Squint maps to JavaScript primitives
3. **Limited Standard Library**: Only a subset of Clojure's core functions are available
4. **No Advanced Optimizations**: Unlike ClojureScript, there's no advanced compilation or optimization
5. **Runtime Transpilation**: Code is transpiled at runtime, not ahead-of-time

## Example: Timer Application

Here's a complete example of a timer application using JavaScript interoperability:

```clojure
(defn timer-view []
  [:div.p-6
   [:h1#timer.text-4xl.font-bold "0"]
   [:div.flex.gap-2.mt-4
    [:button.bg-green-500.text-white.px-4.py-2.rounded
     {:data-on-click
      (weave/handler []
       (weave/push-script!
        (squint/clj->js
         (let [interval-id (js/setInterval
                            (fn []
                              (let [timer (js/document.getElementById "timer")
                                    current (js/parseInt (.-textContent timer))]
                                (set! (.-textContent timer) (inc current))))
                            1000)]
           (set! (.. js/window -weaveTimerId) interval-id)))))}
     "Start"]
    
    [:button.bg-red-500.text-white.px-4.py-2.rounded
     {:data-on-click
      (weave/handler []
       (weave/push-script!
        (squint/clj->js
         (when (.. js/window -weaveTimerId)
           (js/clearInterval (.. js/window -weaveTimerId))
           (set! (.. js/window -weaveTimerId) nil)))))}
     "Stop"]
    
    [:button.bg-blue-500.text-white.px-4.py-2.rounded
     {:data-on-click
      (weave/handler []
       (weave/push-script!
        (squint/clj->js
         (let [timer (js/document.getElementById "timer")]
           (set! (.-textContent timer) "0")))))}
     "Reset"]]])
```

This example demonstrates:

- Setting up a JavaScript interval timer
- Storing state in a browser window property
- Manipulating the DOM directly
- Cleaning up resources when stopping the timer
