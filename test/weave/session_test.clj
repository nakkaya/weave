(ns weave.session-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [weave.session :as session]))

(def ^:private secret-key "test-secret")

(defn- tamper-last-char
  "Returns the given string with its last character changed,
   preserving the length."
  [s]
  (str (subs s 0 (dec (count s)))
       (if (= \A (last s)) "B" "A")))

(defn- tamper-signature
  "Returns the given token with the last character of the
   signature part changed, preserving the signature length."
  [token]
  (let [[header payload signature] (str/split token #"\.")]
    (str header "." payload "." (tamper-last-char signature))))

(deftest verify-jwt-test
  (testing "accepts a valid token"
    (let [token (session/create-jwt {:user "alice"} secret-key)]
      (is (= {:user "alice"}
             (session/verify-jwt token secret-key)))))

  (testing "rejects a token with a tampered signature"
    (let [token (session/create-jwt {:user "alice"} secret-key)]
      (is (nil? (session/verify-jwt (tamper-signature token) secret-key)))))

  (testing "rejects a token signed with a different secret"
    (let [token (session/create-jwt {:user "alice"} secret-key)]
      (is (nil? (session/verify-jwt token "another-secret")))))

  (testing "rejects a token whose payload was tampered with"
    (let [[header _ signature] (str/split (session/create-jwt {:user "alice"} secret-key) #"\.")
          forged-payload (session/bytes->base64 (.getBytes "{\"user\":\"mallory\"}"))]
      (is (nil? (session/verify-jwt (str header "." forged-payload "." signature) secret-key)))))

  (testing "handles malformed tokens without throwing"
    (is (nil? (session/verify-jwt "" secret-key)))
    (is (nil? (session/verify-jwt "header.payload" secret-key)))))

(deftest verify-csrf-test
  (testing "accepts a valid token and rejects invalid ones"
    (binding [session/*csrf-keyspec* (session/secret-key->hmac-sha256-keyspec secret-key)]
      (let [csrf (session/hmac-sha256 session/*csrf-keyspec* "session-id")]
        (is (true? (session/verify-csrf "session-id" csrf)))
        (is (false? (session/verify-csrf "session-id" (tamper-last-char csrf))))
        (is (false? (session/verify-csrf "other-session-id" csrf)))
        (is (nil? (session/verify-csrf nil csrf)))
        (is (nil? (session/verify-csrf "session-id" nil)))))))

(deftest constant-time-equal?-test
  (testing "matches = semantics for strings"
    (is (true? (session/constant-time-equal? "abc" "abc")))
    (is (false? (session/constant-time-equal? "abc" "abd")))
    (is (false? (session/constant-time-equal? "abc" "xbc")))
    (is (false? (session/constant-time-equal? "ab" "abc")))
    (is (nil? (session/constant-time-equal? nil "abc")))
    (is (nil? (session/constant-time-equal? "abc" nil)))
    (is (nil? (session/constant-time-equal? nil nil)))))
