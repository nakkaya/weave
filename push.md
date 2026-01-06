# Weave Push Notification Support Plan

## Overview

Add Web Push notification support to the Weave framework, following the same pattern as the existing PWA support (opt-in via configuration).

## Architecture

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Browser   │────▶│ Push Service│────▶│Weave Server │
│   (Client)  │◀────│ (FCM/VAPID) │◀────│  (Clojure)  │
└─────────────┘     └─────────────┘     └─────────────┘
       │                                       │
       ▼                                       ▼
┌─────────────┐                         ┌─────────────┐
│Service Worker│                        │ App-provided│
│  (weave.js) │                         │  storage fn │
└─────────────┘                         └─────────────┘
```

## Design Principles

1. **Opt-in** - Push is only enabled when `:push` config is provided (like `:icon` for PWA)
2. **Storage agnostic** - Weave provides the protocol, apps provide storage implementation
3. **Session-aware** - Integrate with existing `weave.session` for user identification
4. **Minimal dependencies** - Use Java's built-in crypto where possible
5. **Manual control** - Users trigger subscription via `push-script!`, no automatic UI components

## Configuration

```clojure
(weave/run my-view
  {:icon "app-icon.png"
   :push {:vapid-public-key  "BEl62i..."
          :vapid-private-key "your-private-key"
          :vapid-subject     "mailto:admin@example.com"
          ;; App-provided storage functions
          :save-subscription!    (fn [session-id subscription] ...)
          :delete-subscription!  (fn [session-id endpoint] ...)
          :get-subscriptions     (fn [session-id] ...)}})
```

## Implementation Plan

### Phase 1: Core Infrastructure (`weave.push` namespace)

#### 1.1 VAPID Key Management
- [ ] Function to generate VAPID key pairs (for documentation/tooling)
- [ ] Function to load/validate VAPID keys from config
- [ ] JWT creation for VAPID authentication headers

#### 1.2 Web Push Protocol
- [ ] Encrypt notification payload (ECDH + AES-GCM per Web Push spec)
- [ ] Build HTTP request with proper headers (Authorization, Crypto-Key, TTL, etc.)
- [ ] Send to push service endpoint (use http-kit client)
- [ ] Handle responses (201 success, 410 expired subscription, etc.)

#### 1.3 Subscription Management Protocol
Define a protocol that apps implement for storage:

```clojure
;; Subscription map structure:
{:endpoint "https://fcm.googleapis.com/fcm/send/..."
 :keys {:p256dh "BNc..."
        :auth "tB..."}}
```

### Phase 2: Server-Side API

#### 2.1 Push Functions (in `weave.core`)
```clojure
;; Send to current session (all subscriptions for session-id)
(push-notification! {:title "Hello" :body "World" :url "/"})

;; Send to all sessions (requires app to provide all-subscriptions fn)
(broadcast-notification! {:title "Hello" :body "World"})
```

#### 2.2 Automatic Routes (when `:push` configured)
- `POST /weave/v1/push/subscribe` - Save subscription for current session
- `POST /weave/v1/push/unsubscribe` - Remove subscription for current session
- `GET /weave/v1/push/vapid-public-key` - Return public key for client

### Phase 3: Client-Side Integration

#### 3.1 Service Worker Enhancement
Add to generated service worker (or weave.js):
```javascript
self.addEventListener('push', function(event) { ... });
self.addEventListener('notificationclick', function(event) { ... });
```

#### 3.2 Client Helper Functions
Add to weave.js:
```javascript
weave.push.subscribe()      // Request permission & subscribe
weave.push.unsubscribe()    // Unsubscribe
weave.push.isSubscribed()   // Check subscription status
weave.push.isSupported()    // Check browser support
```

#### 3.3 HTML Head Updates
When push is enabled, add to `app-outer`:
```html
<script>window.WEAVE_VAPID_PUBLIC_KEY = "...";</script>
```

## Dependencies to Add

```clojure
;; deps.edn - no new dependencies needed!
;; Java provides:
;; - java.security for ECDH key agreement
;; - javax.crypto for AES-GCM encryption
;; - http-kit (already included) for HTTP client
```

## File Structure

```
src/weave/
├── core.clj          ; Add push-notification!, routes
├── push.clj          ; NEW: VAPID, encryption, Web Push protocol
├── session.clj       ; Already has session management
└── ...

resources/
└── weave.js          ; Add push subscription helpers
```

## API Summary

### Server-Side (Clojure)
| Function | Description |
|----------|-------------|
| `push-notification!` | Send to current session |
| `broadcast-notification!` | Send to all (requires app fn) |

### Client-Side (JavaScript)
| Function | Description |
|----------|-------------|
| `weave.push.subscribe()` | Request permission & subscribe |
| `weave.push.unsubscribe()` | Unsubscribe from push |
| `weave.push.isSubscribed()` | Check if subscribed |
| `weave.push.isSupported()` | Check browser support |

### Auto-Generated Routes
| Route | Method | Description |
|-------|--------|-------------|
| `/weave/v1/push/subscribe` | POST | Save subscription |
| `/weave/v1/push/unsubscribe` | POST | Remove subscription |
| `/weave/v1/push/vapid-public-key` | GET | Get public key |

## Security Considerations

1. **VAPID private key** - Never expose, validate on startup
2. **Subscription cleanup** - Handle 410 Gone responses by calling `delete-subscription!`
3. **Session binding** - Subscriptions tied to session-id for authorization
4. **HTTPS required** - Push API requires secure context (handled by deployment)

## Testing Strategy

Using real Chrome browser with real FCM (Google's push service) for realistic end-to-end testing.

### Unit Tests (Pure Clojure)

Test crypto and protocol implementation without browser:

```clojure
(deftest vapid-jwt-generation-test
  (testing "generates valid JWT with correct claims"
    (let [keypair (push/generate-vapid-keypair)
          jwt (push/create-vapid-jwt keypair "https://fcm.googleapis.com")]
      (is (string? jwt))
      (is (= 3 (count (str/split jwt #"\.")))))))

(deftest payload-encryption-test
  (testing "encrypts payload according to Web Push spec"
    (let [subscription {:endpoint "https://example.com/push"
                        :keys {:p256dh "test-key" :auth "test-auth"}}
          payload {:title "Test" :body "Hello"}
          encrypted (push/encrypt-payload subscription payload)]
      (is (bytes? encrypted)))))
```

### Browser Integration Tests with Real FCM

Using existing Etaoin test infrastructure with real push services:

```clojure
(def test-vapid-keys
  ;; Generate once and store for tests
  (push/generate-vapid-keypair))

(defn driver-options-with-notifications []
  {:path-driver "chromedriver"
   :args [(str "--user-data-dir=/tmp/chrome-data-" (random-uuid))
          "--no-sandbox"]
   ;; Auto-grant notification permission
   :prefs {"profile.default_content_setting_values.notifications" 1}})

(defn push-test-view []
  [:div#view
   [:div#status "Ready"]
   [:div#last-push "No push received"]
   [:button
    {:id "subscribe-btn"
     :data-on-click
     (core/handler []
       (core/push-script! "weave.push.subscribe()"))}
    "Subscribe"]])

(deftest push-full-flow-test
  (let [subscriptions (atom {})
        push-received (promise)
        push-options {:vapid-public-key (:public test-vapid-keys)
                      :vapid-private-key (:private test-vapid-keys)
                      :vapid-subject "mailto:test@example.com"
                      :save-subscription! (fn [sid sub]
                                            (swap! subscriptions assoc sid sub))
                      :delete-subscription! (fn [sid endpoint]
                                              (swap! subscriptions dissoc sid))
                      :get-subscriptions (fn [sid]
                                           (when-let [sub (get @subscriptions sid)]
                                             [sub]))}]
    (with-browser
      push-test-view
      (assoc weave-options
             :push push-options)

      (testing "VAPID public key is exposed to client"
        (let [key (e/js-execute *browser* "return window.WEAVE_VAPID_PUBLIC_KEY")]
          (is (= (:public test-vapid-keys) key))))

      (testing "Browser subscribes and gets real FCM endpoint"
        (click :subscribe-btn)
        ;; Wait for subscription to be saved
        (e/wait-predicate #(seq @subscriptions) {:timeout 10000})
        (let [[_sid sub] (first @subscriptions)]
          (is (str/starts-with? (:endpoint sub) "https://fcm.googleapis.com"))
          (is (contains? (:keys sub) :p256dh))
          (is (contains? (:keys sub) :auth))))

      (testing "Server sends push via FCM and browser receives it"
        ;; Service worker stores received push in indexedDB or posts message
        (e/js-execute *browser*
          "navigator.serviceWorker.addEventListener('message', e => {
             if (e.data.type === 'push-received') {
               document.getElementById('last-push').textContent = e.data.title;
             }
           });")

        ;; Send push notification
        (let [[sid _] (first @subscriptions)]
          (binding [core/*session-id* sid]
            (core/push-notification! {:title "Test Push" :body "Hello from tests"})))

        ;; Wait for service worker to receive and display
        (e/wait-predicate
         #(= "Test Push" (el-text :last-push))
         {:timeout 15000})))))

(deftest push-unsubscribe-test
  (let [subscriptions (atom {})
        push-options { ... }]
    (with-browser
      push-test-view
      (assoc weave-options :push push-options)

      ;; Subscribe first
      (click :subscribe-btn)
      (e/wait-predicate #(seq @subscriptions))
      (is (= 1 (count @subscriptions)))

      ;; Unsubscribe
      (e/js-execute *browser* "weave.push.unsubscribe()")
      (e/wait-predicate #(empty? @subscriptions) {:timeout 5000})
      (is (empty? @subscriptions)))))
```

### Test Utilities to Add

```clojure
;; In weave.test.browser namespace

(defn with-browser-notifications
  "Like with-browser but with notification permissions granted."
  [view options & body]
  (let [opts (update options :driver-options
                     merge (driver-options-with-notifications))]
    `(with-browser ~view ~opts ~@body)))
```

## Example Usage

```clojure
(ns myapp.core
  (:require [weave.core :as weave]
            [myapp.db :as db]))

(defn my-view []
  [:div
   [:button {:data-on-click "@post('/enable-push')"}
    "Enable Notifications"]])

(weave/handler ["/enable-push"]
  ;; This triggers the browser's permission prompt via push-script!
  (weave/push-script! "weave.push.subscribe()"))

;; Somewhere in your app logic:
(defn notify-user [message]
  (weave/push-notification!
    {:title "New Message"
     :body message
     :url "/messages"}))

;; Start server with push enabled
(weave/run my-view
  {:icon "icon.png"
   :push {:vapid-public-key  (System/getenv "VAPID_PUBLIC")
          :vapid-private-key (System/getenv "VAPID_PRIVATE")
          :vapid-subject     "mailto:admin@myapp.com"
          :save-subscription!   #(db/save-push-sub! %1 %2)
          :delete-subscription! #(db/delete-push-sub! %1 %2)
          :get-subscriptions    #(db/get-push-subs %1)}})
```

## Open Questions

1. Should we provide a reference storage implementation (atom-based for dev)?
2. Should notification payload support icons/badges/actions?
3. Service worker: separate file or inline in weave.js?
