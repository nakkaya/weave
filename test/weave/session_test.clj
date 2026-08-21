(ns weave.session-test
  (:require
   [charred.api :as charred]
   [clojure.test :refer [deftest is testing]]
   [weave.session :as session]))

(def test-secret "test-jwt-secret")

(defn jwt-with
  "Builds a signed token with the given payload, without the
   exp stamping create-jwt does, for testing verify-jwt."
  [payload secret-key]
  (let [header (charred/write-json-str {:alg "HS256" :typ "JWT"})
        encoded-header (session/bytes->base64 (.getBytes ^String header))
        encoded-payload (session/bytes->base64 (.getBytes ^String (charred/write-json-str payload)))
        signature-data (str encoded-header "." encoded-payload)]
    (str encoded-header "." encoded-payload "."
         (session/hmac-sha256 (session/secret-key->hmac-sha256-keyspec secret-key)
                              signature-data))))

(defn now-seconds []
  (quot (System/currentTimeMillis) 1000))

(deftest create-jwt-test
  (testing "create-jwt adds an exp claim aligned with the session TTL"
    (let [jwt (session/create-jwt {:name "TestUser"} test-secret)
          payload (session/verify-jwt jwt test-secret)]
      (is (= "TestUser" (:name payload)))
      (is (integer? (:exp payload)))
      (is (> (:exp payload) (now-seconds)))
      (is (<= (- (:exp payload) (now-seconds))
              session/session-ttl-seconds))))

  (testing "create-jwt overrides any caller-supplied exp"
    (let [jwt (session/create-jwt {:name "TestUser" :exp (+ (now-seconds) 999999)}
                                  test-secret)
          payload (session/verify-jwt jwt test-secret)]
      (is (<= (- (:exp payload) (now-seconds))
              session/session-ttl-seconds)))))

(deftest sign-in-test
  (testing "sign-in emits a cookie whose token verifies and carries an exp claim"
    (reset! session/jwt-secret test-secret)
    (let [cookie (session/sign-in {:name "TestUser"})
          token (second (re-find #"weave-auth=([^;]+)" cookie))
          payload (session/verify-jwt token test-secret)]
      (is (true? (:authenticated payload)))
      (is (> (:exp payload) (now-seconds))))))

(deftest verify-jwt-test
  (testing "verify-jwt returns the payload for a token with a future exp"
    (let [jwt (jwt-with {:name "TestUser" :exp (+ (now-seconds) 3600)} test-secret)]
      (is (= {:name "TestUser"}
             (dissoc (session/verify-jwt jwt test-secret) :exp)))))

  (testing "verify-jwt rejects an expired token"
    (let [jwt (jwt-with {:name "TestUser" :exp (- (now-seconds) 3600)} test-secret)]
      (is (nil? (session/verify-jwt jwt test-secret)))))

  (testing "verify-jwt rejects a token without an exp claim"
    (let [jwt (jwt-with {:name "TestUser"} test-secret)]
      (is (nil? (session/verify-jwt jwt test-secret)))))

  (testing "verify-jwt rejects a tampered token"
    (let [jwt (session/create-jwt {:name "TestUser"} test-secret)
          tampered (str (subs jwt 0 (dec (count jwt))) "x")]
      (is (nil? (session/verify-jwt tampered test-secret)))))

  (testing "verify-jwt rejects a token signed with a different secret"
    (let [jwt (session/create-jwt {:name "TestUser"} test-secret)]
      (is (nil? (session/verify-jwt jwt "other-secret"))))))
