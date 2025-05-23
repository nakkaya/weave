# Custom Routes

Weave allows you to define custom routes to handle requests outside of
the standard Weave event system. This is useful for creating API
endpoints, handling file uploads, or integrating with external
services.

## Adding Custom Routes

Custom routes are added through the `:handlers` option when starting
your Weave application. These routes use
[Compojure](https://github.com/weavejester/compojure) syntax and are
merged with Weave's internal routes.

```clojure
(weave/run view-fn
  {:handlers [(GET "/api/data" [] (api-handler))
              (POST "/api/upload" [] (upload-handler))
              (GET "/api/download/:id" [id] (download-handler id))]})
```

## Route Handlers

Route handlers are regular Ring handler functions that take a request
map and return a response map:

```clojure
(defn api-handler [req]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body "{\"message\":\"Hello from API\"}"})
```

## Authentication for Custom Routes

Custom routes don't automatically use Weave's authentication
system. To protect your routes, you need to manually check the request
for authentication information,

```clojure
(defn authenticated-api [req]
  (if (:identity req)
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body "{\"data\":\"Protected data\"}"}
    {:status 403
     :headers {"Content-Type" "application/json"}
     :body "{\"error\":\"Unauthorized\"}"}))
```

## Accessing Weave Context

Custom routes have access to the same request context as Weave
handlers, including session information:

```clojure
(defn session-info [req]
  (let [session-id (session/get-sid req)]
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (str "{\"session\":\"" session-id "\"}")}))
```
