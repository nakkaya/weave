# Progressive Web Apps (PWA)

Weave includes built-in support for Progressive Web Apps (PWAs),
allowing your applications to be installed on devices and provide a
more app-like experience.

## PWA Support in Weave

Weave automatically generates and serves the necessary resources for
PWAs:

1. **Web App Manifest**: A JSON file that tells the browser about your
   application
2. **Icons**: Various sized icons for different devices and contexts

Note: Currently, Weave does not automatically generate a service worker,
which is required for full PWA functionality like offline support. If you
need offline capabilities, you'll need to implement a custom service worker
and register it in your application.

## Configuration

To enable PWA features in your Weave application, provide an `:icon`
path and optional `:pwa` configuration when starting your application:

```clojure
(weave/run view-fn
  {:title "My PWA App"
   :icon "public/my-icon.png"  ;; Path to an icon in your classpath
   :pwa {:name "My PWA App"
         :short-name "MyApp"
         :description "A description of my application"
         :display "standalone"
         :background-color "#f2f2f2"
         :theme-color "#4a86e8"
         :start-url "/"}})
```

### Icon Requirements

The icon you provide should be:

 - A PNG image
 - High resolution (at least 512x512 pixels recommended)
 - Located in your classpath (typically in resources directory)

Weave will automatically resize your icon to create:

 - `/favicon.png` (32x32) - For browser tabs
 - `/icon-180.png` (180x180) - For iOS home screens
 - `/icon-192.png` (192x192) - For Android home screens
 - `/icon-512.png` (512x512) - For high-resolution displays

### Web App Manifest Options

The `:pwa` configuration map supports these options:

| Option              | Description                     | Default                  |
|---------------------|---------------------------------|--------------------------|
| `:name`             | Full application name           | Value of `:title` option |
| `:short-name`       | Short name for app icons        | Same as `:name`          |
| `:description`      | App description                 | None                     |
| `:display`          | Display mode                    | `"standalone"`           |
| `:background-color` | Background color during loading | `"#f2f2f2"`              |
| `:theme-color`      | Theme color for browser UI      | `"#ffffff"`              |
| `:start-url`        | URL to load when app launches   | `"/"`                    |

## Testing Your PWA

Modern browsers provide tools to test PWA functionality:

1. In Chrome, open DevTools and go to the "Application" tab
2. Check the "Manifest" section to verify your web app manifest
3. Use Lighthouse (in the "Audits" tab) to test PWA compliance

## Installation Experience

When users visit your Weave application in a supported browser:

1. After some engagement, the browser may show an "Add to Home Screen" prompt
2. Users can also manually install the app through the browser menu
3. Once installed, the app will appear on the device's home screen or app launcher
4. When launched, it will open in a standalone window without browser UI

## Push Notifications

### Setup

1. **Generate VAPID keys** (once, store securely):

```clojure
(require '[weave.push :as push])

(push/generate-vapid-keypair)
;; => {:public-key "BEl62i..." :private-key "..."}
```

2. **Define push options**:

```clojure
(def subscriptions (atom {}))

(def push-opts
  {:vapid-public-key  (System/getenv "VAPID_PUBLIC_KEY")
   :vapid-private-key (System/getenv "VAPID_PRIVATE_KEY")
   :vapid-subject     "mailto:admin@example.com"
   :save-subscription!    (fn [id subscription]
                            (swap! subscriptions assoc id subscription))
   :delete-subscription!  (fn [id endpoint]
                            (swap! subscriptions dissoc id))
   :get-subscriptions     (fn [id]
                            (when-let [sub (get @subscriptions id)]
                              [sub]))})
```

3. **Start server with push enabled**:

```clojure
(weave/run my-view
  {:icon "public/icon.png"
   :push push-opts})
```

### Client-Side Subscription

Use the `weave.push` JavaScript API to subscribe users:

```clojure
;; Session-based (default)
[::c/button
 {:data-on-click (weave/handler []
                   (weave/push-script!
                    "weave.push.subscribe()"))}
 "Enable Notifications"]

;; User-based (pass user ID)
[::c/button
 {:data-on-click (weave/handler []
                   (let [user-id (get-in weave/*request* [:identity :id])]
                     (weave/push-script!
                      (str "weave.push.subscribe('" user-id "')"))))}
 "Enable Notifications"]
```

Available JavaScript functions:

| Function                       | Description                              |
|--------------------------------|------------------------------------------|
| `weave.push.isSupported()`     | Check if browser supports push           |
| `weave.push.getPermission()`   | Get current permission state             |
| `weave.push.isSubscribed()`    | Check if currently subscribed            |
| `weave.push.subscribe(id?)`    | Subscribe (optional id, defaults to session) |
| `weave.push.unsubscribe(id?)`  | Unsubscribe (optional id, defaults to session) |

### Sending Notifications

Use `push/send!` to send notifications:

```clojure
;; From a background job
(push/send! "user-123"
            {:title "New Message"
             :body "You have a new message from Alice"
             :url "/#/messages"}
            push-opts)
```

### Notification Payload

| Field    | Description                            |
|----------|----------------------------------------|
| `:title` | Notification title (required)          |
| `:body`  | Body text                              |
| `:url`   | URL to open when clicked               |
| `:icon`  | Icon URL                               |
| `:badge` | Badge icon URL                         |
| `:tag`   | Tag for grouping notifications         |
| `:data`  | Additional data for the service worker |
