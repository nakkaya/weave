(ns weave.push
  (:require
   [charred.api :as charred]
   [clojure.tools.logging :as log]
   [org.httpkit.client :as http]
   [weave.session :as session])
  (:import
   [java.security KeyFactory KeyPairGenerator SecureRandom]
   [java.security.interfaces ECPrivateKey ECPublicKey]
   [java.security.spec ECGenParameterSpec PKCS8EncodedKeySpec X509EncodedKeySpec]
   [java.util Base64]
   [javax.crypto Cipher KeyAgreement Mac]
   [javax.crypto.spec GCMParameterSpec SecretKeySpec]))

(defn- bytes->base64url
  ^String [^bytes data]
  (-> (Base64/getUrlEncoder)
      (.withoutPadding)
      (.encodeToString data)))

(defn- base64url->bytes
  ^bytes [^String s]
  (.decode (Base64/getUrlDecoder) s))

(defn- str->bytes
  ^bytes [^String s]
  (.getBytes s "UTF-8"))

(def ^:private ^bytes x509-ec-header
  (byte-array [0x30 0x59 0x30 0x13 0x06 0x07 0x2A 0x86
               0x48 0xCE 0x3D 0x02 0x01 0x06 0x08 0x2A
               0x86 0x48 0xCE 0x3D 0x03 0x01 0x07 0x03
               0x42 0x00]))

(defn- blen
  "Get byte array length without reflection."
  ^long [^bytes arr]
  (alength arr))

(defn- pad-to-32-bytes
  ^bytes [^bytes arr]
  (let [result (byte-array 32)
        src-len (blen arr)]
    (System/arraycopy arr (max 0 (- src-len 32))
                      result (max 0 (- 32 src-len))
                      (min 32 src-len))
    result))

(defn- ec-point->raw-bytes
  ^bytes [^ECPublicKey public-key]
  (let [point (.getW public-key)
        x-bytes (pad-to-32-bytes (.toByteArray (.getAffineX point)))
        y-bytes (pad-to-32-bytes (.toByteArray (.getAffineY point)))
        result (byte-array 65)]
    (aset-byte result 0 (unchecked-byte 0x04))
    (System/arraycopy x-bytes 0 result 1 32)
    (System/arraycopy y-bytes 0 result 33 32)
    result))

(defn- raw-bytes->x509
  ^bytes [^bytes raw-bytes]
  (let [header-len (blen x509-ec-header)
        result (byte-array (+ header-len 65))]
    (System/arraycopy x509-ec-header 0 result 0 header-len)
    (System/arraycopy raw-bytes 0 result header-len 65)
    result))

(defn generate-vapid-keypair
  "Generate a new VAPID key pair for Web Push.

   Returns a map with:
   - :public-key  - Base64 URL-encoded raw public key (for browser)
   - :private-key - Base64 URL-encoded PKCS8 private key (for server)

   Generate once and store securely."
  []
  (let [generator (doto (KeyPairGenerator/getInstance "EC")
                    (.initialize (ECGenParameterSpec. "secp256r1") (SecureRandom.)))
        keypair (.generateKeyPair generator)]
    {:public-key (bytes->base64url (ec-point->raw-bytes (.getPublic keypair)))
     :private-key (bytes->base64url (.getEncoded (.getPrivate keypair)))}))

(defn- load-public-key
  ^ECPublicKey [^String base64-key]
  (.generatePublic (KeyFactory/getInstance "EC")
                   (X509EncodedKeySpec. (raw-bytes->x509 (base64url->bytes base64-key)))))

(defn- load-private-key
  ^ECPrivateKey [^String base64-key]
  (.generatePrivate (KeyFactory/getInstance "EC")
                    (PKCS8EncodedKeySpec. (base64url->bytes base64-key))))

(defn- der-to-raw-signature
  ^bytes [^bytes der-sig]
  (let [result (byte-array 64)
        r-len (aget der-sig 3)
        r-start (if (= (aget der-sig 4) 0) 5 4)
        r-len-actual (if (= (aget der-sig 4) 0) (dec r-len) r-len)
        s-offset (+ 4 r-len)
        s-len (aget der-sig (inc s-offset))
        s-start (if (= (aget der-sig (+ s-offset 2)) 0)
                  (+ s-offset 3)
                  (+ s-offset 2))
        s-len-actual (if (= (aget der-sig (+ s-offset 2)) 0) (dec s-len) s-len)]
    (System/arraycopy der-sig r-start result (- 32 r-len-actual) r-len-actual)
    (System/arraycopy der-sig s-start result (- 64 s-len-actual) s-len-actual)
    result))

(defn- create-vapid-jwt
  [^String private-key ^String audience ^String subject]
  (let [header-b64 (bytes->base64url (str->bytes (charred/write-json-str {:typ "JWT" :alg "ES256"})))
        payload-b64 (bytes->base64url (str->bytes (charred/write-json-str
                                                   {:aud audience
                                                    :exp (+ (quot (System/currentTimeMillis) 1000) 43200)
                                                    :sub subject})))
        signing-input (str header-b64 "." payload-b64)
        sig (doto (java.security.Signature/getInstance "SHA256withECDSA")
              (.initSign (load-private-key private-key))
              (.update (str->bytes signing-input)))
        signature (der-to-raw-signature (.sign sig))]
    (str signing-input "." (bytes->base64url signature))))

(defn- hkdf
  ^bytes [^bytes salt ^bytes ikm ^bytes info ^long length]
  (let [mac (Mac/getInstance "HmacSHA256")
        prk (do (.init mac (SecretKeySpec. salt "HmacSHA256"))
                (.doFinal mac ikm))
        info-with-counter (doto (byte-array (inc (blen info)))
                            (#(System/arraycopy info 0 % 0 (blen info)))
                            (aset-byte (blen info) (unchecked-byte 1)))
        okm (do (.init mac (SecretKeySpec. prk "HmacSHA256"))
                (.doFinal mac info-with-counter))]
    (if (<= length (blen okm))
      (let [result (byte-array length)]
        (System/arraycopy okm 0 result 0 length)
        result)
      (throw (ex-info "HKDF length too long" {:length length})))))

(defn- create-info
  ^bytes [^String type]
  (str->bytes (str "Content-Encoding: " type "\0")))

(defn encrypt-payload
  "Encrypt a notification payload for Web Push (RFC 8291 aes128gcm)."
  [subscription payload]
  (let [client-public-bytes (base64url->bytes (get-in subscription [:keys :p256dh]))
        client-auth (base64url->bytes (get-in subscription [:keys :auth]))
        server-keypair (.generateKeyPair (doto (KeyPairGenerator/getInstance "EC")
                                           (.initialize (ECGenParameterSpec. "secp256r1") (SecureRandom.))))
        server-public-bytes (ec-point->raw-bytes (.getPublic server-keypair))
        salt (let [s (byte-array 16)] (.nextBytes (SecureRandom.) s) s)
        key-agreement (doto (KeyAgreement/getInstance "ECDH")
                        (.init (.getPrivate server-keypair))
                        (.doPhase (.generatePublic (KeyFactory/getInstance "EC")
                                                   (X509EncodedKeySpec. (raw-bytes->x509 client-public-bytes)))
                                  true))
        shared-secret (.generateSecret key-agreement)
        auth-info (let [prefix (str->bytes "WebPush: info\0")
                        result (byte-array (+ (blen prefix) 130))]
                    (System/arraycopy prefix 0 result 0 (blen prefix))
                    (System/arraycopy client-public-bytes 0 result (blen prefix) 65)
                    (System/arraycopy server-public-bytes 0 result (+ (blen prefix) 65) 65)
                    result)
        ikm (hkdf client-auth shared-secret auth-info 32)
        cek (hkdf salt ikm (create-info "aes128gcm") 16)
        nonce (hkdf salt ikm (create-info "nonce") 12)
        plaintext (str->bytes (charred/write-json-str payload))
        padded-plaintext (doto (byte-array (inc (blen plaintext)))
                           (#(System/arraycopy plaintext 0 % 0 (blen plaintext)))
                           (aset-byte (blen plaintext) (unchecked-byte 2)))
        cipher (doto (Cipher/getInstance "AES/GCM/NoPadding")
                 (.init Cipher/ENCRYPT_MODE
                        (SecretKeySpec. cek "AES")
                        (GCMParameterSpec. 128 nonce)))
        ciphertext (.doFinal cipher padded-plaintext)
        record-size 4096
        encrypted-payload (byte-array (+ 86 (blen ciphertext)))]
    (System/arraycopy salt 0 encrypted-payload 0 16)
    (aset-byte encrypted-payload 16 (unchecked-byte (bit-shift-right record-size 24)))
    (aset-byte encrypted-payload 17 (unchecked-byte (bit-shift-right record-size 16)))
    (aset-byte encrypted-payload 18 (unchecked-byte (bit-shift-right record-size 8)))
    (aset-byte encrypted-payload 19 (unchecked-byte record-size))
    (aset-byte encrypted-payload 20 (unchecked-byte 65))
    (System/arraycopy server-public-bytes 0 encrypted-payload 21 65)
    (System/arraycopy ciphertext 0 encrypted-payload 86 (blen ciphertext))
    {:encrypted-payload encrypted-payload
     :server-public-key (bytes->base64url server-public-bytes)
     :salt (bytes->base64url salt)}))

(defn- extract-origin
  [^String endpoint]
  (let [url (java.net.URL. endpoint)]
    (str (.getProtocol url) "://" (.getHost url)
         (when (not= (.getPort url) -1)
           (str ":" (.getPort url))))))

(defn send-notification!
  "Send a push notification to a single subscription.

   Returns:
   - {:success true} on success
   - {:success false :status <code> :error <message>} on failure
   - {:success false :expired true} if subscription is no longer valid (410 Gone)"
  [subscription payload vapid-opts]
  (try
    (let [{:keys [endpoint]} subscription
          {:keys [public-key private-key subject]} vapid-opts
          jwt (create-vapid-jwt private-key (extract-origin endpoint) subject)
          raw-public-key (ec-point->raw-bytes (load-public-key public-key))
          {:keys [encrypted-payload]} (encrypt-payload subscription payload)
          response @(http/request
                     {:method :post
                      :url endpoint
                      :headers {"Authorization" (str "vapid t=" jwt ", k=" (bytes->base64url raw-public-key))
                                "Content-Type" "application/octet-stream"
                                "Content-Encoding" "aes128gcm"
                                "TTL" "86400"
                                "Urgency" "normal"}
                      :body encrypted-payload
                      :timeout 30000})
          status (:status response)]
      (cond
        (nil? status)
        {:success false :error (str "No response: " (:error response))}

        (<= 200 status 299)
        {:success true}

        (= 410 status)
        {:success false :expired true :status 410 :error "Subscription expired"}

        :else
        {:success false :status status :error (str "Push failed: " (:body response))}))
    (catch Exception e
      (log/error e "Failed to send push notification")
      {:success false :error (.getMessage e)})))

(defn send-notification-to-subscriptions!
  "Send a notification to multiple subscriptions.
   Returns a map of endpoint -> result."
  [subscriptions payload vapid-opts]
  (->> subscriptions
       (map (fn [sub] [(:endpoint sub) (send-notification! sub payload vapid-opts)]))
       (into {})))

(defn send!
  "Send a Web Push notification to all subscriptions for the given ID.

   Parameters:
   - id: The identifier to look up subscriptions (user-id, session-id, topic, etc.)
   - payload: Notification data {:title :body :url :icon :badge :tag :data}
   - push-opts: Configuration map with VAPID keys and subscription functions

   Returns a map of endpoint -> result for each subscription.
   Automatically removes expired subscriptions."
  [id payload push-opts]
  (let [{:keys [get-subscriptions delete-subscription!]} push-opts
        subscriptions (when get-subscriptions (get-subscriptions id))
        vapid-opts {:public-key (:vapid-public-key push-opts)
                    :private-key (:vapid-private-key push-opts)
                    :subject (:vapid-subject push-opts)}
        results (send-notification-to-subscriptions! subscriptions payload vapid-opts)]
    (doseq [[endpoint result] results
            :when (:expired result)]
      (when delete-subscription!
        (delete-subscription! id endpoint)))
    results))

(defn- parse-body [req]
  (let [body (:body req)]
    (if (instance? java.io.InputStream body)
      (slurp body)
      body)))

(defn vapid-key-handler
  [_req push-opts]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (charred/write-json-str {:vapidPublicKey (:vapid-public-key push-opts)})})

(defn subscribe-handler
  [req push-opts]
  (let [body (charred/read-json (parse-body req) :key-fn keyword)
        id (or (:id body) (session/get-sid req))
        subscription (dissoc body :id)]
    (when-let [save-fn (:save-subscription! push-opts)]
      (save-fn id subscription))
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body "{\"success\":true}"}))

(defn unsubscribe-handler
  [req push-opts]
  (let [{:keys [id endpoint]} (charred/read-json (parse-body req) :key-fn keyword)
        id (or id (session/get-sid req))]
    (when-let [delete-fn (:delete-subscription! push-opts)]
      (when endpoint
        (delete-fn id endpoint)))
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body "{\"success\":true}"}))
