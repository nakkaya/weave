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

## Signal Naming Conventions

Weave automatically converts signal names between Clojure's kebab-case
convention and JavaScript's camelCase convention:

### Clojure to JavaScript (Outgoing Signals)

When you use `push-signal!` or similar functions, signal names are
converted from kebab-case keywords to camelCase:

```clojure
;; In your handler
(weave/push-signal! {:user-name "John"
                     :is-active true
                     :item-count 42})

;; JavaScript receives:
;; {userName: "John", isActive: true, itemCount: 42}
```

### JavaScript to Clojure (Incoming Signals)

When signals are sent from the browser (via Datastar), they are
converted from camelCase to kebab-case keywords:

```html
<!-- In your HTML -->
<div data-signals-userName="John"
     data-signals-isActive="true"
     data-signals-itemCount="42">
```

```clojure
;; In your handler, signals are accessible as:
(let [{:keys [user-name is-active item-count]} weave/*signals*]
  ;; user-name = "John"
  ;; is-active = true
  ;; item-count = 42
  )
```

## Examples

## With Variables

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

## With Signals

Signals provide a powerful alternative to variable capture for
managing dynamic state. Instead of capturing variables in closures,
you can store state as signals in the browser and access them via
`weave/*signals*`.

```clojure
(defn click-count-view []
  [::c/view#app
   [::c/center-hv
    [::c/card
     [:div.text-center.text-6xl.font-bold.mb-6.text-blue-600
      {:data-signals-count "0"
       :data-text "$count"}]
     [::c/button
      {:size :xl
       :variant :primary
       :data-on-click (weave/handler []
                        (let [count (or (:count weave/*signals*) 0)]
                          (weave/push-signal! {:count (inc count)})))}
      "Increment Count"]]]])
```

In this example:

- `data-signals-click-count="0"` initializes the signal with value 0
- `data-text="$click"` displays the signal value reactively
- The handler reads the current value from `weave/*signals*` and
  updates it with `push-signal!`

## With `:data-call-with-*`

The `:data-call-with-*` attribute is a Weave-specific feature that
provides way to pass arguments to handlers while avoiding variable
capture.

```clojure
(defn action-buttons-view []
  [:div#app
   [:div#result "No action performed yet"]
   ;; Define a single shared handler
   (let [handle-action (weave/handler []
                         (let [{:keys [action item-id]} weave/*signals*]
                           (weave/push-html!
                             [:div#result (str "Action: " action ", Item: " item-id)])))]
     [:div.button-group
      [::c/button
       {:data-call-with-action "edit"
        :data-call-with-item-id "123"
        :data-on-click handle-action}
       "Edit"]
      [::c/button
       {:data-call-with-action "delete"
        :data-call-with-item-id "123"
        :data-on-click handle-action}
       "Delete"]])])
```

### Scoping Rules

```clojure
;; Example showing inheritance
[:div {:data-call-with-action "noop"}  ; Parent element
 [:button {:data-call-with-action "edit"  ; Child element
           :data-call-with-id "123"
           :data-on-click handler}
  "Edit User"]]
;; Result: signals will be {:action "edit", :id "123"}
```

## Problem: Route Explosion with Closures

When using variable capture, each unique combination of captured
variables creates a separate route. This becomes problematic with
things like table rows where each action becomes a new route and
nothing shared:

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

### Solution: Shared Handlers with `:data-call-with-*`

```clojure
;; GOOD: Only 1 handler total, regardless of number of users
(defn user-table [users]
  (let [handle-action (weave/handler []
                        (let [{:keys [user-id action]} weave/*signals*]
                          (case action
                            "delete" (delete-user! user-id)
                            "promote" (promote-user! user-id))
                          (weave/push-html! (user-table (get-updated-users)))))]
    [:table
     (for [user users]
       [:tr
        [:td (:name user)]
        [:td
         [::c/button
          {:data-call-with-user-id (:id user)
           :data-call-with-action "delete"
           :data-on-click handle-action}
          "Delete"]
         [::c/button
          {:data-call-with-user-id (:id user)
           :data-call-with-action "promote"
           :data-on-click handle-action}
          "Promote"]]])]))

;; Only 1 handler registered total - shared across all rows and actions!
```
