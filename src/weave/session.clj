(ns weave.session
  (:require
   [charred.api :as charred]
   [clojure.string])
  (:import
   [java.util Base64 Base64$Encoder UUID]
   [javax.crypto Mac]
   [javax.crypto.spec SecretKeySpec]))

(def ^Base64$Encoder base64-encoder
  (.withoutPadding (Base64/getUrlEncoder)))

(defn bytes->base64 [b]
  (.encodeToString base64-encoder b))

(def ^:dynamic *csrf-keyspec* nil)

(defn secret-key->hmac-sha256-keyspec [secret-key]
  (SecretKeySpec. (.getBytes ^java.lang.String secret-key) "HmacSHA256"))

(defn hmac-sha256
  [key-spec data]
  (-> (doto (Mac/getInstance "HmacSHA256")
        (.init key-spec))
      (.doFinal (.getBytes ^java.lang.String data))
      bytes->base64))

(defn get-sid [req]
  (try
    (some->> (get-in req [:headers "cookie"])
             (re-find #"weave-sid=([^;^ ]+)")
             second)
    (catch Throwable _)))

(defn session-cookie [sid]
  (str "weave-sid=" sid "; Path=/; HttpOnly; SameSite=Lax; Max-Age=86400"))

(defn csrf-cookie [csrf]
  (str "weave-csrf=" csrf "; Path=/; SameSite=Lax; Max-Age=86400"))

(def jwt-secret (atom nil))

(defn create-jwt [payload secret-key]
  (let [header (charred/write-json-str {:alg "HS256" :typ "JWT"})
        encoded-header (bytes->base64 (.getBytes ^String header))
        encoded-payload (bytes->base64 (.getBytes ^String (charred/write-json-str payload)))
        signature-data (str encoded-header "." encoded-payload)
        key-spec (secret-key->hmac-sha256-keyspec secret-key)
        signature (hmac-sha256 key-spec signature-data)]
    (str encoded-header "." encoded-payload "." signature)))

(defn verify-jwt [token secret-key]
  (let [[header-b64 payload-b64 signature] (clojure.string/split token #"\.")
        signature-data (str header-b64 "." payload-b64)
        key-spec (secret-key->hmac-sha256-keyspec secret-key)
        expected-signature (hmac-sha256 key-spec signature-data)]
    (when (= signature expected-signature)
      (charred/read-json
       (String. (.decode
                 ^java.util.Base64$Decoder (Base64/getUrlDecoder)
                 (.getBytes ^String payload-b64)))
       :key-fn keyword))))

(defn auth-cookie [jwt]
  (str "weave-auth=" jwt "; Path=/; SameSite=Lax; Max-Age=86400"))

(defn verify-csrf
  "Verifies that the CSRF token is valid for the given session ID"
  [sid csrf-token]
  (when (and sid csrf-token)
    (let [expected-csrf (hmac-sha256 *csrf-keyspec* sid)]
      (= csrf-token expected-csrf))))

(defn wrap-session
  [handler jwt-secret]
  (fn [req]
    (let [sid (get-sid req)
          csrf-token (get-in req [:headers "x-csrf-token"])
          auth-token (some->> (get-in req [:headers "cookie"])
                              (re-find #"weave-auth=([^;^ ]+)")
                              second)
          auth-data (when auth-token
                      (verify-jwt auth-token jwt-secret))]

      (cond
        (verify-csrf sid csrf-token)
        (handler (assoc req :identity auth-data))

        (= (:request-method req) :get)
        (let [new-sid (or sid (str (UUID/randomUUID)))
              csrf    (hmac-sha256 *csrf-keyspec* new-sid)]
          (-> (handler (assoc req :identity auth-data))
              (assoc-in [:headers "Set-Cookie"]
                        [(session-cookie new-sid) (csrf-cookie csrf)])))

        :else
        {:status 403}))))

(defn sign-in [user-data]
  (let [jwt (create-jwt (assoc user-data :authenticated true) @jwt-secret)]
    (auth-cookie jwt)))

(defn sign-out []
  "weave-auth=; Path=/; SameSite=Lax; Max-Age=0")

;; Maps {session-id -> {instance-id -> sse-generator}}
(def ^{:private true} !connections (atom {}))

;; Maps {session-id -> {instance-id -> last-activity-timestamp}}
(def ^{:private true} !activity (atom {}))

(defn add-connection! [session-id instance-id sse-gen]
  (swap! !connections update session-id
         (fn [session-connections]
           (assoc (or session-connections {}) instance-id sse-gen))))

(defn remove-connection! [session-id instance-id]
  (swap! !connections update session-id
         (fn [session-connections]
           (let [new-connections (dissoc (or session-connections {}) instance-id)]
             (if (empty? new-connections)
               nil
               new-connections))))
  (swap! !activity update session-id
         (fn [session-activity]
           (let [new-activity (dissoc (or session-activity {}) instance-id)]
             (if (empty? new-activity)
               nil
               new-activity)))))

(defn session-connections [session-id]
  (vals (@!connections session-id)))

(defn instance-connection [session-id instance-id]
  ((@!connections session-id) instance-id))

(defn record-activity!
  "Records the current timestamp as the last activity for the given session instance"
  [session-id instance-id]
  (let [now (System/currentTimeMillis)]
    (swap! !activity update session-id
           (fn [session-activity]
             (assoc (or session-activity {}) instance-id now)))))

(defn last-activity
  "Gets the last activity timestamp for a session instance"
  [session-id instance-id]
  (get-in @!activity [session-id instance-id]))

(defn session-activity
  "Gets all activity data for a session"
  [session-id]
  (@!activity session-id))

(defn all-sessions
  "Gets all session activity data.
   Returns a map of {session-id -> {instance-id -> timestamp}}"
  []
  @!activity)
