# Handlers

Handlers are server-side functions that process client-side
events. They are a core part of Weave's reactivity model.

## How Handlers Work

- When you define a handler using the `weave/handler` macro, Weave:
    - Generates a unique route path based on code structure and
      captured variables. A hash is calculated for each handler,
      handlers with the same hash share the same unique route
    - Registers the handler function with that route
    - Returns client-side [Datastar](https://data-star.dev/)
      expression that will invoke this route when triggered

- When a client-side event occurs (like a button click):
    - The browser sends a request to the unique route
    - Weave executes your handler function on the server
    - Your handler can update the DOM, execute scripts, etc.

## Handler Syntax

```clojure
(weave/handler ^{options} [arguments]
  ;; handler body
  )
```

### Variable Capture

Any variables accessed within the handler body must be explicitly
captured in the first argument vector. This is required for proper
caching and ensures handlers work correctly with closures:

```clojure
(let [user-name "John"
      counter (atom 0)]
  (weave/handler [user-name counter]
    (weave/push-html! [:div "Hello " user-name "! Count: " @counter])))
```

## Handler Options

Options are provided as metadata (optional):

- `:auth-required?` - Whether authentication is required (defaults to
  the value of `*secure-handlers*`)
- `:type` - Request content type (use `:form` for form submissions)
- `:selector` - CSS selector for the form to submit (e.g. `"#myform"`)

## Example

```clojure
{:data-on-click
 (weave/handler []
  ;; This code runs on the server when the button is clicked
  (weave/push-html! [:div#message "Button clicked!"]))}
```

## Example with Variables

```clojure
(let [message "Hello from server!"]
  {:data-on-click
   (weave/handler [message]
    (weave/push-html! [:div#message message]))})
```

When this handler is registered, Weave:

 - Creates a unique route based on the handler code and captured
   variables.
 - Sets up a POST endpoint for that route
 - Returns client-side code that will POST to that route when the
   click event occurs

## Handlers with Signals

Signals provide a powerful alternative to variable capture for
managing dynamic state. Instead of capturing variables in closures,
you can store state as signals in the browser and access them via
`weave/*signals*`.

### Basic Signal Example

```clojure
(defn click-count-view []
  [::c/view#app
   [::c/center-hv
    [::c/card
     [:div.text-center.text-6xl.font-bold.mb-6.text-blue-600
      {:data-signals-click-count "0"
       :data-text "$click_count"}]
     [::c/button
      {:size :xl
       :variant :primary
       :data-on-click (weave/handler []
                        (let [current-count (or (:click-count weave/*signals*) 0)]
                          (weave/push-signal! {:click-count (inc current-count)})))}
      "Increment Count"]]]])
```

In this example:

- `data-signals-click-count="0"` initializes the signal with value 0
- `data-text="$click_count"` displays the signal value reactively
- The handler reads the current value from `weave/*signals*` and updates it with `push-signal!`

### Why Signals Are Better Than Closures for Some Use Cases

#### Problem: Route Explosion with Closures

When using variable capture, each unique combination of captured
variables creates a separate route. This becomes problematic with
dynamic data like table rows:

```clojure
;; BAD: Creates separate handler for each row × action combination
(defn user-table-bad [users]
  [:table
   (for [user users]
     [:tr
      [:td (:name user)]
      [:td
       [::c/button
        {:data-on-click (weave/handler [user] ; Captures user - creates unique route!
                          (delete-user! (:id user))
                          (weave/push-html! (user-table-bad (get-updated-users))))}
        "Delete"]
       [::c/button
        {:data-on-click (weave/handler [user] ; Another unique route per user!
                          (promote-user! (:id user))
                          (weave/push-html! (user-table-bad (get-updated-users))))}
        "Promote"]]])])

;; With 100 users × 2 actions = 200 different routes registered!
```

#### Solution: Shared Handlers with Signals

```clojure
;; GOOD: Only 2 handlers total, regardless of number of users
(defn user-table-good [users]
  [:table
   (for [user users]
     [:tr
      [:td (:name user)]
      [:td
       [::c/button
        {:data-signals-user-id (:id user)         ; Store user ID as signal
         :data-on-click (weave/handler []         ; No variable capture!
                          (let [user-id (:user-id weave/*signals*)]
                            (delete-user! user-id)
                            (weave/push-html! (user-table-good (get-updated-users)))))}
        "Delete"]
       [::c/button
        {:data-signals-user-id (:id user)         ; Same user ID signal
         :data-on-click (weave/handler []         ; Same handler pattern
                          (let [user-id (:user-id weave/*signals*)]
                            (promote-user! user-id)
                            (weave/push-html! (user-table-good (get-updated-users)))))}
        "Promote"]]])])

;; Only 2 handlers registered total - shared across all rows!
```
