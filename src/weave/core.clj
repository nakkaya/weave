(ns weave.core
  (:require
   [charred.api :as charred]
   [clojure.java.io :as io]
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
   [weave.session :as session]
   [weave.squint :refer [clj->js]])
  (:import
   [java.awt.image BufferedImage]
   [java.awt RenderingHints]
   [java.io ByteArrayOutputStream]
   [javax.imageio ImageIO]))

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

(def ^:dynamic *secure-handlers*
  "When true, all handlers require authentication by default
   unless :auth-required? is explicitly set to false."
  false)

(def ^:dynamic *sse-gen*
  "Current Server-Sent Events (SSE) generator instance."
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
            :icon - Path to an icon file in the classpath (PNG format)
            :head - Additional HTML to include in the head section
            :view-port - The viewport meta tag
            :keep-alive - Whether to keep SSE connections alive when tab is hidden"
  [server-id opts]
  (-> (resp/response
       (c/html
        [c/doctype-html5
         [:html {:class "w-full h-full"}
          [:head
           [:meta {:charset "UTF-8"}]
           (or (:viewport opts)
               [:meta {:name "viewport"
                       :content (str "width=device-width,"
                                     "initial-scale=1.0,"
                                     "maximum-scale=1.0,"
                                     "user-scalable=no")}])
           ;;
           (when (:icon opts)
             [[:link {:rel "icon" :href "/favicon.png"}]
              [:link {:rel "apple-touch-icon" :href "/icon-180.png"}]
              [:link {:rel "manifest" :href "/manifest.json"}]])
           [:title (or (:title opts) "Weave")]
           ;;
           [:script {:type "module" :src "/datastar@v1.0.0-beta.11.js"}]
           [:script {:src "/squint@v0.8.147.js"}]
           ;;
           [:script {:src "/tailwind@3.4.16.js"}]
           [:script
            (clj->js
             (set! (.-config js/tailwind) {:darkMode "class"})

             (defn csrf []
               (some-> (.-cookie js/document)
                       (.match #"(^|)weave-csrf=([^;]+)")
                       (aget 2)))

             (.addEventListener js/window "hashchange"
                                (fn [e]
                                  (when-not (.-__pushHashChange js/window)
                                    (.reload (.-location js/window)))))

             (set! (.-__pushHashChange js/window) false)

             (defn path []
               (let [hash-path (.substring (.-hash (.-location js/window)) 1)]
                 (if (not hash-path)
                   "/"
                   (if (.startsWith hash-path "/")
                     hash-path
                     (str "/" hash-path)))))

             (defn instance []
               ~(str (random-uuid)))

             (defn server []
               ~(str server-id)))]

           (:head opts)]
          ;;
          [:body {:class "w-full h-full"}
           [:div {:data-signals-app.server "server();"}]
           [:div {:data-signals-app.path "path();"}]
           [:div {:data-signals-app.csrf "csrf();"}]
           [:div {:data-signals-app.instance "instance();"}]
           (let [opts (if (get-in opts [:sse :keep-alive])
                        {:keep-alive true}
                        {})
                 opts (request-options opts)]
             [:div {:id "weave-main"
                    :class "w-full h-full"
                    :data-on-load
                    (str "@get('/app-loader', " opts ")")}])]]]))
      (resp/content-type "text/html")
      (resp/charset "UTF-8")))

(defmulti app-inner
  "Renders the inner application content based on SSE configuration.

   Dispatches based on whether SSE is enabled in the options."
  (fn [_req _server-id _view options]
    (get-in options [:sse :enabled])))

(defn- app-loader
  "Checks for stale connections and triggers a reload if necessary.

   - Verify the server ID and CSRF token from client signals
   - If valid, passes to app-inner for rendering
   - If stale, forces a page reload and removes the connection

   This is the entry point for all app content rendering."
  [req server-id view options]
  (let [signals (get-signals req)
        valid-session? (and (= server-id (-> signals :app :server))
                            (session/verify-csrf
                             *session-id* (-> signals :app :csrf)))]
    (if valid-session?
      (app-inner req server-id view options)
      (do
        (session/remove-connection! *session-id* *instance-id*)
        (->sse-response
         {:on-open
          (fn [sse-gen]
            (d*/execute-script!
             sse-gen
             (clj->js
              (-> js/window .-location .reload))))})))))

(defmethod app-inner true
  [_req _server-id view _options]
  (->sse-response
   {:on-open
    (fn [sse-gen]
      (d*/merge-fragment!
       sse-gen
       (c/html
        [:div {:id "weave-main"
               :class "w-full h-full"}
         (view)]))
      (session/add-connection!
       *session-id* *instance-id* sse-gen))

    :on-close
    (fn [_sse-gen _status]
      (session/remove-connection!
       *session-id* *instance-id*))}))

(defmethod app-inner false
  [_req _server-id view _options]
  (->sse-response
   {:on-open
    (fn [sse-gen]
      (d*/merge-fragment!
       sse-gen
       (c/html
        [:div {:id "weave-main"
               :class "w-full h-full"}
         (view)]))
      (d*/close-sse! sse-gen))}))

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
            :auth-required? - Whether authentication is required (defaults to true if :secure-handlers is enabled)
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
                (let [auth-required?# (if (contains? ~opts :auth-required?)
                                        (:auth-required? ~opts)
                                        *secure-handlers*)]
                  (if (and auth-required?#
                           (not (authenticated? *request*)))
                    {:status 403, :headers {}, :body nil}
                    (hk-gen/->sse-response *request*
                                           {:on-open
                                            (fn [sse-gen#]
                                              (binding [*sse-gen* sse-gen#]
                                                ~@body)
                                              (d*/close-sse! sse-gen#))}))))))]
       (#'add-route! handler-fn# ~opts))))

(defn- sse-conn
  "Returns the current Server-Sent Events (SSE) connection.

   First checks if there's an active SSE generator in the dynamic *sse-gen* var.
   If not found, attempts to retrieve the connection from the session store
   using the current session ID and instance ID.

   This function is used internally by push-html!, broadcast-html!, and other
   functions that need to communicate with the client browser."
  []
  (if *sse-gen*
    *sse-gen*
    (session/instance-connection
     *session-id* *instance-id*)))

(defn push-html!
  "Push HTML to the specific browser tab/window that
   triggered the handler."
  [html]
  (d*/merge-fragment! (sse-conn) (c/html html)))

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
   (let [sse (sse-conn)
         cmd (clj->js
              (set! (.-__pushHashChange js/window) true)
              (.pushState js/history nil nil ~(str "#" url))
              (set! (.-__pushHashChange js/window) false))]

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
         cmd (clj->js
              (set! (.-__pushHashChange js/window) true)
              (.pushState js/history nil nil ~(str "#" url))
              (set! (.-__pushHashChange js/window) false))]
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
  (d*/execute-script! (sse-conn) script))

(defn push-reload!
  "Send a reload command to the specific browser tab/window that
   triggered the current handler."
  []
  (d*/execute-script!
   (sse-conn)
   (clj->js
    (-> js/window .-location .reload))))

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
  (d*/merge-signals!
   (sse-conn) (charred/write-json-str signal)))

(defn set-cookie!
  "Send a Set-Cookie header to the specific browser tab/window that
   triggered the current handler. It allows setting, updating, or
   deleting cookies."
  [cookie]
  (push-script!
   (str "document.cookie = '" cookie "';")))

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

(defn- load-icon
  "Load an icon from the classpath."
  [icon-path]
  (when icon-path
    (try
      (-> (io/resource icon-path)
          (ImageIO/read))
      (catch Exception e
        (log/warn "Failed to load icon resource:" icon-path e)
        nil))))

(defn- resize-icon
  "Resize an icon to the specified dimensions."
  [^BufferedImage icon width height]
  (when icon
    (let [resized (BufferedImage. width height BufferedImage/TYPE_INT_ARGB)
          g (.createGraphics resized)]
      (try
        (.setRenderingHint g RenderingHints/KEY_INTERPOLATION
                          RenderingHints/VALUE_INTERPOLATION_BILINEAR)
        (.setRenderingHint g RenderingHints/KEY_RENDERING
                          RenderingHints/VALUE_RENDER_QUALITY)
        (.setRenderingHint g RenderingHints/KEY_ANTIALIASING
                          RenderingHints/VALUE_ANTIALIAS_ON)
        (.drawImage g icon 0 0 width height nil)
        resized
        (finally
          (.dispose g))))))

(defn- icon->bytes
  "Convert a BufferedImage to a byte array in PNG format."
  [^BufferedImage icon]
  (when icon
    (let [baos (ByteArrayOutputStream.)]
      (ImageIO/write icon "png" baos)
      (.toByteArray baos))))

(defn- icon-handler
  "Create a handler that serves an icon at the specified size."
  [icon-path width height]
  (fn [_req]
    (if-let [icon (load-icon icon-path)]
      (let [resized (resize-icon icon width height)
            bytes (icon->bytes resized)]
        (-> (resp/response bytes)
            (resp/content-type "image/png")
            (resp/header "Cache-Control" "public, max-age=86400")))
      (resp/not-found "Icon not found"))))

(defn- manifest-handler
  "Create a handler that serves the web app manifest."
  [options server-id]
  (fn [_req]
    (let [pwa-opts (:pwa options)
          name (or (:name pwa-opts)
                   (:title options)
                   "Weave")
          short-name (or (:short-name pwa-opts) name)
          manifest {:name name
                    :short_name short-name
                    :id server-id
                    :icons [{:src "/icon-192.png"
                             :sizes "192x192"
                             :type "image/png"}
                            {:src "/icon-512.png"
                             :sizes "512x512"
                             :type "image/png"}]
                    :start_url (or (:start-url pwa-opts) "/")
                    :display (or (:display pwa-opts) "standalone")
                    :background_color (or (:background-color pwa-opts) "#f2f2f2")
                    :theme_color (or (:theme-color pwa-opts) "#ffffff")}
          manifest (if-let [desc (:description pwa-opts)]
                     (assoc manifest :description desc)
                     manifest)]
      (-> (resp/response (charred/write-json-str manifest))
          (resp/content-type "application/json")
          (resp/charset "UTF-8")))))

;; This method is needed to handle resources from GraalVM-compiled
;; JARs.  When running in a GraalVM native image, resources are
;; accessed via the 'resource:' URL scheme instead of the standard
;; 'jar:' or 'file:' schemes. Without this method, Ring's resource
;; handling would fail to serve static resources from the classpath in
;; GraalVM environments.
(defmethod resp/resource-data :resource
  [^java.net.URL url]
  ;; GraalVM resource scheme
  (let [resource     (.openConnection url)]
    {:content        (.getInputStream resource)
     :content-length (#'resp/connection-content-length resource)
     :last-modified  (#'resp/connection-last-modified resource)}))

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
              :view-port - The viewport meta tag
              :sse - Server-Sent Events options map:
                    :enabled - Whether to enable SSE (default: true)
                    :keep-alive - Whether to keep SSE connections alive when tab is hidden (default: false)
              :handlers - A vector of custom route handlers (Compojure routes) that
                          will be included in the application's routing system
              :csrf-secret - Secret for CSRF token generation
              :jwt-secret - Secret for JWT token generation/validation
              :secure-handlers - When true, all handlers require authentication by default
                                 unless :auth-required? is explicitly set to false
              :icon - Path to an icon file in the classpath (PNG format)
              :pwa - A map of Progressive Web App manifest options:
                      :name - Application name (defaults to :title)
                      :short-name - Application shortname (defaults to :name)
                      :description - Application description
                      :display - Preferred display mode (default: \"standalone\")
                      :background-color - Background color (default: \"#f2f2f2\")
                      :theme-color - Theme color (default: \"#ffffff\")
                      :start-url - Start URL when launched (default: \"/\")

   Returns:
     A function that stops the server when called"
  [view options]
  (let [server-id (str (random-uuid))
        options (update options :sse #(merge {:enabled true :keep-alive false} %))
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
        icon-path (:icon options)
        icon-handlers (when icon-path
                        [(GET "/favicon.png" [] (icon-handler icon-path 32 32))
                         (GET "/icon-180.png" [] (icon-handler icon-path 180 180))
                         (GET "/icon-192.png" [] (icon-handler icon-path 192 192))
                         (GET "/icon-512.png" [] (icon-handler icon-path 512 512))
                         (GET "/manifest.json" [] (manifest-handler options server-id))])
        custom-handlers (concat (or (:handlers options) [])
                                (or icon-handlers []))
        handler
        (fn [request]
          (let [base-routes (compojure.core/routes
                             (GET "/" _req
                               (app-outer server-id options))
                             (GET "/app-loader" req
                               (bind-vars
                                req (app-loader req server-id view options)))
                             (GET "/health" _req
                               {:status 200
                                :headers {"Content-Type" "application/json"}
                                :body "{\"status\":\"ok\"}"})
                             (route/resources "/"))
                all-routes (routes base-routes
                                   (apply routes @event-routes)
                                   (apply routes custom-handlers)
                                   (route/not-found "Not Found"))]
            (binding [session/*csrf-keyspec* csrf-keyspec
                      *secure-handlers* (:secure-handlers options)]
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
