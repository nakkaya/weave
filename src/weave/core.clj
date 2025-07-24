(ns weave.core
  (:require
   [camel-snake-kebab.core :as csk]
   [camel-snake-kebab.extras :as cske]
   [charred.api :as charred]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
   [compojure.core :refer [GET POST routes]]
   [compojure.route :as route]
   [dev.onionpancakes.chassis.core :as c]
   [integrant.core :as ig]
   [nrepl.server :as nrepl.server]
   [org.httpkit.server :as http.server]
   [ring.middleware.defaults :as def]
   [ring.middleware.gzip :refer [wrap-gzip]]
   [ring.util.response :as resp]
   [starfederation.datastar.clojure.adapter.http-kit :as hk-gen]
   [starfederation.datastar.clojure.api :as d*]
   [weave.session :as session])
  (:import
   [java.awt RenderingHints]
   [java.awt.image BufferedImage]
   [java.io ByteArrayOutputStream]
   [javax.imageio ImageIO]))

(def ^:dynamic *view*
  "Holds the view function that renders the application."
  nil)

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

(def ^:dynamic *signals*
  "Contains the parsed client-side signals from the current request.

   Signals are reactive state data sent from the browser via Datastar,
   similar to form data but for real-time applications."
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
               *request* ~req
               *signals* (get-signals ~req)]
       ~@body)))

(let [key-fn (fn [v]
               (-> v csk/->kebab-case-keyword keyword))
      read-json (charred/parse-json-fn
                 {:async? false :bufsize 1024 :key-fn key-fn})]
  (defn get-signals
    "Extract and parse client-side signals from the request."
    [req]
    (let [signals (-> req d*/get-signals)]
      (if (empty? signals)
        {}
        (read-json signals)))))

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
  (let [sb (StringBuilder.)
        has-options? (or (= (:type opts) :form)
                         (= (:type opts) :sign-in)
                         (:keep-alive opts)
                         (:selector opts))]
    (if has-options?
      (do
        (.append sb "{")
        (when (or (= (:type opts) :form)
                  (= (:type opts) :sign-in))
          (.append sb "contentType: 'form'"))
        (when (:keep-alive opts)
          (when (or (= (:type opts) :form)
                    (= (:type opts) :sign-in))
            (.append sb ", "))
          (.append sb "openWhenHidden: true"))
        (when-let [selector (:selector opts)]
          (when (or (= (:type opts) :form)
                    (= (:type opts) :sign-in)
                    (:keep-alive opts))
            (.append sb ", "))
          (.append sb "selector: '")
          (.append sb selector)
          (.append sb "'"))
        (.append sb "}"))
      (.append sb "{}"))
    (.toString sb)))

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
           [:script {:src "/tailwind@3.4.16.js"}]
           [:script {:src "/squint@v0.8.147.js"}]
           [:script {:type "module" :src "/weave.js"}]
           [:script {:type "module"}
            (let [keep-alive (get-in opts [:sse :keep-alive] false)]
              (str "weave.setup('" server-id "', '" (random-uuid) "', " keep-alive ");"))]
           (:head opts)]
          [:body {:class "w-full h-full"}
           [:div {:id "weave-main" :class "w-full h-full"}]]]]))
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
  (let [headers (:headers req)
        valid-session? (and (= server-id (headers "x-server-id"))
                            (session/verify-csrf
                             *session-id* (headers "x-csrf-token")))]
    (if valid-session?
      (app-inner req server-id view options)
      (do
        (session/remove-connection! *session-id* *instance-id*)
        (->sse-response
         {hk-gen/on-open
          (fn [sse-gen]
            (d*/execute-script! sse-gen "weave.reload();"))})))))

(defmethod app-inner true
  [_req _server-id view _options]
  (->sse-response
   {hk-gen/on-open
    (fn [sse-gen]
      (d*/patch-elements!
       sse-gen
       (c/html
        [:div {:id "weave-main"
               :class "w-full h-full"}
         (view)]))
      (session/add-connection!
       *session-id* *instance-id* sse-gen))

    hk-gen/on-close
    (fn [_sse-gen _status]
      (session/remove-connection!
       *session-id* *instance-id*))}))

(defmethod app-inner false
  [_req _server-id view _options]
  (->sse-response
   {hk-gen/on-open
    (fn [sse-gen]
      (d*/patch-elements!
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

(def ^{:private true :dynamic true} *event-handlers*
  "Registered event handlers for the application."
  (atom {}))

(defn- add-route!
  "Register a new route for a handler function."
  [route route-hash handler-fn dstar-expr]
  (swap! *event-handlers* assoc route-hash {:route route
                                            :handler-fn handler-fn
                                            :dstar-expr dstar-expr}))

(defn- handler-router-middleware
  "Custom middleware that handles event handler routes via direct hash map lookup."
  [routes]
  (fn [request]
    (let [uri (:uri request)
          method (:request-method request)]
      (if (and (= method :post)
               (.startsWith ^String uri "/h/"))
        (let [route-hash (subs uri 3)
              handlers @*event-handlers*
              handler-entry (get handlers route-hash)]
          (if-let [handler-fn (:handler-fn handler-entry)]
            (handler-fn request)
            {:status 404
             :headers {"Content-Type" "text/plain"}
             :body "Handler not found"}))
        (routes request)))))

(defmacro handler
  "Create a handler that process client-side events."
  [args & body]
  (let [opts (or (meta args) {})
        body-hash (hash body)]
    `(let [arg-hash# (mapv hash ~args)
           cache-key# [~body-hash arg-hash#]
           route-hash# (Integer/toUnsignedString (hash cache-key#))]
       (if-let [cached-route# (get @#'*event-handlers* route-hash#)]
         (:dstar-expr cached-route#)
         (let [handler-fn#
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
                                               {hk-gen/on-open
                                                (fn [sse-gen#]
                                                  (binding [*sse-gen* sse-gen#]
                                                    ~@body)
                                                  (d*/close-sse! sse-gen#))}))))))
               route# (str "/h/" route-hash#)
               dstar-expr# (str "@call('" route# "', " (#'request-options ~opts) ")")]
           (#'add-route! route# route-hash# handler-fn# dstar-expr#)
           dstar-expr#)))))

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
   triggered the handler.

   Args:
   - html: The HTML content to push
   - opts: Optional map with the following keys:
     - :mode - The patch mode, one of:
         :outer (default) - Morphs the outer HTML of the elements
         :inner - Morphs the inner HTML of the elements
         :replace - Replaces the outer HTML of the elements
         :prepend - Prepends the elements to the target's children
         :append - Appends the elements to the target's children
         :before - Inserts the elements before the target as siblings
         :after - Inserts the elements after the target as siblings
         :remove - Removes target elements from DOM
     - :selector - CSS selector to target specific elements
     - :use-view-transition - Whether to use view transitions (boolean)
     - :id - Event ID for SSE replay functionality
     - :retry-duration - Retry duration in milliseconds"
  ([html]
   (push-html! html {}))
  ([html opts]
   (let [mode-map {:outer d*/pm-outer
                   :inner d*/pm-inner
                   :replace d*/pm-replace
                   :prepend d*/pm-prepend
                   :append d*/pm-append
                   :before d*/pm-before
                   :after d*/pm-after
                   :remove d*/pm-remove}
         mode (get mode-map (:mode opts) d*/pm-outer)
         patch-opts (cond-> {d*/patch-mode mode}
                      (:selector opts) (assoc d*/selector (:selector opts))
                      (:use-view-transition opts) (assoc d*/use-view-transition (:use-view-transition opts))
                      (:id opts) (assoc d*/id (:id opts))
                      (:retry-duration opts) (assoc d*/retry-duration (:retry-duration opts)))]

     (d*/patch-elements! (sse-conn) (c/html html) patch-opts))))

(defn broadcast-html!
  "Pushes HTML to all browser tabs/windows that share the same
   session ID.

   Args:
   - html: The HTML content to broadcast
   - opts: Optional map with the same keys as push-html!"
  ([html]
   (broadcast-html! html {}))
  ([html opts]
   (let [mode-map {:outer d*/pm-outer
                   :inner d*/pm-inner
                   :replace d*/pm-replace
                   :prepend d*/pm-prepend
                   :append d*/pm-append
                   :before d*/pm-before
                   :after d*/pm-after
                   :remove d*/pm-remove}
         mode (get mode-map (:mode opts) d*/pm-outer)
         patch-opts (cond-> {d*/patch-mode mode}
                      (:selector opts) (assoc d*/selector (:selector opts))
                      (:use-view-transition opts) (assoc d*/use-view-transition (:use-view-transition opts))
                      (:id opts) (assoc d*/id (:id opts))
                      (:retry-duration opts) (assoc d*/retry-duration (:retry-duration opts)))
         connections (session/session-connections *session-id*)]
     (doseq [sse connections]
       (d*/patch-elements! sse (c/html html) patch-opts)))))

(defn push-path!
  "Change the URL hash for the specific browser tab/window that
   triggered the current handler."
  ([url]
   (push-path! url nil))
  ([url view-fn]
   (let [sse (sse-conn)
         cmd (str "weave.pushHistoryState('" url "');")]

     (d*/patch-signals!
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
         cmd (str "weave.pushHistoryState('" url "');")]
     (doseq [sse connections]
       (d*/patch-signals!
        sse
        (charred/write-json-str {:app {:path url}}))
       (d*/execute-script! sse cmd))
     (when view-fn
       (binding [*app-path* url]
         (doseq [sse connections]
           (d*/patch-elements! sse (c/html (view-fn)))))))))

(defn push-script!
  "Send JavaScript to the specific browser tab/window that
   triggered the current handler for execution."
  [script]
  (d*/execute-script! (sse-conn) script))

(defn push-reload!
  "Send a reload command to the specific browser tab/window that
   triggered the current handler."
  []
  (d*/execute-script! (sse-conn) "weave.reload();"))

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
  (d*/patch-signals!
   (sse-conn) (->> signal
                   (cske/transform-keys csk/->camelCase)
                   (charred/write-json-str))))

(defn set-cookie!
  "Send a Set-Cookie header to the specific browser tab/window that
   triggered the current handler. It allows setting, updating, or
   deleting cookies."
  [cookie]
  (push-script!
   (str "document.cookie = '" cookie "';")))

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
        (.setRenderingHint g
                           RenderingHints/KEY_INTERPOLATION
                           RenderingHints/VALUE_INTERPOLATION_BILINEAR)
        (.setRenderingHint g
                           RenderingHints/KEY_RENDERING
                           RenderingHints/VALUE_RENDER_QUALITY)
        (.setRenderingHint g
                           RenderingHints/KEY_ANTIALIASING
                           RenderingHints/VALUE_ANTIALIAS_ON)
        (.drawImage g icon 0 0 width height nil)
        resized
        (finally
          (.dispose g))))))

(defn- icon->bytes
  "Convert a BufferedImage to a byte array in PNG format."
  [^BufferedImage icon]
  (when icon
    (with-open [baos (ByteArrayOutputStream.)]
      (ImageIO/write icon "png" baos)
      (.toByteArray baos))))

(def ^{:private true} icon-loader
  (memoize
   (fn [icon-path width height]
     (when-let [icon (load-icon icon-path)]
       (->> (resize-icon icon width height)
            (icon->bytes))))))

(defn- icon-handler
  "Create a handler that serves an icon at the specified size."
  [icon-path width height]
  (fn [_req]
    (if-let [icon (icon-loader icon-path width height)]
      (-> (resp/response icon)
          (resp/content-type "image/png")
          (resp/header "Cache-Control" "public, max-age=86400"))
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
  (let [resource (.openConnection url)]
    {:content (.getInputStream resource)
     :content-length (#'resp/connection-content-length resource)
     :last-modified (#'resp/connection-last-modified resource)}))

(defmethod ig/init-key :weave/nrepl [_ {:keys [bind port] :as options}]
  (when options
    (log/info "Starting nREPL server on" (str bind ":" port))
    (let [server (apply nrepl.server/start-server (mapcat identity options))]
      {:nrepl-server server})))

(defmethod ig/halt-key! :weave/nrepl [_ {:keys [nrepl-server]}]
  (when nrepl-server
    (log/info "Stopping nREPL server")
    (nrepl.server/stop-server nrepl-server)))

(defmethod ig/init-key :weave/http [_ {:keys [handler options]}]
  (let [server (http.server/run-server handler options)]
    (log/info "Started Weave server on"
              (str (:bind options) ":" (:port options)))
    {:http-server server}))

(defmethod ig/halt-key! :weave/http [_ {:keys [http-server]}]
  (when http-server
    (log/info "Stopping Weave HTTP server")
    (http-server)))

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
              :middleware - A sequence of middleware functions to apply to the handler chain
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
     An integrant system."
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
        icon-path (:icon options)
        icon-routes (when icon-path
                      [(GET "/favicon.png" [] (icon-handler icon-path 32 32))
                       (GET "/icon-180.png" [] (icon-handler icon-path 180 180))
                       (GET "/icon-192.png" [] (icon-handler icon-path 192 192))
                       (GET "/icon-512.png" [] (icon-handler icon-path 512 512))
                       (GET "/manifest.json" [] (manifest-handler options server-id))])
        custom-routes (concat (or (:handlers options) [])
                              (or icon-routes []))
        routes (routes
                (GET "/" _req
                  (app-outer server-id options))
                (POST "/app-loader" req
                  (let [body (:body req)
                        body (if (instance? java.io.InputStream body)
                               (slurp body)
                               body)
                        req (assoc req :body body)]
                    (bind-vars
                     req (app-loader req server-id view options))))
                (GET "/health" _req
                  {:status 200
                   :headers {"Content-Type" "application/json"}
                   :body "{\"status\":\"ok\"}"})
                (route/resources "/")
                (apply routes custom-routes)
                (route/not-found "Not Found"))
        handler-chain (-> routes
                          handler-router-middleware
                          (session/wrap-session jwt-secret)
                          (def/wrap-defaults site-defaults)
                          (wrap-gzip))
        handler-chain (if (:middleware options)
                        (reduce (fn [handler middleware-fn]
                                  (middleware-fn handler))
                                handler-chain
                                (reverse (:middleware options)))
                        handler-chain)
        handler
        (fn [request]
          (binding [*view* view
                    session/*csrf-keyspec* csrf-keyspec
                    *secure-handlers* (:secure-handlers options)]
            (handler-chain request)))]

    (ig/init
     (merge
      {:weave/nrepl (when-let [nrepl-opts (:nrepl options)]
                      (merge {:bind "0.0.0.0" :port 8888}
                             nrepl-opts))
       :weave/http {:handler handler
                    :options (merge {:bind "0.0.0.0" :port 8080}
                                    (:http-kit options))}}
      (:integrant options)))))
