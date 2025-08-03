(ns weave.core-test
  (:require
   [charred.api :as charred]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [compojure.core :refer [GET]]
   [etaoin.api :as e]
   [integrant.core :as ig]
   [weave.browser :refer [*browser* with-browser url weave-options visible? fill click el-text new-tab tabs switch-tab] :as browser]
   [weave.core :as core]
   [weave.session :as session]
   [weave.squint :as squint]))

(defn event-handler-fixture
  "Test fixture that binds a fresh event-handlers atom for each test."
  [test-fn]
  (binding [core/*event-handlers* (atom {})]
    (test-fn)))

(use-fixtures :each event-handler-fixture)

(defmacro test-with-sse-variants
  "Creates two test variants, one with SSE enabled and one with SSE disabled.
   Usage: (test-with-sse-variants test-name view
            ;; test body with access to *browser* binding)"
  [test-name view & test-body]
  (let [test-symbol (if (and (seq? test-name) (= 'quote (first test-name)))
                      (second test-name)
                      test-name)]
    `(do
       (deftest ~(symbol (str test-symbol "-with-sse"))
         (with-browser ~view weave-options
           (testing ~(str (name test-symbol) " with SSE enabled")
             ~@test-body)))
       (deftest ~(symbol (str test-symbol "-without-sse"))
         (with-browser ~view (assoc-in weave-options [:sse :enabled] false)
           (testing ~(str (name test-symbol) " with SSE disabled")
             ~@test-body))))))

(deftest request-headers-test
  (testing "Test request header options"
    (is (= "{}"
           (#'core/request-options {})))
    (is (= "{contentType: 'form'}"
           (#'core/request-options {:type :form})))
    (is (= "{openWhenHidden: true}"
           (#'core/request-options {:keep-alive true})))
    (is (= "{contentType: 'form', openWhenHidden: true}"
           (#'core/request-options {:type :form :keep-alive true})))
    (is (= "{selector: '#myform'}"
           (#'core/request-options {:selector "#myform"})))
    (is (= "{contentType: 'form', selector: '#myform'}"
           (#'core/request-options {:type :form :selector "#myform"})))
    (is (= "{contentType: 'form', openWhenHidden: true, selector: '#myform'}"
           (#'core/request-options
            {:type :form :keep-alive true :selector "#myform"})))
    (is (= "{}"
           (#'core/request-options {:filter-signals {}})))
    (is (= "{filterSignals: { include: /.*test.*/, exclude: /(^|\\.)_/ }}"
           (#'core/request-options {:filter-signals {:include ".*test.*"}})))
    (is (= "{filterSignals: { include: /.*public.*/, exclude: /.*private.*/ }}"
           (#'core/request-options
            {:filter-signals {:include ".*public.*" :exclude ".*private.*"}})))
    (is (= "{contentType: 'form', filterSignals: { include: /.*/, exclude: /.*private.*/ }}"
           (#'core/request-options
            {:type :form :filter-signals {:exclude ".*private.*"}})))))

(defn instance-id-test-view []
  [:div
   [:h1#content "Instance ID Test"]
   [:div#instance-display
    [:p "Instance ID: " [:span#instance-id "Loading..."]]
    [:p "Session Storage: " [:span#session-storage-id "Loading..."]]]
   [:button
    {:id "get-instance-id"
     :data-on-click
     (core/handler []
       (core/push-script!
        (squint/clj->js
         (set! (-> js/document
                   (.getElementById "instance-id")
                   (.-textContent))
               (.instance js/window.weave))
         (set! (-> js/document
                   (.getElementById "session-storage-id")
                   (.-textContent))
               (.getItem js/sessionStorage "weave-tab-id")))))}
    "Get Instance ID"]])

(deftest multiple-tabs-different-instance-ids-test
  (with-browser instance-id-test-view weave-options
    (testing "Test that multiple tabs get different instance IDs"
      (visible? :get-instance-id)

      (click :get-instance-id)
      (e/wait-predicate #(not= "Loading..." (el-text :instance-id)))
      (let [tab1-instance-id (el-text :instance-id)]
        (is (not (str/blank? tab1-instance-id)))

        (new-tab)
        (let [[tab1 tab2] (tabs)]

          (switch-tab tab2)
          (visible? :get-instance-id)
          (click :get-instance-id)
          (e/wait-predicate #(not= "Loading..." (el-text :instance-id)))
          (let [tab2-instance-id (el-text :instance-id)]
            (is (not (str/blank? tab2-instance-id)))

            (is (not= tab1-instance-id tab2-instance-id)
                (str "Tab 1 ID: " tab1-instance-id ", Tab 2 ID: " tab2-instance-id))))))))

(deftest page-reload-preserves-instance-id-test
  (with-browser instance-id-test-view weave-options
    (testing "Test that page reloads preserve the same instance ID"
      (visible? :get-instance-id)
      (click :get-instance-id)
      (e/wait-predicate #(not= "Loading..." (el-text :instance-id)))
      (let [initial-instance-id (el-text :instance-id)]
        (is (not (str/blank? initial-instance-id)))

        (e/refresh *browser*)
        (visible? :get-instance-id)
        (click :get-instance-id)
        (e/wait-predicate #(not= "Loading..." (el-text :instance-id)))
        (let [reloaded-instance-id (el-text :instance-id)]
          (is (not (str/blank? reloaded-instance-id)))

          (is (= initial-instance-id reloaded-instance-id)
              (str "Initial ID: " initial-instance-id ", Reloaded ID: " reloaded-instance-id)))))))

(defn page-load-test-view []
  [:div
   [:h1#content "Hello, Weave!"]])

(test-with-sse-variants
 'page-load-test
 page-load-test-view

 (visible? :content)

 (is (= "Hello, Weave!"
        (el-text :content))))

(defn push-html-test-view [click-count]
  [:div#view
   [:div#count @click-count]
   [:button
    {:id "increment-button"
     :data-on-click
     (core/handler [click-count]
       (swap! click-count inc)
       (core/push-html!
        (push-html-test-view click-count)))}
    "Click Me"]])

(test-with-sse-variants
 'push-html-test
 (let [counter (atom 41)]
   (fn [] (push-html-test-view counter)))

 (visible? :increment-button)
 (is (= "41" (el-text :count)))

 (click :increment-button)
 (e/wait-predicate #(= "42" (el-text :count)))
 (is (= "42" (el-text :count))))

(defn push-html-append-test-view []
  [:div#view
   [:ul#item-list
    [:li "Item 1"]
    [:li "Item 2"]]
   [:button
    {:id "append-button"
     :data-on-click
     (core/handler []
       (core/push-html!
        [:li "New Item"]
        {:mode :append :selector "#item-list"}))}
    "Add Item"]])

(deftest push-html-append-test
  (with-browser push-html-append-test-view weave-options
    (testing "push-html! with append mode adds elements to list"
      (visible? :append-button)

      (let [items (e/query-all *browser* {:css "#item-list li"})]
        (is (= 2 (count items))))

      (click :append-button)

      (e/wait-predicate
       #(= 3 (count (e/query-all *browser* {:css "#item-list li"}))))

      (let [items (e/query-all *browser* {:css "#item-list li"})]
        (is (= 3 (count items))))

      (click :append-button)

      (e/wait-predicate
       #(= 4 (count (e/query-all *browser* {:css "#item-list li"}))))

      (let [items (e/query-all *browser* {:css "#item-list li"})]
        (is (= 4 (count items)))))))

(defn broadcast-html-append-test-view []
  [:div#view
   [:ul#item-list
    [:li "Item 1"]
    [:li "Item 2"]]
   [:button
    {:id "append-button"
     :data-on-click
     (core/handler []
       (core/broadcast-html!
        [:li "New Item"]
        {:mode :append :selector "#item-list"}))}
    "Add Item"]])

(deftest broadcast-html-append-test
  (with-browser broadcast-html-append-test-view weave-options
    (testing "broadcast-html! with append mode adds elements to list across tabs"
      (visible? :append-button)

      (new-tab)

      (let [[tab1 tab2] (tabs)]

        (switch-tab tab1)
        (visible? :append-button)
        (let [items (e/query-all *browser* {:css "#item-list li"})]
          (is (= 2 (count items))))

        (switch-tab tab2)
        (visible? :append-button)
        (let [items (e/query-all *browser* {:css "#item-list li"})]
          (is (= 2 (count items))))

        (switch-tab tab1)
        (click :append-button)

        (e/wait-predicate
         #(= 3 (count (e/query-all *browser* {:css "#item-list li"}))))

        (switch-tab tab2)
        (e/wait-predicate
         #(= 3 (count (e/query-all *browser* {:css "#item-list li"}))))

        (let [items (e/query-all *browser* {:css "#item-list li"})]
          (is (= 3 (count items))))))))

(defn form-submission-test-view []
  [:div {:id "view"}
   [:h1 "Form Submission Test"]
   [:form {:id "test-form"
           :data-on-submit
           (core/handler ^{:type :form} []
             (let [form-data (:params core/*request*)
                   name (:name form-data)
                   email (:email form-data)]
               (core/push-html!
                [:div#view
                 [:div#result
                  [:p (str "Hello " name "!")]
                  [:p (str "Email: " email)]]])))}
    [:div
     [:label {:for "name"} "Name:"]
     [:input {:type "text" :id "name" :name "name" :required true}]]
    [:div
     [:label {:for "email"} "Email:"]
     [:input {:type "email" :id "email" :name "email" :required true}]]
    [:button {:type "submit" :id "submit-btn"} "Submit"]]])

(test-with-sse-variants
 'form-submission-test
 form-submission-test-view

 (visible? :test-form)

 (fill :name "John Doe")
 (fill :email "john@example.com")

 (click :submit-btn)

 (visible? :result)

 (is (str/includes?
      (el-text :result)
      "Hello John Doe!"))
 (is (str/includes?
      (el-text :result)
      "Email: john@example.com")))

(defn broadcast-html-test-view [click-count]
  [:div#view
   [:div#count @click-count]
   [:button
    {:id "increment-button"
     :data-on-click
     (core/handler [click-count]
       (swap! click-count inc)
       (core/broadcast-html!
        (broadcast-html-test-view click-count)))}
    "Click Me"]])

(deftest broadcast-html-test
  (let [counter (atom 0)
        view (fn [] (broadcast-html-test-view counter))]
    (with-browser view weave-options
      (testing "broadcast-html! updates all connected tabs"
        (visible? :increment-button)
        (new-tab)

        (let [[tab1 tab2] (tabs)]

          (switch-tab tab1)
          (visible? :increment-button)

          (switch-tab tab2)
          (visible? :increment-button)

          (is (= "0" (el-text :count)))
          (switch-tab tab1)
          (is (= "0" (el-text :count)))

          (click :increment-button)
          (e/wait-predicate #(= "1" (el-text :count)))

          (switch-tab tab2)
          (e/wait-predicate #(= "1" (el-text :count)))
          (is (= "1" (el-text :count))))))))

(defn push-path-test-view []
  [:div {:id "view"}
   [:div
    [:a {:id "trigger-view-one"
         :data-on-click
         (core/handler []
           (core/push-path! "/views/one" push-path-test-view))}
     "Page One"]
    [:a {:id "trigger-view-two"
         :data-on-click
         (core/handler []
           (core/push-path! "/views/two" push-path-test-view))}
     "Page Two"]]

   [:div#content
    (case core/*app-path*
      "/views/one" [:div#page-one-content
                    [:h2 "Page One Content"]]
      "/views/two" [:div#page-two-content
                    [:h2 "Page Two Content"]]
      [:div#default-content
       "Select a page from the navigation above"])]])

(test-with-sse-variants
 'push-path-test
 push-path-test-view

 (visible? :default-content)

 (click :trigger-view-two)
 (e/wait-predicate
  #(= "Page Two Content"
      (el-text :page-two-content)))
 (is (= "Page Two Content"
        (el-text :page-two-content))))

(defn broadcast-path-test-view []
  [:div {:id "view"}
   [:div
    [:a {:id "trigger-view-one"
         :data-on-click
         (core/handler []
           (core/broadcast-path! "/views/one" broadcast-path-test-view))}
     "Page One"]
    [:a {:id "trigger-view-two"
         :data-on-click
         (core/handler []
           (core/broadcast-path! "/views/two" broadcast-path-test-view))}
     "Page Two"]]

   [:div#content
    (case core/*app-path*
      "/views/one" [:div#page-one-content
                    [:h2 "Page One Content"]]
      "/views/two" [:div#page-two-content
                    [:h2 "Page Two Content"]]
      [:div#default-content
       "Select a page from the navigation above"])]])

(deftest broadcast-path-test
  (with-browser broadcast-path-test-view weave-options
    (testing "Test broadcast-path! functionality across multiple tabs"
      (visible? :default-content)

      (new-tab)

      (let [[tab1 tab2] (tabs)]

        (switch-tab tab1)
        (visible? :default-content)

        (switch-tab tab2)
        (visible? :default-content)

        (switch-tab tab1)
        (click :trigger-view-one)
        (e/wait-predicate
         #(= "Page One Content" (el-text :page-one-content)))
        (is (= "Page One Content" (el-text :page-one-content)))

        (switch-tab tab2)
        (e/wait-predicate
         #(= "Page One Content" (el-text :page-one-content)))
        (is (= "Page One Content" (el-text :page-one-content)))

        (click :trigger-view-two)
        (e/wait-predicate
         #(= "Page Two Content" (el-text :page-two-content)))
        (is (= "Page Two Content" (el-text :page-two-content)))

        (switch-tab tab1)
        (e/wait-predicate
         #(= "Page Two Content" (el-text :page-two-content)))
        (is (= "Page Two Content" (el-text :page-two-content)))))))

(defn manual-hash-change-test-view []
  [:div {:id "view"}
   [:div#content
    (case core/*app-path*
      "/views/one" [:div#page-one-content
                    [:h2 "Page One Content"]]
      "/views/two" [:div#page-two-content
                    [:h2 "Page Two Content"]]
      [:div#default-content
       "Default content"])]])

(deftest manual-hash-change-test
  (with-browser manual-hash-change-test-view weave-options
    (testing "Test manual hash change detection"
      (visible? :default-content)

      (e/js-execute
       *browser*
       (squint/clj->js
        (set! (-> js/window .-location .-hash) "#/views/one")
        (-> js/window .-location .reload)))

      (visible? :page-one-content)
      (is (= "Page One Content" (el-text :page-one-content))))))

(defn push-script-test-view []
  [:div {:id "view"}
   [:div#content "Initial content"]
   [:button
    {:id "execute-script-button"
     :data-on-click
     (core/handler []
       (core/push-script!
        (squint/clj->js
         (set! (-> js/document
                   (.getElementById "content")
                   (.-textContent))
               "Script executed!"))))}
    "Execute Script"]])

(test-with-sse-variants
 'push-script-test
 push-script-test-view

 (visible? :execute-script-button)
 (is (= "Initial content"
        (el-text :content)))

 (click :execute-script-button)
 (e/wait-predicate
  #(= "Script executed!" (el-text :content)))
 (is (= "Script executed!" (el-text :content))))

(defn broadcast-script-test-view []
  [:div {:id "view"}
   [:div#content "Initial content"]
   [:button
    {:id "execute-script-button"
     :data-on-click
     (core/handler []
       (core/broadcast-script!
        (squint/clj->js
         (set! (-> js/document
                   (.getElementById "content")
                   (.-textContent))
               "Broadcast script executed!"))))}
    "Execute Script"]])

(deftest broadcast-script-test
  (with-browser broadcast-script-test-view weave-options
    (testing "Test broadcast-script! functionality across multiple tabs"
      (visible? :execute-script-button)
      (is (= "Initial content" (el-text :content)))

      (new-tab)

      (let [[tab1 tab2] (tabs)]

        (switch-tab tab1)
        (visible? :execute-script-button)

        (switch-tab tab2)
        (visible? :execute-script-button)

        (is (= "Initial content" (el-text :content)))

        (switch-tab tab1)
        (is (= "Initial content" (el-text :content)))

        (click :execute-script-button)
        (e/wait-predicate
         #(= "Broadcast script executed!" (el-text :content)))
        (is (= "Broadcast script executed!" (el-text :content)))

        (switch-tab tab2)
        (e/wait-predicate
         #(= "Broadcast script executed!" (el-text :content)))
        (is (= "Broadcast script executed!" (el-text :content)))))))

(defn push-signal-test-view []
  [:div {:id "view"}
   [:button
    {:id "increment-button"
     :data-on-click
     (core/handler []
       (core/push-signal! {:foo 42}))}
    "Update Signal"]
   [:div {:data-signals-foo "0"}
    [:input#signal-value {:data-bind-foo true}]]])

(test-with-sse-variants
 'push-signal-test
 push-signal-test-view

 (visible? :increment-button)

 (click :increment-button)

 (e/wait-predicate
  #(= "42" (e/get-element-value *browser* {:id :signal-value})))
 (is (= "42" (e/get-element-value *browser* {:id :signal-value}))))

(defn local-signal-test-view []
  [:div {:id "view"}
   [:button
    {:id "show-button"
     :data-on-click
     (core/handler []
       (core/push-signal! {:_show-div true}))}
    "Show Hidden Div"]
   [:div {:id "hidden-div"
          :data-show "$_showDiv"}
    "This div was hidden but is now visible!"]])

(test-with-sse-variants
 'local-signal-test
 local-signal-test-view

 (visible? :show-button)

 (is (not (e/visible? *browser* {:id :hidden-div})))

 (click :show-button)

 (visible? :hidden-div))

(defn filter-signals-test-view []
  [:div {:id "view"
         :data-signals-public-data "1"
         :data-signals-other-field "-1"
         :data-signals-public-name "2"}
   [:div#result "No signals received"]
   [:button
    {:id "send-signals-button"
     :data-on-click
     (core/handler ^{:filter-signals {:include ".*public.*"
                                      :exclude ".*private.*"}} []
       (let [signals core/*signals*]
         (core/push-html!
          [:div#result
           [:p "Received signals:"]
           [:ul
            (for [[k v] signals]
              [:li (str (name k) ": " v)])]])))}
    "Send Signals"]
   [:button
    {:id "send-all-signals-button"
     :data-on-click
     (core/handler [] ;; no filter
       (let [signals core/*signals*]
         (core/push-html!
          [:div#result
           [:p "All signals received:"]
           [:ul
            (for [[k v] signals]
              [:li (str (name k) ": " v)])]])))}
    "Send All Signals"]])

(test-with-sse-variants
 'filter-signals-test
 filter-signals-test-view

 (visible? :send-signals-button)
 (is (= "No signals received" (el-text :result)))

 (click :send-signals-button)
 (e/wait-predicate
  #(str/includes? (el-text :result) "Received signals:"))

 (let [result-text (el-text :result)]
   (is (str/includes? result-text "public-data: 1"))
   (is (str/includes? result-text "public-name: 2"))
   (is (not (str/includes? result-text "-1"))))

 (click :send-all-signals-button)
 (e/wait-predicate
  #(str/includes? (el-text :result) "All signals received:"))

 (let [result-text (el-text :result)]
   (is (str/includes? result-text "public-data: 1"))
   (is (str/includes? result-text "other-field: -1"))
   (is (str/includes? result-text "public-name: 2"))))

(defn data-call-with-test-view []
  [:div {:id "view"}
   [:div#result "No action performed yet"]
   (let [handle-action (core/handler []
                         (let [{:keys [action item-id]} core/*signals*]
                           (core/push-html!
                            [:div#result (str "Action: " action ", Item: " item-id)])))]
     [:div.button-group
      [:button#edit-button
       {:data-call-with-action "edit"
        :data-call-with-item-id "123"
        :data-on-click handle-action}
       "Edit"]
      [:button#delete-button
       {:data-call-with-action "delete"
        :data-call-with-item-id "456"
        :data-on-click handle-action}
       "Delete"]])])

(test-with-sse-variants
 'data-call-with-test
 data-call-with-test-view

 (visible? :edit-button)
 (is (= "No action performed yet"
        (el-text :result)))

 (click :edit-button)
 (e/wait-predicate
  #(= "Action: edit, Item: 123"
      (el-text :result)))
 (is (= "Action: edit, Item: 123"
        (el-text :result)))

 (click :delete-button)
 (e/wait-predicate
  #(= "Action: delete, Item: 456"
      (el-text :result)))
 (is (= "Action: delete, Item: 456"
        (el-text :result))))

(defn set-cookie-test-view []
  [:div {:id "view"}
   [:div#cookie-status "No cookie set"]
   [:button
    {:id "set-cookie-button"
     :data-on-click
     (core/handler []
       (core/set-cookie! "test-cookie=cookie-value; Path=/; Max-Age=3600")
       (core/push-script!
        (squint/clj->js
         (set! (-> js/document
                   (.getElementById "cookie-status")
                   (.-textContent))
               (str "Cookie set: " js/document.cookie)))))}
    "Set Cookie"]])

(deftest set-cookie-test
  (with-browser set-cookie-test-view weave-options
    (testing "Test set-cookie! functionality"
      (visible? :set-cookie-button)
      (is (= "No cookie set" (el-text :cookie-status)))

      (click :set-cookie-button)

      (e/wait-predicate #(str/includes?
                          (el-text :cookie-status)
                          "test-cookie=cookie-value"))

      (is (str/includes?
           (el-text :cookie-status)
           "test-cookie=cookie-value")))))

(defn session-management-test-view []
  [:div {:id "view"}
   [:div#session-status "Checking session..."]
   [:div#auth-status
    (if (:identity core/*request*)
      (str "Authenticated as " (get-in core/*request* [:identity :name]))
      "Not authenticated")]
   [:button
    {:id "sign-in-button"
     :data-on-click
     (core/handler []
       (core/set-cookie! (session/sign-in {:name "TestUser" :role "User"}))
       (core/push-reload!))}
    "Sign In"]
   [:button
    {:id "sign-out-button"
     :data-on-click
     (core/handler []
       (core/set-cookie! (session/sign-out))
       (core/push-reload!))}
    "Sign Out"]])

(deftest session-management-test
  (with-browser
    session-management-test-view
    (assoc weave-options :jwt-secret "test-jwt-secret")

    (testing "Test session management functionality"
      (visible? :sign-in-button)
      (is (= "Not authenticated" (el-text :auth-status)))

      (click :sign-in-button)

      (visible? :sign-out-button)
      (e/wait-predicate #(= "Authenticated as TestUser"
                            (el-text :auth-status)))
      (is (= "Authenticated as TestUser"
             (el-text :auth-status)))

      (click :sign-out-button)

      (visible? :sign-in-button)
      (e/wait-predicate #(= "Not authenticated"
                            (el-text :auth-status)))
      (is (= "Not authenticated"
             (el-text :auth-status))))))

(defn auth-required-handler-test-view []
  [:div {:id "view"}
   [:div#auth-status
    (if (:identity core/*request*)
      (str "Authenticated as " (get-in core/*request* [:identity :name]))
      "Not authenticated")]
   [:button
    {:id "sign-in-button"
     :data-on-click
     (core/handler []
       (core/set-cookie! (session/sign-in {:name "TestUser" :role "User"}))
       (core/push-reload!))}
    "Sign In"]
   [:button
    {:id "sign-out-button"
     :data-on-click
     (core/handler []
       (core/set-cookie! (session/sign-out))
       (core/push-reload!))}
    "Sign Out"]
   [:button
    {:id "protected-action-button"
     :data-on-click
     (core/handler ^{:auth-required? true} []
       (core/push-script!
        (squint/clj->js
         (set! (-> js/document
                   (.getElementById "protected-result")
                   (.-textContent))
               "Protected action executed!"))))}
    "Execute Protected Action"]
   [:div#protected-result "Protected action not executed"]])

(deftest auth-required-handler-test
  (with-browser
    auth-required-handler-test-view
    (assoc weave-options :jwt-secret "test-jwt-secret")

    (testing "Test auth-required handler functionality"
      (visible? :protected-action-button)
      (is (= "Not authenticated" (el-text :auth-status)))

      (click :protected-action-button)
      (Thread/sleep 500)

      (is (= "Protected action not executed"
             (el-text :protected-result)))

      (click :sign-in-button)

      (visible? :sign-out-button)
      (e/wait-predicate #(= "Authenticated as TestUser"
                            (el-text :auth-status)))

      (click :protected-action-button)

      (e/wait-predicate #(= "Protected action executed!"
                            (el-text :protected-result)))
      (is (= "Protected action executed!"
             (el-text :protected-result))))))

(deftest custom-handlers-test
  (testing "Test custom handlers functionality"
    (let [response-body (str (random-uuid))
          handlers [(GET "/custom-route" []
                      {:status 200
                       :headers {"Content-Type" "text/plain"}
                       :body response-body})]]
      (with-browser
        (fn [] [:div "Test view"])
        (assoc weave-options :handlers handlers)

        (let [response (slurp (str url "/custom-route"))]
          (is (= response-body response)))))))

(deftest authenticated-test
  (with-browser
    (fn [] [:div])
    weave-options

    (testing "authenticated? returns true when identity is present"
      (let [request-with-identity {:identity {:name "TestUser"}}
            request-without-identity {}]
        (is (true? (core/authenticated? request-with-identity)))
        (is (false? (core/authenticated? request-without-identity)))
        (is (false? (core/authenticated? nil)))))))

(deftest throw-unauthorized-test
  (with-browser
    (fn [] [:div])
    weave-options

    (testing "throw-unauthorized throws exception with expected data"
      (try
        (core/throw-unauthorized)
        (is false "Exception should have been thrown")
        (catch clojure.lang.ExceptionInfo e
          (is (= "Unauthorized." (.getMessage e)))
          (is (= :weave.core/unauthorized (-> (ex-data e) :weave.core/type)))
          (is (map? (-> (ex-data e) :weave.core/payload))))))

    (testing "throw-unauthorized includes custom error data"
      (let [custom-data {:reason "Invalid token" :code 401}]
        (try
          (core/throw-unauthorized custom-data)
          (is false "Exception should have been thrown")
          (catch clojure.lang.ExceptionInfo e
            (is (= "Unauthorized." (.getMessage e)))
            (is (= :weave.core/unauthorized (-> (ex-data e) :weave.core/type)))
            (is (= custom-data (-> (ex-data e) :weave.core/payload)))))))))

(defn secure-handlers-test-view []
  [:div {:id "view"}
   [:div#auth-status
    (if (:identity core/*request*)
      (str "Authenticated as " (get-in core/*request* [:identity :name]))
      "Not authenticated")]
   [:button
    {:id "sign-in-button"
     :data-on-click
     (core/handler ^{:auth-required? false} []
       (core/set-cookie! (session/sign-in {:name "TestUser" :role "User"}))
       (core/push-reload!))}
    "Sign In"]
   [:button
    {:id "sign-out-button"
     :data-on-click
     (core/handler ^{:auth-required? false} []
       (core/set-cookie! (session/sign-out))
       (core/push-reload!))}
    "Sign Out"]
   [:button
    {:id "secure-action-button"
     :data-on-click
     (core/handler []
       (core/push-script!
        (squint/clj->js
         (set! (-> js/document
                   (.getElementById "secure-result")
                   (.-textContent))
               "Secure action executed!"))))}
    "Execute Secure Action"]
   [:button
    {:id "public-action-button"
     :data-on-click
     (core/handler ^{:auth-required? false} []
       (core/push-script!
        (squint/clj->js
         (set! (-> js/document
                   (.getElementById "public-result")
                   (.-textContent))
               "Public action executed!"))))}
    "Execute Public Action"]
   [:div#secure-result "Secure action not executed"]
   [:div#public-result "Public action not executed"]])

(deftest secure-handlers-test
  (with-browser
    secure-handlers-test-view
    (assoc weave-options
           :jwt-secret "test-jwt-secret"
           :secure-handlers true)

    (testing "Test secure-handlers functionality"
      (visible? :secure-action-button)
      (is (= "Not authenticated"
             (el-text :auth-status)))

      (click :secure-action-button)
      (Thread/sleep 100)
      (is (= "Secure action not executed"
             (el-text :secure-result)))

      (click :public-action-button)
      (e/wait-predicate
       #(= "Public action executed!"
           (el-text :public-result)))
      (is (= "Public action executed!"
             (el-text :public-result)))

      (click :sign-in-button)
      (visible? :sign-out-button)
      (e/wait-predicate
       #(= "Authenticated as TestUser"
           (el-text :auth-status)))

      (click :secure-action-button)
      (e/wait-predicate
       #(= "Secure action executed!"
           (el-text :secure-result)))
      (is (= "Secure action executed!"
             (el-text :secure-result))))))

(def read-json
  (charred/parse-json-fn
   {:async? false :bufsize 1024 :key-fn keyword}))

(deftest with-icon-options-test
  (with-browser
    (fn [] [:div "Icon Test"])
    (assoc weave-options
           :icon "public/weave.png"
           :title "Icon Test App")

    (testing "Test icon options in HTML when icon is provided"

      (is (= "Icon Test App" (e/get-title *browser*)))

      (is (e/js-execute
           *browser*
           "return document.querySelector('link[rel=\"icon\"][href=\"/favicon.png\"]') !== null"))

      (is (e/js-execute
           *browser*
           "return document.querySelector('link[rel=\"apple-touch-icon\"][href=\"/icon-180.png\"]') !== null"))

      (is (e/js-execute
           *browser*
           "return document.querySelector('link[rel=\"manifest\"][href=\"/manifest.json\"]') !== null"))

      (let [manifest-json (slurp (str url "/manifest.json"))
            manifest (read-json manifest-json)]
        (is (= "Icon Test App" (:name manifest)))
        (is (= "Icon Test App" (:short_name manifest)))
        (is (vector? (:icons manifest)))
        (is (= 2 (count (:icons manifest))))
        (is (= "/icon-192.png" (-> manifest :icons first :src)))
        (is (= "/icon-512.png" (-> manifest :icons second :src)))))))

(deftest without-icon-options-test
  (with-browser
    (fn [] [:div "No Icon Test"])
    (assoc weave-options :title "No Icon Test App")

    (testing "Test that icon elements are not generated when no icon is provided"

      (is (= "No Icon Test App" (e/get-title *browser*)))

      (is (e/js-execute
           *browser*
           "return document.querySelector('link[rel=\"icon\"][href=\"/favicon.png\"]') === null"))

      (is (e/js-execute
           *browser*
           "return document.querySelector('link[rel=\"apple-touch-icon\"][href=\"/icon-180.png\"]') === null"))

      (is (e/js-execute
           *browser*
           "return document.querySelector('link[rel=\"manifest\"][href=\"/manifest.json\"]') === null")))))

(deftest with-pwa-options-test
  (let [pwa-options {:name "PWA Test App"
                     :short-name "PWA Test"
                     :description "Testing PWA options"
                     :display "fullscreen"
                     :background-color "#ff0000"
                     :theme-color "#00ff00"
                     :start-url "/start"}]
    (with-browser
      (fn [] [:div "PWA Test"])
      (assoc weave-options
             :icon "public/weave.png"
             :pwa pwa-options)

      (testing "Test PWA options in manifest when PWA options are provided"

        (is (e/js-execute
             *browser*
             "return document.querySelector('link[rel=\"manifest\"][href=\"/manifest.json\"]') !== null"))

        (let [manifest-json (slurp (str url "/manifest.json"))
              manifest (read-json manifest-json)]
          (is (= "PWA Test App" (:name manifest)))
          (is (= "PWA Test" (:short_name manifest)))
          (is (= "Testing PWA options" (:description manifest)))
          (is (= "fullscreen" (:display manifest)))
          (is (= "#ff0000" (:background_color manifest)))
          (is (= "#00ff00" (:theme_color manifest)))
          (is (= "/start" (:start_url manifest))))))))

(deftest without-pwa-options-test
  (with-browser
    (fn [] [:div "No PWA Test"])
    (assoc weave-options
           :icon "public/weave.png"
           :title "No PWA Test App")

    (testing "Test default PWA options in manifest when no PWA options are provided"

      (is (e/js-execute
           *browser*
           "return document.querySelector('link[rel=\"manifest\"][href=\"/manifest.json\"]') !== null"))

      (let [manifest-json (slurp (str url "/manifest.json"))
            manifest (read-json manifest-json)]
        (is (= "No PWA Test App" (:name manifest)))
        (is (= "No PWA Test App" (:short_name manifest)))
        (is (nil? (:description manifest)))
        (is (= "standalone" (:display manifest)))
        (is (= "#f2f2f2" (:background_color manifest)))
        (is (= "#ffffff" (:theme_color manifest)))
        (is (= "/" (:start_url manifest)))))))

(deftest user-middleware-test
  (testing "Test user-supplied middleware functionality"
    (let [middleware-execution-order (atom [])
          middleware-1 (fn [handler]
                         (fn [request]
                           (swap! middleware-execution-order conj :middleware-1-before)
                           (let [response (handler request)]
                             (swap! middleware-execution-order conj :middleware-1-after)
                             (assoc-in response [:headers "X-Middleware-1"] "applied"))))
          middleware-2 (fn [handler]
                         (fn [request]
                           (swap! middleware-execution-order conj :middleware-2-before)
                           (let [response (handler request)]
                             (swap! middleware-execution-order conj :middleware-2-after)
                             (assoc-in response [:headers "X-Middleware-2"] "applied"))))
          custom-route (GET "/middleware-test" []
                         {:status 200
                          :headers {"Content-Type" "text/plain"}
                          :body "Middleware test endpoint"})
          server (core/run
                  (fn [] [:div "Middleware Test"])
                  (assoc browser/weave-options
                         :handlers [custom-route]
                         :middleware [middleware-1 middleware-2]))]

      (try
        (let [response (slurp (str url "/middleware-test"))]
          (is (= "Middleware test endpoint" response))

          (is (= [:middleware-1-before
                  :middleware-2-before
                  :middleware-2-after
                  :middleware-1-after]
                 @middleware-execution-order))

          (let [url (java.net.URL. (str url "/middleware-test"))
                connection (.openConnection url)
                _ (.connect connection)
                headers (into {} (.getHeaderFields connection))]
            (is (= "applied" (first (get headers "X-Middleware-1"))))
            (is (= "applied" (first (get headers "X-Middleware-2"))))))
        (finally
          (ig/halt! server))))))
