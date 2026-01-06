(ns weave.core
  (:require
   [camel-snake-kebab.core :as csk]
   [camel-snake-kebab.extras :as cske]
   [charred.api :as charred]
   [clojure.java.io :as io]
   [clojure.string :as s]
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
   [weave.push :as push]
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

(def ^:dynamic *query-params*
  "The current query parameters parsed into a map. This is used for
   accessing URL query parameters from handlers and views."
  nil)

(def ^:dynamic *timezone*
  "The client's timezone as an IANA timezone ID (e.g., \"Europe/Helsinki\").
  Defaults to \"UTC\"."
  "UTC")

(def ^:dynamic *language*
  "The client's preferred language from navigator.language (e.g., \"en-US\").
   Defaults to \"en\"."
  "en")

(def ^:dynamic *request*
  "Current Ring request map.  Contains all HTTP request information
   including headers, parameters, and the authenticated user identity."
  nil)

(def ^:dynamic *signals*
  "Contains the parsed client-side signals from the current request.

   Signals are reactive state data sent from the browser via Datastar,
   similar to form data but for real-time applications."
  nil)

(def ^:dynamic *handler-options*
  "Default options that will be merged with handler-specific options.
   Handler-specific options (provided via metadata) will override these defaults."
  {})

(def ^:dynamic *sse-gen*
  "Current Server-Sent Events (SSE) generator instance."
  nil)

(def ^:dynamic *server-id*
  "The current server instance's unique ID. Generated at server startup
   and used to detect stale connections from old server instances."
  nil)

(defn- parse-query-params
  "Parse query parameter string into a map.
   Examples:
   - \"\" -> {}
   - \"?foo=bar&baz=123\" -> {:foo \"bar\", :baz \"123\"}
   - \"?foo=bar&foo=baz\" -> {:foo [\"bar\" \"baz\"]}
   - \"?foo\" -> {:foo \"\"}
   "
  [query-string]
  (if (or (nil? query-string) (empty? query-string))
    {}
    (let [params-str (if (.startsWith ^String query-string "?")
                       (subs query-string 1)
                       query-string)]
      (if (empty? params-str)
        {}
        (->> (s/split params-str #"&")
             (map #(let [parts (s/split % #"=" 2)]
                     [(keyword (first parts))
                      (if (> (count parts) 1)
                        (java.net.URLDecoder/decode (second parts) "UTF-8")
                        "")]))
             (reduce (fn [acc [k v]]
                       (let [existing (get acc k)]
                         (cond
                           (nil? existing) (assoc acc k v)
                           (vector? existing) (assoc acc k (conj existing v))
                           :else (assoc acc k [existing v]))))
                     {}))))))

(defmacro bind-vars
  "Bind the dynamic variables *session-id*, *instance-id*,
   *app-path*, *query-params*, *timezone*, *language*, and *request*
   to values extracted from the request map for the duration of body execution.

   This macro is used internally to ensure that handlers and views
   have access to the current request context through these dynamic
   variables."
  [req & body]
  `(let [session-id# (session/get-sid ~req)
         headers# (:headers ~req)
         instance-id# (get headers# "x-instance-id")
         app-path# (get headers# "x-app-path")
         query-params-str# (get headers# "x-query-params")
         query-params# (#'parse-query-params query-params-str#)
         timezone# (or (get headers# "x-timezone") "UTC")
         language# (or (get headers# "x-language") "en")]
     (binding [*session-id* session-id#
               *instance-id* instance-id#
               *app-path* app-path#
               *query-params* query-params#
               *timezone* timezone#
               *language* language#
               *request* ~req
               *signals* (get-signals ~req)]
       ~@body)))

(defn- from-camel-case
  [s]
  (if (and s (seq s) (= (first s) \_))
    (str "_" (csk/->kebab-case (subs s 1)))
    (csk/->kebab-case s)))

(let [key-fn (fn [v]
               (-> v from-camel-case keyword))
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
            :filter-signals - Map with :include and :exclude regex patterns
            :retry-interval - The retry interval in milliseconds
            :retry-scaler - A numeric multiplier applied to scale retry wait times
            :retry-max-wait-ms - The maximum allowable wait time in milliseconds between retries
            :retry-max-count - The maximum number of retry attempts
            :request-cancellation - Controls request cancellation behavior

   Returns:
     A datastar string containing configured options."
  [opts]
  (let [sb (StringBuilder.)
        has-options? (or (= (:type opts) :form)
                         (= (:type opts) :sign-in)
                         (:keep-alive opts)
                         (:selector opts)
                         (:filter-signals opts)
                         (:retry-interval opts)
                         (:retry-scaler opts)
                         (:retry-max-wait-ms opts)
                         (:retry-max-count opts)
                         (:request-cancellation opts))]
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
        (when-let [filter-signals (:filter-signals opts)]
          (when (or (:include filter-signals) (:exclude filter-signals))
            (when (or (= (:type opts) :form)
                      (= (:type opts) :sign-in)
                      (:keep-alive opts)
                      (:selector opts))
              (.append sb ", "))
            (.append sb "filterSignals: { include: ")
            (.append sb (if-let [include (:include filter-signals)]
                          (str "/" include "/")
                          "/.*/"))
            (.append sb ", exclude: ")
            (.append sb (if-let [exclude (:exclude filter-signals)]
                          (str "/" exclude "/")
                          "/(^|\\.)_/"))
            (.append sb " }")))
        (when-let [retry-interval (:retry-interval opts)]
          (when (or (= (:type opts) :form)
                    (= (:type opts) :sign-in)
                    (:keep-alive opts)
                    (:selector opts)
                    (:filter-signals opts))
            (.append sb ", "))
          (.append sb "retryInterval: ")
          (.append sb retry-interval))
        (when-let [retry-scaler (:retry-scaler opts)]
          (when (or (= (:type opts) :form)
                    (= (:type opts) :sign-in)
                    (:keep-alive opts)
                    (:selector opts)
                    (:filter-signals opts)
                    (:retry-interval opts))
            (.append sb ", "))
          (.append sb "retryScaler: ")
          (.append sb retry-scaler))
        (when-let [retry-max-wait-ms (:retry-max-wait-ms opts)]
          (when (or (= (:type opts) :form)
                    (= (:type opts) :sign-in)
                    (:keep-alive opts)
                    (:selector opts)
                    (:filter-signals opts)
                    (:retry-interval opts)
                    (:retry-scaler opts))
            (.append sb ", "))
          (.append sb "retryMaxWaitMs: ")
          (.append sb retry-max-wait-ms))
        (when-let [retry-max-count (:retry-max-count opts)]
          (when (or (= (:type opts) :form)
                    (= (:type opts) :sign-in)
                    (:keep-alive opts)
                    (:selector opts)
                    (:filter-signals opts)
                    (:retry-interval opts)
                    (:retry-scaler opts)
                    (:retry-max-wait-ms opts))
            (.append sb ", "))
          (.append sb "retryMaxCount: ")
          (.append sb retry-max-count))
        (when-let [request-cancellation (:request-cancellation opts)]
          (when (or (= (:type opts) :form)
                    (= (:type opts) :sign-in)
                    (:keep-alive opts)
                    (:selector opts)
                    (:filter-signals opts)
                    (:retry-interval opts)
                    (:retry-scaler opts)
                    (:retry-max-wait-ms opts)
                    (:retry-max-count opts))
            (.append sb ", "))
          (.append sb "requestCancellation: '")
          (.append sb request-cancellation)
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
            :keep-alive - Whether to keep SSE connections alive when tab is hidden
            :dev-mode - When true, enables additional development features like signal change logging
            :push - Push notification options (when present, exposes VAPID public key)"
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
           [:script {:src "/squint.core.umd@0.9.182.js"}]
           [:script {:type "module" :src "/weave.js"}]
           (when-let [push-opts (:push opts)]
             [:script
              (str "window.WEAVE_VAPID_PUBLIC_KEY = '" (:vapid-public-key push-opts) "';")])
           [:script {:type "module"}
            (let [keep-alive (get-in opts [:sse :keep-alive] false)
                  dev-mode (get opts :dev-mode false)]
              (str "weave.setup('" server-id "', " keep-alive ", " dev-mode ");"))]
           (:head opts)]
          [:body {:class "w-full h-full"}
           (when (:dev-mode opts)
             [:div {:id "weave-dev-debug"
                    :class "fixed top-1 right-1 z-50 flex flex-col items-end gap-2"
                    :data-signals-weave-signals "false"}
              [:button {:class "bg-blue-600 hover:bg-blue-700 text-white text-xs px-3 py-1 rounded shadow-lg border border-blue-500"
                        :data-on-click "$weaveSignals = !$weaveSignals"}
               "↖"]
              [:div {:class "bg-gray-900 text-green-400 text-xs p-3 rounded shadow-lg max-w-md max-h-96 overflow-auto"
                     :data-show "$weaveSignals"
                     :style "display: none;"}
               [:div {:class "flex justify-between items-center mb-2"}]
               [:pre {:data-json-signals ""
                      :class "whitespace-pre-wrap break-words text-xs"}]]])
           [:div {:id "weave-main" :class "w-full h-full"}]]]]))
      (resp/content-type "text/html")
      (resp/charset "UTF-8")))

(defmulti app-loader
  "Renders the application content based on SSE configuration.
   Dispatches based on whether SSE is enabled in the options."
  (fn [_req _server-id _view options]
    (get-in options [:sse :enabled])))

(defmethod app-loader true
  [_req _server-id view _options]
  (->sse-response
   {hk-gen/on-open
    (fn [sse-gen]
      (d*/patch-elements!
       sse-gen
       (c/html
        [:div {:id "weave-main"
               :class "w-full h-full"}
         (binding [*sse-gen* sse-gen]
           (view))]))
      (session/add-connection!
       *session-id* *instance-id* sse-gen)
      (session/record-activity!
       *session-id* *instance-id*))
    hk-gen/on-close
    (fn [_sse-gen _status]
      (session/remove-connection!
       *session-id* *instance-id*))}))

(defmethod app-loader false
  [_req _server-id view _options]
  (->sse-response
   {hk-gen/on-open
    (fn [sse-gen]
      (d*/patch-elements!
       sse-gen
       (c/html
        [:div {:id "weave-main"
               :class "w-full h-full"}
         (binding [*sse-gen* sse-gen]
           (view))]))
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

(defn- wrap-stale-check
  "Middleware that detects stale connections by checking server-id and CSRF.
   If either fails, triggers reload via SSE."
  [handler]
  (fn [request]
    (let [headers (:headers request)
          client-server-id (get headers "x-server-id")]
      (if client-server-id
        ;; Internal request - check server-id and CSRF
        (let [sid (session/get-sid request)
              csrf-token (get headers "x-csrf-token")
              valid? (and (= client-server-id *server-id*)
                          (session/verify-csrf sid csrf-token))]
          (if valid?
            (handler request)
            (hk-gen/->sse-response
             request
             {hk-gen/on-open
              (fn [sse-gen]
                (d*/execute-script! sse-gen "weave.reload();")
                (d*/close-sse! sse-gen))})))
        ;; Not internal - pass through
        (handler request)))))

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
  (let [explicit-opts (or (meta args) {})
        body-hash (hash body)]
    `(let [arg-hash# (mapv hash ~args)
           cache-key# [~body-hash arg-hash#]
           route-hash# (Integer/toUnsignedString (hash cache-key#))
           merged-opts# (merge *handler-options* ~explicit-opts)]
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
                    (let [auth-required?# (:auth-required? merged-opts#)]
                      (if (and auth-required?#
                               (not (authenticated? *request*)))
                        {:status 403, :headers {}, :body nil}
                        (do
                          (session/record-activity!
                           *session-id* *instance-id*)
                          (hk-gen/->sse-response
                           *request*
                           {hk-gen/on-open
                            (fn [sse-gen#]
                              (binding [*sse-gen* sse-gen#]
                                ~@body)
                              (d*/close-sse! sse-gen#))})))))))
               route# (str "/h/" route-hash#)
               base-expr# (str "@call('" route# "', " (#'request-options merged-opts#) ")")
               dstar-expr# (if-let [confirm-msg# (:confirm merged-opts#)]
                             (let [escaped-msg# (clojure.string/replace confirm-msg# "'" "\\'")]
                               (str "confirm('" escaped-msg# "') && " base-expr#))
                             base-expr#)]
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

(defn- to-camel-case
  [x]
  (let [s (name x)
        v (if (and (seq s) (= (first s) \_))
            (str "_" (csk/->camelCase (subs s 1)))
            (csk/->camelCase s))]
    (if (keyword? x) (keyword v) v)))

(defn- deep-merge
  "Recursively merges maps. If values are not maps, the second value wins."
  [& maps]
  (letfn [(reconcile-keys [val-in-result val-in-latter]
            (if (and (map? val-in-result)
                     (map? val-in-latter))
              (merge-with reconcile-keys val-in-result val-in-latter)
              val-in-latter))
          (reconcile-maps [result latter]
            (merge-with reconcile-keys result latter))]
    (reduce reconcile-maps maps)))

(defn- resolve-signal-fns
  "Walk through a signal map and resolve any function values."
  [signal]
  (reduce-kv
   (fn [m k v]
     (assoc m k
            (cond
              (fn? v) (v)
              (map? v) (resolve-signal-fns v)
              :else v)))
   {}
   signal))

(defn push-signal!
  "Send updated signal values to the specific browser tab/window that
   triggered the current handler.

   If any signal value is a function, it will be called and the result
   will be used as the value. This allows for dynamic signal defaults."
  [signal]
  (let [resolved (resolve-signal-fns signal)]
    (set! *signals* (deep-merge *signals* resolved))
    (d*/patch-signals!
     (sse-conn) (->> resolved
                     (cske/transform-keys to-camel-case)
                     (charred/write-json-str)))))

(defn push-path!
  "Change the URL hash for the specific browser tab/window that
   triggered the current handler."
  ([url]
   (push-path! url nil))
  ([url view-fn]
   (set! *app-path* url)
   (push-script! (str "weave.pushHistoryState('" url "');"))
   (when view-fn
     (push-html! (view-fn)))))

(defn broadcast-path!
  "Change the URL hash for all browser tabs/windows that share the same
   session ID."
  ([url]
   (broadcast-path! url nil))
  ([url view-fn]
   (let [connections (session/session-connections *session-id*)]
     (set! *app-path* url)
     (doseq [sse connections]
       (binding [*sse-gen* sse]
         (push-script! (str "weave.pushHistoryState('" url "');"))))
     (when view-fn
       (doseq [sse connections]
         (d*/patch-elements! sse (c/html (view-fn))))))))

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
              :dev-mode - When true, enables additional development features like signal change logging (default: false)
              :handlers - A vector of custom route handlers (Compojure routes) that
                          will be included in the application's routing system
              :middleware - A sequence of middleware functions to apply to the handler chain
              :csrf-secret - Secret for CSRF token generation
              :jwt-secret - Secret for JWT token generation/validation
              :handler-options - A map of default options that will be merged with handler-specific options.
                                Handler-specific options (provided via metadata) will override these defaults.
              :secure-handlers - DEPRECATED. Use :handler-options {:auth-required? true} instead.
                                When true, all handlers require authentication by default
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
              :push - A map of Web Push notification options:
                      :vapid-public-key - VAPID public key (base64url encoded)
                      :vapid-private-key - VAPID private key (base64url encoded)
                      :vapid-subject - Contact URI (mailto: or https:)
                      :save-subscription! - (fn [session-id subscription] ...) to store subscriptions
                      :delete-subscription! - (fn [session-id endpoint] ...) to remove subscriptions
                      :get-subscriptions - (fn [session-id] ...) to retrieve subscriptions for a session
                      :get-all-subscriptions - (fn [] ...) optional, for broadcast-notification!

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
        push-opts (:push options)
        push-routes (when push-opts
                      [(POST "/weave/v1/push/subscribe" request (push/subscribe-handler request push-opts))
                       (POST "/weave/v1/push/unsubscribe" request (push/unsubscribe-handler request push-opts))
                       (GET "/weave/v1/push/vapid-public-key" request (push/vapid-key-handler request push-opts))])
        custom-routes (concat (or (:handlers options) [])
                              (or icon-routes [])
                              (or push-routes []))
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
                          wrap-stale-check
                          (def/wrap-defaults site-defaults)
                          (wrap-gzip))
        handler-chain (if (:middleware options)
                        (reduce (fn [handler middleware-fn]
                                  (middleware-fn handler))
                                handler-chain
                                (reverse (:middleware options)))
                        handler-chain)
        _ (when (:secure-handlers options)
            (println "WARNING: :secure-handlers is deprecated. Use :handler-options {:auth-required? true} instead."))
        handler
        (fn [request]
          (binding [*view* view
                    *server-id* server-id
                    session/*csrf-keyspec* csrf-keyspec
                    *handler-options* (or (:handler-options options) {})]
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
