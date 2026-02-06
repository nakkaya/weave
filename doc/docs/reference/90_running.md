# Running Applications

The `weave.core/run` function is the main entry point for starting a
Weave application. It creates and starts an integrated web
server with all the necessary components for your application.

## Basic Usage

```clojure
(ns my-app.core
  (:require [weave.core :as weave]))

(defn my-view []
  [:div
   [:h1 "Hello, Weave!"]
   [:p "Welcome to my application"]])

(defn -main []
  (weave/run my-view {}))
```

## Function Signature

```clojure
(run view options)
```

**Parameters:**

- `view` - A function that returns the Hiccup view to render
- `options` - A map of server configuration options

**Returns:**

- An integrant system that can be halted with `(integrant.core/halt! system)`

## Configuration Options

### Base Path (`:base-path`)

By default, Weave serves the application shell from the root path (`/`).
Use `:base-path` to serve the app from a subfolder.

```clojure
(weave/run my-view
  {:base-path "/app"})
```

With this configuration:

- `/` - Returns 404 (or can be handled by a custom handler or static resource)
- `/app` - Serves the Weave application
- `/app/#/dashboard` - Example client-side route within the Weave app

Note: Only the app route is affected by `:base-path`. Static resources,
`/app-loader`, `/h/*` handlers, `/health`, and other routes remain at root.

**Example with landing page:**

```clojure
(require '[compojure.core :refer [GET]])

(weave/run my-view
  {:base-path "/app"
   :handlers [(GET "/" []
               {:status 200
                :headers {"Content-Type" "text/html"}
                :body "<html><body><a href='/app'>Launch App</a></body></html>"})]})
```

### HTTP Server Options (`:http-kit`)

```clojure
(weave/run my-view
  {:http-kit {:bind "127.0.0.1"  ; IP address to bind to (default: "0.0.0.0")
              :port 3000}})      ; HTTP server port (default: 8080)
```

### nREPL Server Options (`:nrepl`)

```clojure
(weave/run my-view
  {:nrepl {:bind "127.0.0.1"     ; IP address to bind to (default: "0.0.0.0")
           :port 7888}})         ; nREPL server port (default: 8888)
```

### Server-Sent Events Options (`:sse`)

```clojure
(weave/run my-view
  {:sse {:enabled true          ; Whether to enable SSE (default: true)
         :keep-alive false}})   ; Keep SSE connections alive when tab is hidden (default: false)
```

### Page Configuration

```clojure
(weave/run my-view
  {:title "My Application"                             ; Page title
   :head [:meta {:name "description" 
                 :content "My app description"}]       ; Additional HTML for head section
   :view-port "width=device-width, initial-scale=1"})  ; Viewport meta tag
```

### Security Options

```clojure
(weave/run my-view
  {:csrf-secret "my-secret-key"        ; Secret for CSRF token generation
   :jwt-secret "my-jwt-secret"         ; Secret for JWT token generation/validation
   :handler-options {:auth-required? true}}) ; Require authentication for all handlers by default
```

### Custom Routes and Middleware

```clojure
(require '[compojure.core :refer [GET POST]])

(weave/run my-view
  {:handlers [(GET "/api/status" [] {:status 200 :body "OK"})
              (POST "/api/data" req (handle-data req))]
   :middleware [my-logging-middleware
                my-cors-middleware]})
```

### Progressive Web App (PWA) Configuration

```clojure
(weave/run my-view
  {:icon "icons/app-icon.png"          ; Path to icon file in classpath (PNG format)
   :pwa {:name "My Application"        ; Application name (defaults to :title)
         :short-name "MyApp"           ; Application shortname (defaults to :name)
         :description "A great app"    ; Application description
         :display "standalone"         ; Display mode (default: "standalone")
         :background-color "#f2f2f2"   ; Background color (default: "#f2f2f2")
         :theme-color "#ffffff"        ; Theme color (default: "#ffffff")
         :start-url "/"}})             ; Start URL when launched (default: "/")
```

## Complete Example

```clojure
(ns my-app.core
  (:require [weave.core :as weave]
            [compojure.core :refer [GET]]
            [integrant.core :as ig]))

(defn my-view []
  [:div
   [:h1 "My Weave Application"]
   [:button {:data-on-click (weave/handler []
                              (weave/push-html! "body" [:p "Button clicked!"]))}
    "Click me"]])

(def app
  (weave/run my-view
    {:title "My App"
     :http-kit {:port 3000}
     :nrepl {:port 7888}
     :sse {:enabled true
           :keep-alive true}
     :handlers [(GET "/healthz" [] {:status 200 :body "healthy"})]
     :icon "icons/app.png"
     :pwa {:name "My Application"
           :description "A sample Weave application"}}))

;; To stop the server:
;; (ig/halt! app)
```

## Health Check Endpoint

Weave automatically provides a health check endpoint at `/health` that returns:

```json
{"status":"ok"}
```

This can be used for container orchestration health checks and load balancer monitoring.
