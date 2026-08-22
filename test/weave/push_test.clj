(ns weave.push-test
  (:require
   [charred.api :as charred]
   [clojure.test :refer [deftest is testing]]
   [weave.push :as push]))

(defn- req
  "A request map for the push handlers. The identity mimics the
   verified JWT identity that wrap-session attaches to requests."
  [body & {:keys [identity]}]
  {:identity identity
   :body (charred/write-json-str body)})

(defn- push-opts
  [storage]
  {:vapid-public-key "public"
   :vapid-private-key "private"
   :vapid-subject "mailto:test@example.com"
   :save-subscription! (fn [id sub] (swap! storage assoc id sub))
   :delete-subscription! (fn [id _] (swap! storage dissoc id))
   :get-subscriptions (fn [id] (when-let [sub (get @storage id)] [sub]))})

(deftest subscribe-requires-authentication
  (testing "unauthenticated requests are rejected with 403 and nothing is saved"
    (let [storage (atom {})]
      (is (= 403 (:status (push/subscribe-handler
                           (req {:endpoint "https://push.example.com/x"
                                 :keys {:p256dh "k" :auth "a"}})
                           (push-opts storage)))))
      (is (empty? @storage)))))

(deftest subscribe-keys-on-authenticated-identity
  (testing "without a client-supplied id the subscription is keyed on the identity id"
    (let [storage (atom {})]
      (is (= 200 (:status (push/subscribe-handler
                           (req {:endpoint "https://push.example.com/attacker"
                                 :keys {:p256dh "k" :auth "a"}}
                                :identity {:id "user-1" :authenticated true})
                           (push-opts storage)))))
      (is (contains? @storage "user-1")))))

(deftest subscribe-rejects-id-mismatch
  (testing "a client-supplied id differing from the identity is rejected with 403"
    (let [storage (atom {})]
      (is (= 403 (:status (push/subscribe-handler
                           (req {:id "victim-id"
                                 :endpoint "https://push.example.com/attacker"
                                 :keys {:p256dh "k" :auth "a"}}
                                :identity {:id "user-1"})
                           (push-opts storage)))))
      (is (empty? @storage)))))

(deftest subscribe-accepts-matching-id
  (testing "a client-supplied id that matches the identity is accepted"
    (let [storage (atom {})]
      (is (= 200 (:status (push/subscribe-handler
                           (req {:id "user-1"
                                 :endpoint "https://push.example.com/x"
                                 :keys {:p256dh "k" :auth "a"}}
                                :identity {:id "user-1"})
                           (push-opts storage)))))
      (is (contains? @storage "user-1")))))

(deftest subscribe-uses-subject-claim
  (testing "the JWT subject claim is preferred as the subscription id"
    (let [storage (atom {})]
      (is (= 200 (:status (push/subscribe-handler
                           (req {:endpoint "https://push.example.com/x"
                                 :keys {:p256dh "k" :auth "a"}}
                                :identity {:sub "sub-id" :id "id-claim"})
                           (push-opts storage)))))
      (is (contains? @storage "sub-id"))
      (is (not (contains? @storage "id-claim"))))))

(deftest unsubscribe-requires-authentication
  (testing "unauthenticated requests are rejected with 403 and nothing is deleted"
    (let [storage (atom {"user-1" {:endpoint "https://push.example.com/x"
                                   :keys {:p256dh "k" :auth "a"}}})]
      (is (= 403 (:status (push/unsubscribe-handler
                           (req {:endpoint "https://push.example.com/x"})
                           (push-opts storage)))))
      (is (= 1 (count @storage))))))

(deftest unsubscribe-rejects-id-mismatch
  (testing "a client-supplied id differing from the identity is rejected with 403"
    (let [storage (atom {"user-1" {:endpoint "e1"}
                         "user-2" {:endpoint "e2"}})]
      (is (= 403 (:status (push/unsubscribe-handler
                           (req {:id "user-2" :endpoint "e2"}
                                :identity {:id "user-1"})
                           (push-opts storage)))))
      (is (= 2 (count @storage))))))

(deftest unsubscribe-deletes-owned-endpoint
  (testing "an endpoint owned by the authenticated id is deleted"
    (let [storage (atom {"user-1" {:endpoint "e1"}})]
      (is (= 200 (:status (push/unsubscribe-handler
                           (req {:endpoint "e1"} :identity {:id "user-1"})
                           (push-opts storage)))))
      (is (empty? @storage)))))

(deftest unsubscribe-ignores-unowned-endpoint
  (testing "an endpoint that does not belong to the authenticated id is not deleted"
    (let [storage (atom {"user-1" {:endpoint "e1"}
                         "user-2" {:endpoint "e2"}})]
      (is (= 200 (:status (push/unsubscribe-handler
                           (req {:endpoint "e2"} :identity {:id "user-1"})
                           (push-opts storage)))))
      (is (= 2 (count @storage))))))

(deftest unsubscribe-without-get-subscriptions
  (testing "without get-subscriptions, deletion is still keyed on the authenticated id"
    (let [storage (atom {"user-1" {:endpoint "e1"}})
          opts (dissoc (push-opts storage) :get-subscriptions)]
      (is (= 200 (:status (push/unsubscribe-handler
                           (req {:endpoint "e1"} :identity {:id "user-1"})
                           opts))))
      (is (empty? @storage)))))
