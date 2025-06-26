# Push and Broadcast

Weave provides several functions for updating the UI and communicating
with the client browser. These functions fall into two main
categories:

- **Push functions**: Send updates to the specific browser tab/window
  that triggered the current handler
- **Broadcast functions**: Send updates to all browser tabs/windows
  that share the same session ID

## HTML Updates

### push-html!

Pushes HTML updates to the specific browser tab/window that triggered
the handler.

```clojure
(weave/push-html! [:div#message "Operation completed successfully!"])
```

### broadcast-html!

Pushes HTML updates to all browser tabs/windows that share the same
session ID.

```clojure
(weave/broadcast-html! [:div#notification "New message received!"])
```

## Navigation

### push-path!

Changes the URL hash for the specific browser tab/window that
triggered the handler. Optionally renders a new view.

```clojure
;; Simple navigation
(weave/push-path! "/dashboard")

;; Navigation with view update
(weave/push-path! "/profile" 
  (fn [] 
    [:div#profile 
     [:h1 "User Profile"]
     [:p "Profile content here"]]))
```

### broadcast-path!

Changes the URL hash for all browser tabs/windows that share the same
session ID. Optionally renders a new view.

```clojure
;; Simple navigation for all tabs
(weave/broadcast-path! "/logout")

;; Navigation with view update for all tabs
(weave/broadcast-path! "/maintenance" 
  (fn [] 
    [:div.alert 
     [:h1 "Maintenance Mode"]
     [:p "The system is currently undergoing maintenance."]]))
```

## JavaScript Execution

### push-script!

Sends JavaScript to the specific browser tab/window for execution.

```clojure
(weave/push-script! "console.log('Handler executed');")
```

### push-reload!

Sends a reload command to the specific browser tab/window.

```clojure
(weave/push-reload!)
```

### broadcast-script!

Sends JavaScript to all browser tabs/windows for execution.

```clojure
(weave/broadcast-script! "localStorage.clear();")
```

## Data and State

### push-signal!

Sends updated signal values to the specific browser tab/window.

```clojure
(weave/push-signal! {:user {:name "John" :role "admin"}})
```

Signals are client-side state values that can be accessed in the
browser using [Datastar](https://data-star.dev/).  They can be used
for reactive UI updates, storing user preferences, or maintaining
application state. Signals are stored in the browser and can be
accessed in HTML attributes using the `data-signals-` prefix:

```clojure
[:div {:data-signals-username "''"}
 "Welcome, "
 [:span {:data-text "$username"}]]

;; Later update the signal
(weave/push-signal! {:username "John"})
```

## Common Patterns

### Form Submission Handler

```clojure
[:form
 {:data-on-submit
  (weave/handler ^{:type :form} []
   (let [form-data (:params weave/*request*)]
     (save-data! form-data)
     (weave/push-html! 
      [:div#status.text-green-500 "Data saved successfully!"])))}
 [:input {:type "text" :name "username"}]
 [:button {:type "submit"} "Save"]]
```

### Toggling UI Elements

```clojure
(let [panel-visible (atom false)]
  [:button
   {:data-on-click
    (weave/handler [panel-visible]
     (weave/push-html!
      [:div#panel
       {:class (if @panel-visible "hidden" "block")}
       "Panel content"])
     (swap! panel-visible not))}
   "Toggle Panel"])
```

### Broadcasting Notifications

```clojure
(defn notify-all-users [message]
  (weave/broadcast-html!
   [:div#notification.fixed.top-0.right-0.m-4.p-4.bg-blue-500.text-white.rounded
    {:data-on-load
     (weave/handler []
      (weave/push-script!
       "setTimeout(() => document.getElementById('notification').remove(), 5000)"))}
    message]))
```
