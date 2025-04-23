(ns weave.core
  (:require
   [charred.api :as charred]
   [clojure.tools.logging :as log]
   [compojure.core :refer [GET POST routes]]
   [compojure.route :as route]
   [dev.onionpancakes.chassis.core :as c]
   [nrepl.server :as nrepl.server]
   [org.httpkit.server :as server]
   [reitit.core :as r]
   [ring.middleware.defaults :as def]
   [ring.middleware.gzip :refer [wrap-gzip]]
   [ring.util.response :as resp]
   [starfederation.datastar.clojure.adapter.http-kit :as hk-gen]
   [starfederation.datastar.clojure.api :as d*]
   [weave.session :as session]))

(defmethod resp/resource-data :resource
  [^java.net.URL url]
  ;; GraalVM resource scheme
  (let [resource     (.openConnection url)]
    {:content        (.getInputStream resource)
     :content-length (#'resp/connection-content-length resource)
     :last-modified  (#'resp/connection-last-modified resource)}))

(def ^:dynamic *session-id*
  "The current user's session ID, extracted from the session cookie.
   Available in handler functions and view rendering code."
  nil)

(def ^:dynamic *instance-id*
  "The current browser tab's unique instance ID.  Each browser
   tab/window gets a unique instance ID to track server-sent events
   connections."
  nil)

(def ^:dynamic *app-path*
  "The current application path from the URL hash.  This is used for
   client-side routing."
  nil)

(def ^:dynamic *request*
  "Current Ring request map.  Contains all HTTP request information
   including headers, parameters, and the authenticated user identity."
  nil)

(defmacro bind-vars
  "Bind the dynamic variables *session-id*, *instance-id*,
   *app-path*, and *request* to values extracted from the request map
   for the duration of body execution.

   This macro is used internally to ensure that handlers and views
   have access to the current request context through these dynamic
   variables."
  [req & body]
  `(let [session-id# (session/get-sid ~req)
         headers# (:headers ~req)
         csrf-token# (get headers# "x-csrf-token")
         instance-id# (get headers# "x-instance-id")
         app-path# (get headers# "x-app-path")]
     (binding [*session-id* session-id#
               *instance-id* instance-id#
               *app-path* app-path#
               *request* ~req]
       ~@body)))

(let [read-json
      (charred/parse-json-fn
       {:async? false :bufsize 1024 :key-fn keyword})]
  (defn get-signals
    "Extract and parse client-side signals from the request."
    [req]
    (-> req d*/get-signals read-json)))

(defn ->sse-response
  "Create a Server-Sent Events (SSE) response with the given options."
  [opts]
  (hk-gen/->sse-response *request* opts))

(defn- request-options
  "Generate a JavaScript object string with request options for datastar.

   Parameters:
     opts - A map of options:
            :type - The content type (:form for form data)
            :keep-alive - Whether to keep the connection alive when tab is hidden
            :selector - CSS selector for the form to submit (e.g. \"#myform\")

   Returns:
     A datastar string containing configured options."
  [opts]
  (let [headers (str "headers: {
                       'x-csrf-token': $app.csrf,
                       'x-instance-id': $app.instance,
                       'x-app-path': $app.path
                      }")
        type (when (or (= (opts :type) :form)
                       (= (opts :type) :sign-in))
               ", contentType: 'form'")
        keep-alive (when (opts :keep-alive)
                     ", openWhenHidden: true")
        selector (when (opts :selector)
                   (str ", selector: '" (opts :selector) "'"))]
    (str "{" headers type keep-alive selector "}")))

(defn- app-outer
  "Generate the outer HTML shell for the application.

   Creates the initial HTML page that loads all required JavaScript
   and CSS resources, sets up the client-side environment, and prepares
   for loading the actual application content.

   Parameters:
     server-id - A unique identifier for this server instance
     opts - A map of options:
            :title - The page title (defaults to \"Weave\")
            :head - Additional HTML to include in the head section
            :keep-alive - Whether to keep SSE connections alive when tab is hidden"
  [server-id opts]
  (-> (resp/response
       (c/html
        [c/doctype-html5
         [:html {:class "h-full bg-gray-100"}
          [:head
           [:meta {:charset "UTF-8"}]
           [:meta {:name "viewport"
                   :content "width=device-width, initial-scale=1.0"}]
           ;;
           [:link {:rel "icon" :href "data:image/png;base64,iVBORw0KGgo="}]
           [:title (or (:title opts) "Weave")]
           ;;
           [:link {:href "/tailwind@2.2.19.css" :rel "stylesheet"}]
           [:script {:type "module" :src "/datastar@v1.0.0-beta.11.js"}]
           [:script
            "window.__pushHashChange = false;
             function path() {
               const hashPath = window.location.hash.substring(1);

               if (!hashPath) {
                 return \"/\";
               } else {
                 return hashPath.startsWith(\"/\") ? hashPath : \"/\" + hashPath;
               }
             }"
            "function csrf() { return document.cookie.match(/(^| )weave-csrf=([^;]+)/)?.[2];}"
            "function instance() { return  \"" (random-uuid) "\";}"
            "function server() { return  \"" server-id "\";}"
            "window.addEventListener('hashchange', function(e) {
               if (!window.__pushHashChange) {
                 window.location.reload();
               }
             });"]
           (:head opts)]
          ;;
          [:body {:class "h-full"}
           [:div {:data-signals-app.server "server();"}]
           [:div {:data-signals-app.path "path();"}]
           [:div {:data-signals-app.csrf "csrf();"}]
           [:div {:data-signals-app.instance "instance();"}]
           (let [opts (if (:sse-keep-alive opts)
                        {:keep-alive true}
                        {})
                 opts (request-options opts)]
             [:div {:id "main"
                    :data-on-load
                    (str "@get('/app-inner', " opts ")")}])]]]))
      (resp/content-type "text/html")
      (resp/charset "UTF-8")))

(defn- app-inner
  "Renders the inner application content and establishes an SSE connection.

   - Verifie the server ID and CSRF token from client signals
   - Create an SSE response that renders the view
   - Register the connection for future updates
   - Handle connection cleanup on close

   If verification fails (stale session), it forces a page reload."
  [req server-id view]
  (let [signals (get-signals req)]
    (if (and (= server-id (-> signals :app :server))
             (session/verify-csrf
              *session-id* (-> signals :app :csrf)))
      (->sse-response
       {:on-open
        (fn [sse-gen]
          (d*/merge-fragment!
           sse-gen
           (c/html
            [:div {:id "main"}
             (view)]))
          (session/add-connection!
           *session-id* *instance-id* sse-gen))

        :on-close
        (fn [_sse-gen _status]
          (session/remove-connection!
           *session-id* *instance-id*))})
       ;; stale session reload
      (do (->sse-response
           {:on-open
            (fn [sse-gen]
              (d*/execute-script!
               sse-gen "window.location.reload();"))})
          (session/remove-connection!
           *session-id* *instance-id*)))))

(def ^{:private true} event-routes
  "Contains all dynamically registered event handler routes.

   Stores Compojure route definitions created by the handler macro.
   Each time a handler is defined, a new unique route is generated and added
   to this collection. These routes are then combined with the base routes
   when processing requests.

   This approach allows handlers to be defined inline within views while still
   being properly registered with the web server."
  (atom []))

(defn- add-route!
  "Dynamically registers a new route for a handler function.

    - Generate a unique route path using UUID
    - Add the handler to the event-routes atom as a POST route
    - Return the client-side datastar code to invoke this route

   Parameters:
     handler-fn - The Ring handler function to register
     opts - Options map for the handler (passed to request-options)

   Returns:
     A string containing the datastar code to invoke this route"
  [handler-fn opts]
  (let [route (str "/" (random-uuid))]
    (swap! event-routes conj (POST route [] handler-fn))
    (str "@post('" route "', " (request-options opts) ")")))

(defmacro handler
  "Create a handler function for processing client-side events.

   Parameters:
     opts - Optional map of handler options:
            :auth-required? - Whether authentication is required
            :type - Request content type (:form for form submissions)
            :keep-alive - Whether to keep the connection alive when tab is hidden
            :selector - CSS selector for the form to submit (e.g. \"#myform\")
     body - Forms to execute when the handler is invoked

   Returns:
     A string containing the datastar code to invoke this handler"
  [& args]
  (let [[opts body] (if (and (seq args) (map? (first args)))
                      [(first args) (rest args)]
                      [{} args])]
    `(let [handler-fn#
           (fn [req#]
             (let [body# (:body req#)
                   body# (if (instance? java.io.InputStream body#)
                           (slurp body#)
                           body#)
                   req# (assoc req# :body body#)]
               (bind-vars
                req#
                (if (and (:auth-required? ~opts)
                         (not (:identity *request*)))
                  {:status 403, :headers {}, :body nil}
                  (do ~@body
                      {:status 200, :headers {}, :body nil})))))]
       (#'add-route! handler-fn# ~opts))))

(defn push-html!
  "Push HTML to the specific browser tab/window that
   triggered the handler."
  [html]
  (let [sse (session/instance-connection
             *session-id* *instance-id*)]
    (d*/merge-fragment! sse (c/html html))))

(defn broadcast-html!
  "Pushes HTML to all browser tabs/windows that share the same
   session ID."
  [html]
  (let [connections (session/session-connections *session-id*)]
    (doseq [sse connections]
      (d*/merge-fragment! sse (c/html html)))))

(defn push-path!
  "Change the URL hash for the specific browser tab/window that
   triggered the current handler."
  ([url]
   (push-path! url nil))
  ([url view-fn]
   (let [sse (session/instance-connection
              *session-id* *instance-id*)
         cmd (str "window.__pushHashChange = true;
                   history.pushState(null, null, \"#" url "\");
                   window.__pushHashChange = false;")]
     (d*/merge-signals!
      sse
      (charred/write-json-str {:app {:path url}}))
     (d*/execute-script! sse cmd)
     (when view-fn
       (binding [*app-path* url]
         (push-html! (view-fn)))))))

(defn broadcast-path!
  "Change the URL hash for all browser tabs/windows that share the same
   session ID."
  ([url]
   (broadcast-path! url nil))
  ([url view-fn]
   (let [connections (session/session-connections *session-id*)
         cmd (str "window.__pushHashChange = true;
                   history.pushState(null, null, \"#" url "\");
                   window.__pushHashChange = false;")]
     (doseq [sse connections]
       (d*/merge-signals!
        sse
        (charred/write-json-str {:app {:path url}}))
       (d*/execute-script! sse cmd))
     (when view-fn
       (binding [*app-path* url]
         (doseq [sse connections]
           (d*/merge-fragment! sse (c/html (view-fn)))))))))

(defn push-script!
  "Send JavaScript to the specific browser tab/window that
   triggered the current handler for execution."
  [script]
  (let [sse (session/instance-connection
             *session-id* *instance-id*)]
    (d*/execute-script! sse script)))

(defn push-reload!
  "Send a reload command to the specific browser tab/window that
   triggered the current handler."
  []
  (let [sse (session/instance-connection
             *session-id* *instance-id*)]
    (d*/execute-script! sse "window.location.reload();")))

(defn broadcast-script!
  "Send JavaScript to all browser tabs/windows that share the same
   session ID for execution."
  [script]
  (let [connections (session/session-connections *session-id*)]
    (doseq [sse connections]
      (d*/execute-script! sse script))))

(defn push-signal!
  "Send updated signal values to the specific browser tab/window that
   triggered the current handler."
  [signal]
  (let [sse (session/instance-connection
             *session-id* *instance-id*)]
    (d*/merge-signals!
     sse (charred/write-json-str signal))))

(defn set-cookie!
  "Send a Set-Cookie header to the specific browser tab/window that
   triggered the current handler. It allows setting, updating, or
   deleting cookies."
  [cookie]
  (->sse-response
   {:headers {"Set-Cookie" cookie}
    :on-open
    (fn [sse]
      (d*/with-open-sse sse
         (d*/execute-script! sse "null;")))}))

(defn authenticated?
  "Return `true` if the `request` is an authenticated request."
  [request]
  (boolean (:identity request)))

(defn throw-unauthorized
  "Throw unauthorized exception."
  ([] (throw-unauthorized {}))
  ([errordata]
   (throw (ex-info "Unauthorized." {::type ::unauthorized
                                    ::payload errordata}))))

#_:clj-kondo/ignore
(defn make-router []
  (fn [router]
    (let [path *app-path*
          match (r/match-by-path router path)]
      (if-not match
        nil
        (let [route-data (:data match)
              requires-auth? (:auth-required? route-data)
              authenticated? (:identity *request*)]
          (if (and requires-auth? (not authenticated?))
            :sign-in
            (-> route-data :name)))))))

(defn run
  "Starts the Weave application server.

   Parameters:
     view - A function that returns the Hiccup view to render
     options - A map of server options:
              :http-kit - HTTP server options map:
                         :bind - IP address to bind to (default: \"0.0.0.0\")
                         :port - HTTP server port (default: 8080)
              :nrepl - nrepl server options map:
                         :bind - IP address to bind to (default: \"0.0.0.0\")
                         :port - Server port (default: 8888)
              :title - Page title
              :head - Additional HTML for the head section
              :sse-keep-alive - Whether to keep SSE connections alive when tab is hidden
              :handlers - A vector of custom route handlers (Compojure routes) that
                          will be included in the application's routing system
              :csrf-secret - Secret for CSRF token generation
              :jwt-secret - Secret for JWT token generation/validation

   Returns:
     A function that stops the server when called"
  [view options]
  (let [server-id (str (random-uuid))
        csrf-secret (or (:csrf-secret options)
                        (str (random-uuid)))
        csrf-keyspec (session/secret-key->hmac-sha256-keyspec
                      csrf-secret)
        jwt-secret (reset! session/jwt-secret
                           (or (:jwt-secret options)
                               (str (random-uuid))))
        site-defaults (-> def/site-defaults
                          (assoc :session false)
                          (assoc-in [:security :anti-forgery] false)

                          (assoc-in [:static :resources] false)
                          (assoc-in [:static :files] false))
        http-kit-opts (merge {:bind "0.0.0.0" :port 8080}
                             (:http-kit options))
        custom-handlers (or (:handlers options) [])
        handler
        (fn [request]
          (let [base-routes (compojure.core/routes
                             (GET "/" _req
                               (app-outer server-id options))
                             (GET "/app-inner" req
                               (bind-vars
                                req (app-inner req server-id view)))
                             (route/resources "/"))
                all-routes (routes base-routes
                                   (apply routes @event-routes)
                                   (apply routes custom-handlers)
                                   (route/not-found "Not Found"))]
            (binding [session/*csrf-keyspec* csrf-keyspec]
              (-> all-routes
                  (session/wrap-session jwt-secret)
                  (def/wrap-defaults site-defaults)
                  (wrap-gzip)
                  (apply [request])))))]

    (when-let [nrepl-opts (:nrepl options)]
      (let [nrepl-opts (merge
                        {:bind "0.0.0.0" :port 8888} nrepl-opts)]
        (apply nrepl.server/start-server (mapcat identity nrepl-opts))
        (log/info
         "Started nREPL server on"
         (str (:bind nrepl-opts) ":" (:port nrepl-opts)))))

    (let [server (server/run-server handler http-kit-opts)]
      (log/info "Started Weave server on"
                (str (:bind http-kit-opts) ":" (:port http-kit-opts)))
      server)))
