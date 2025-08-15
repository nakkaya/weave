# Session Management

Weave provides a session management system that handles
authentication, CSRF protection, and server-sent event (SSE)
connections.

## How Sessions Work in Weave

Weave's session system uses three cookies to manage user state:

1. **weave-sid**: The session ID cookie that uniquely identifies a browser session
2. **weave-csrf**: A CSRF token that protects against cross-site request forgery attacks
3. **weave-auth**: A JWT (JSON Web Token) that stores authenticated user information

### Session Flow

1. When a user first visits a Weave application:
    - A unique session ID is generated
    - A CSRF token is derived from this session ID
    - Both are set as cookies in the browser

2. For authenticated sessions:
    - When a user signs in, their identity information is stored in a JWT
    - The JWT is set as the `weave-auth` cookie
    - Subsequent requests include this cookie, allowing the server to
      verify the user's identity

3. For all requests:
    - The CSRF token must be included in the `x-csrf-token` header
    - The server verifies that the CSRF token matches the expected
      value for the session ID
    - If valid, the request proceeds; otherwise, it's rejected with a
      403 status

## Managing Connections

Weave tracks active browser connections using a combination of session
ID and instance ID:

- Each browser tab/window gets a unique instance ID
- Multiple tabs can share the same session ID
- This allows Weave to:
   - Push updates to specific tabs (`push-html!`)
   - Broadcast to all tabs for a user (`broadcast-html!`)

## Session Management Functions

### Authentication

```clojure
;; Sign in a user and get the auth cookie string
(weave/set-cookie! (session/sign-in {:name "username" :role "admin"}))

;; Sign out a user by clearing the auth cookie
(weave/set-cookie! (session/sign-out))
```

### Using `set-cookie!`

The `set-cookie!` function is a key part of session management in
Weave. The function works by sending JavaScript that sets the
document.cookie value, which updates or creates the specified cookie
in the browser.

```clojure
;; Basic usage
(weave/set-cookie! "mycookie=value; Path=/; Max-Age=86400")

;; Sign in example
(weave/handler []
  (weave/set-cookie! 
    (session/sign-in {:name "Weave" :role "User"}))
  (weave/push-reload!))

;; Sign out example
(weave/handler []
  (weave/set-cookie! (session/sign-out))
  (weave/push-path! "/sign-in"))
```

## Session Activity Tracking

Weave automatically tracks the last activity timestamp for each
session instance whenever a handler is called. This enables you to:

1. Monitor when users were last active
2. Automatically logout stale sessions
3. Build features like "active users" displays

### Activity Tracking Functions

```clojure
(require '[weave.session :as session])

;; Get the last activity timestamp for a specific session instance
(session/last-activity session-id instance-id)
;; => 1672531200000 (timestamp in milliseconds)

;; Get all activity data for a session
;; Returns a map of {instance-id -> timestamp}
(session/session-activity session-id)
;; => {"instance-123" 1672531200000, "instance-456" 1672531150000}

;; Get all session activity data
;; Returns a map of {session-id -> {instance-id -> timestamp}}
(session/session-activities)
;; => {"session-abc" {"instance-123" 1672531200000}
;;     "session-def" {"instance-456" 1672531150000}}

;; Get activity data for a specific session
;; Returns a map of {instance-id -> timestamp}
(session/session-activities "session-abc")
;; => {"instance-123" 1672531200000}
```

## Configuration

When starting a Weave application, you can configure session security:

```clojure
(weave/run view-fn 
  {:csrf-secret "your-csrf-secret"  ;; Secret for CSRF token generation
   :jwt-secret "your-jwt-secret"})  ;; Secret for JWT signing
```

If not provided, Weave will generate random secrets for each server
instance.
