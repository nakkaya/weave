(ns weave.core-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [compojure.core :refer [GET]]
   [etaoin.api :as e]
   [integrant.core :as ig]
   [weave.core :as core]
   [weave.session :as session]
   [charred.api :as charred]))

(defn clean-string [s]
  (-> s
      (str/replace #"\n" " ")
      (str/replace #"\s+" " ")
      str/trim))

(deftest request-headers-test
  (testing "Test request header options"
    (is (= "{headers: { 'x-csrf-token': $app.csrf, 'x-instance-id': $app.instance, 'x-app-path': $app.path }}"
           (clean-string
            (#'core/request-options {}))))
    (is (= "{headers: { 'x-csrf-token': $app.csrf, 'x-instance-id': $app.instance, 'x-app-path': $app.path }, contentType: 'form'}"
           (clean-string
            (#'core/request-options {:type :form}))))
    (is (= "{headers: { 'x-csrf-token': $app.csrf, 'x-instance-id': $app.instance, 'x-app-path': $app.path }, openWhenHidden: true}"
           (clean-string
            (#'core/request-options {:keep-alive true}))))
    (is (= "{headers: { 'x-csrf-token': $app.csrf, 'x-instance-id': $app.instance, 'x-app-path': $app.path }, contentType: 'form', openWhenHidden: true}"
           (clean-string
            (#'core/request-options {:type :form :keep-alive true}))))
    (is (= "{headers: { 'x-csrf-token': $app.csrf, 'x-instance-id': $app.instance, 'x-app-path': $app.path }, selector: '#myform'}"
           (clean-string
            (#'core/request-options {:selector "#myform"}))))
    (is (= "{headers: { 'x-csrf-token': $app.csrf, 'x-instance-id': $app.instance, 'x-app-path': $app.path }, contentType: 'form', selector: '#myform'}"
           (clean-string
            (#'core/request-options {:type :form :selector "#myform"}))))
    (is (= "{headers: { 'x-csrf-token': $app.csrf, 'x-instance-id': $app.instance, 'x-app-path': $app.path }, contentType: 'form', openWhenHidden: true, selector: '#myform'}"
           (clean-string
            (#'core/request-options {:type :form :keep-alive true :selector "#myform"}))))))

(defn driver-options []
  {:path-driver "chromedriver"
   :args [(str "--user-data-dir="
               (str "/tmp/chrome-data-"
                    (System/currentTimeMillis)))
          "--no-sandbox"]})

(def test-port 3333)
(def test-url (str "http://localhost:" test-port))
(def test-options {:http-kit {:port test-port}
                   :sse {:enabled true
                         :keep-alive true}})

(defn simple-view []
  [:div
   [:h1#content "Hello, Weave!"]])

(deftest page-load-test-with-sse
  (let [server (core/run simple-view test-options)
        driver (e/chrome-headless (driver-options))]
    (try
      (testing "App renders with SSE enabled"
        (e/go driver test-url)
        (e/wait-visible driver {:id :content})
        (is (e/visible? driver {:id :content}))
        (is (= "Hello, Weave!" (e/get-element-text driver {:id :content}))))
      (finally
        (e/quit driver)
        (ig/halt! server)))))

(deftest page-load-test-without-sse
  (let [server (core/run simple-view
                 (assoc-in test-options [:sse :enabled] false))
        driver (e/chrome-headless (driver-options))]
    (try
      (testing "App renders with SSE disabled"
        (e/go driver test-url)
        (e/wait-visible driver {:id :content})
        (is (e/visible? driver {:id :content}))
        (is (= "Hello, Weave!" (e/get-element-text driver {:id :content}))))
      (finally
        (e/quit driver)
        (ig/halt! server)))))

(defn push-click-count-view [click-count]
  [:div#view
   [:div#count @click-count]
   [:button
    {:id "increment-button"
     :data-on-click
     (core/handler [click-count]
      (swap! click-count inc)
      (core/push-html!
       (push-click-count-view click-count)))}
    "Click Me"]])

(deftest push-html-test-with-sse
  (let [counter (atom 41)
        view (fn [] (push-click-count-view counter))
        server (core/run view test-options)
        driver (e/chrome-headless (driver-options))]
    (try
      (testing "push-html! updates the view with SSE enabled"
        (e/go driver test-url)
        (e/wait-visible driver {:id :increment-button})
        (is (e/visible? driver {:id :increment-button}))
        (is (= "41" (e/get-element-text driver {:id :count})))
        (e/click driver {:id :increment-button})
        (e/wait-predicate #(= "42" (e/get-element-text driver {:id :count})))
        (is (= "42" (e/get-element-text driver {:id :count}))))
      (finally
        (e/quit driver)
        (ig/halt! server)))))

(deftest push-html-test-without-sse
  (let [counter (atom 41)
        view (fn [] (push-click-count-view counter))
        server (core/run view
                 (assoc-in test-options [:sse :enabled] false))
        driver (e/chrome-headless (driver-options))]
    (try
      (testing "push-html! updates the view with SSE disabled"
        (e/go driver test-url)
        (e/wait-visible driver {:id :increment-button})
        (is (e/visible? driver {:id :increment-button}))
        (is (= "41" (e/get-element-text driver {:id :count})))
        (e/click driver {:id :increment-button})
        (e/wait-predicate #(= "42" (e/get-element-text driver {:id :count})))
        (is (= "42" (e/get-element-text driver {:id :count}))))
      (finally
        (e/quit driver)
        (ig/halt! server)))))

(defn broadcast-click-count-view [click-count]
  [:div#view
   [:div#count @click-count]
   [:button
    {:id "increment-button"
     :data-on-click
     (core/handler [click-count]
      (swap! click-count inc)
      (core/broadcast-html!
       (broadcast-click-count-view click-count)))}
    "Click Me"]])

(deftest broadcast-html-test
  (let [counter (atom 0)
        view (fn [] (broadcast-click-count-view counter))
        server (core/run view test-options)
        driver (e/chrome-headless (driver-options))]
    (try
      (testing "broadcast-html! updates all connected tabs"
        ;; Open first tab
        (e/go driver test-url)
        (e/wait-visible driver {:id :increment-button})

        ;; Open a new tab using JavaScript
        (e/js-execute driver "window.open(arguments[0], '_blank');" test-url)

        ;; Get handles for both tabs
        (let [handles (e/get-window-handles driver)
              tab1 (first handles)
              tab2 (second handles)]

          ;; Make sure first tab is loaded
          (e/switch-window driver tab1)
          (e/wait-visible driver {:id :increment-button})
          (is (e/visible? driver {:id :increment-button}))

          ;; Make sure second tab is loaded
          (e/switch-window driver tab2)
          (e/wait-visible driver {:id :increment-button})
          (is (e/visible? driver {:id :increment-button}))

          ;; Verify initial state in both tabs
          (is (= "0" (e/get-element-text driver {:id :count})))

          (e/switch-window driver tab1)
          (is (= "0" (e/get-element-text driver {:id :count})))

          ;; Click button in first tab
          (e/click driver {:id :increment-button})
          (e/wait-predicate #(= "1" (e/get-element-text driver {:id :count})))

          ;; Switch to second tab and verify it was updated too
          (e/switch-window driver tab2)
          (e/wait-predicate #(= "1" (e/get-element-text driver {:id :count})))
          (is (= "1" (e/get-element-text driver {:id :count})))))
      (finally
        (e/quit driver)
        (ig/halt! server)))))

(defn push-path-view []
  [:div {:id "view"}
   [:div
    [:a {:id "trigger-view-one"
         :data-on-click
         (core/handler []
          (core/push-path! "/views/one" push-path-view))}
     "Page One"]
    [:a {:id "trigger-view-two"
         :data-on-click
         (core/handler []
          (core/push-path! "/views/two" push-path-view))}
     "Page Two"]]

   [:div#content
    (case core/*app-path*
      "/views/one" [:div#page-one-content
                    [:h2 "Page One Content"]]
      "/views/two" [:div#page-two-content
                    [:h2 "Page Two Content"]]
      [:div#default-content
       "Select a page from the navigation above"])]])

(deftest push-path-test-with-sse
  (let [server (core/run push-path-view test-options)
        driver (e/chrome-headless (driver-options))]
    (try
      (testing "Test push-path! functionality with SSE enabled"
        (e/go driver test-url)
        (e/wait-visible driver {:id :default-content})
        (is (e/visible? driver {:id :default-content}))
        (e/click driver {:id :trigger-view-two})
        (e/wait-predicate
         #(= "Page Two Content" (e/get-element-text driver {:id :page-two-content})))
        (is (= "Page Two Content" (e/get-element-text driver {:id :page-two-content}))))
      (finally
        (e/quit driver)
        (ig/halt! server)))))

(deftest push-path-test-without-sse
  (let [server (core/run push-path-view
                 (assoc-in test-options [:sse :enabled] false))
        driver (e/chrome-headless (driver-options))]
    (try
      (testing "Test push-path! functionality with SSE disabled"
        (e/go driver test-url)
        (e/wait-visible driver {:id :default-content})
        (is (e/visible? driver {:id :default-content}))
        (e/click driver {:id :trigger-view-two})
        (e/wait-predicate
         #(= "Page Two Content" (e/get-element-text driver {:id :page-two-content})))
        (is (= "Page Two Content" (e/get-element-text driver {:id :page-two-content}))))
      (finally
        (e/quit driver)
        (ig/halt! server)))))

(defn broadcast-path-view []
  [:div {:id "view"}
   [:div
    [:a {:id "trigger-view-one"
         :data-on-click
         (core/handler []
          (core/broadcast-path! "/views/one" broadcast-path-view))}
     "Page One"]
    [:a {:id "trigger-view-two"
         :data-on-click
         (core/handler []
          (core/broadcast-path! "/views/two" broadcast-path-view))}
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
  (let [server (core/run broadcast-path-view test-options)
        driver (e/chrome-headless (driver-options))]
    (try
      (testing "Test broadcast-path! functionality across multiple tabs"
        ;; Open first tab
        (e/go driver test-url)
        (e/wait-visible driver {:id :default-content})
        (is (e/visible? driver {:id :default-content}))

        (e/js-execute driver "window.open(arguments[0], '_blank');" test-url)

        (let [handles (e/get-window-handles driver)
              tab1 (first handles)
              tab2 (second handles)]

          (e/switch-window driver tab1)
          (e/wait-visible driver {:id :default-content})
          (is (e/visible? driver {:id :default-content}))

          (e/switch-window driver tab2)
          (e/wait-visible driver {:id :default-content})
          (is (e/visible? driver {:id :default-content}))

          (e/switch-window driver tab1)
          (e/click driver {:id :trigger-view-one})
          (e/wait-predicate
           #(= "Page One Content" (e/get-element-text driver {:id :page-one-content})))
          (is (= "Page One Content" (e/get-element-text driver {:id :page-one-content})))

          (e/switch-window driver tab2)
          (e/wait-predicate
           #(= "Page One Content" (e/get-element-text driver {:id :page-one-content})))
          (is (= "Page One Content" (e/get-element-text driver {:id :page-one-content})))

          (e/click driver {:id :trigger-view-two})
          (e/wait-predicate
           #(= "Page Two Content" (e/get-element-text driver {:id :page-two-content})))
          (is (= "Page Two Content" (e/get-element-text driver {:id :page-two-content})))

          (e/switch-window driver tab1)
          (e/wait-predicate
           #(= "Page Two Content" (e/get-element-text driver {:id :page-two-content})))
          (is (= "Page Two Content" (e/get-element-text driver {:id :page-two-content})))))
      (finally
        (e/quit driver)
        (ig/halt! server)))))

(defn manual-hash-change-view []
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
  (let [server (core/run manual-hash-change-view test-options)
        driver (e/chrome-headless (driver-options))]
    (try
      (testing "Test manual hash change detection"
        (e/go driver test-url)
        (e/wait-visible driver {:id :default-content})
        (is (e/visible? driver {:id :default-content}))

        (e/js-execute
         driver "window.location.hash = '#/views/one'; window.location.reload();")

        (e/wait-visible driver {:id :page-one-content})
        (is (= "Page One Content" (e/get-element-text driver {:id :page-one-content}))))
      (finally
        (e/quit driver)
        (ig/halt! server)))))

(defn push-script-view []
  [:div {:id "view"}
   [:div#content "Initial content"]
   [:button
    {:id "execute-script-button"
     :data-on-click
     (core/handler []
      (core/push-script!
       "document.getElementById('content').textContent = 'Script executed!';"))}
    "Execute Script"]])

(deftest push-script-test-with-sse
  (let [server (core/run push-script-view test-options)
        driver (e/chrome-headless (driver-options))]
    (try
      (testing "Test push-script! functionality with SSE enabled"
        (e/go driver test-url)
        (e/wait-visible driver {:id :execute-script-button})
        (is (e/visible? driver {:id :execute-script-button}))
        (is (= "Initial content" (e/get-element-text driver {:id :content})))

        (e/click driver {:id :execute-script-button})
        (e/wait-predicate #(= "Script executed!" (e/get-element-text driver {:id :content})))
        (is (= "Script executed!" (e/get-element-text driver {:id :content}))))
      (finally
        (e/quit driver)
        (ig/halt! server)))))

(deftest push-script-test-without-sse
  (let [server (core/run push-script-view
                 (assoc-in test-options [:sse :enabled] false))
        driver (e/chrome-headless (driver-options))]
    (try
      (testing "Test push-script! functionality with SSE disabled"
        (e/go driver test-url)
        (e/wait-visible driver {:id :execute-script-button})
        (is (e/visible? driver {:id :execute-script-button}))
        (is (= "Initial content" (e/get-element-text driver {:id :content})))

        (e/click driver {:id :execute-script-button})
        (e/wait-predicate #(= "Script executed!" (e/get-element-text driver {:id :content})))
        (is (= "Script executed!" (e/get-element-text driver {:id :content}))))
      (finally
        (e/quit driver)
        (ig/halt! server)))))

(defn broadcast-script-view []
  [:div {:id "view"}
   [:div#content "Initial content"]
   [:button
    {:id "execute-script-button"
     :data-on-click
     (core/handler []
      (core/broadcast-script!
       "document.getElementById('content').textContent = 'Broadcast script executed!';"))}
    "Execute Script"]])

(deftest broadcast-script-test
  (let [server (core/run broadcast-script-view test-options)
        driver (e/chrome-headless (driver-options))]
    (try
      (testing "Test broadcast-script! functionality across multiple tabs"
        (e/go driver test-url)
        (e/wait-visible driver {:id :execute-script-button})
        (is (e/visible? driver {:id :execute-script-button}))
        (is (= "Initial content" (e/get-element-text driver {:id :content})))

        (e/js-execute driver "window.open(arguments[0], '_blank');" test-url)

        (let [handles (e/get-window-handles driver)
              tab1 (first handles)
              tab2 (second handles)]

          (e/switch-window driver tab1)
          (e/wait-visible driver {:id :execute-script-button})
          (is (e/visible? driver {:id :execute-script-button}))

          (e/switch-window driver tab2)
          (e/wait-visible driver {:id :execute-script-button})
          (is (e/visible? driver {:id :execute-script-button}))

          (is (= "Initial content" (e/get-element-text driver {:id :content})))

          (e/switch-window driver tab1)
          (is (= "Initial content" (e/get-element-text driver {:id :content})))

          (e/click driver {:id :execute-script-button})
          (e/wait-predicate
           #(= "Broadcast script executed!" (e/get-element-text driver {:id :content})))
          (is (= "Broadcast script executed!" (e/get-element-text driver {:id :content})))

          (e/switch-window driver tab2)
          (e/wait-predicate
           #(= "Broadcast script executed!" (e/get-element-text driver {:id :content})))
          (is (= "Broadcast script executed!" (e/get-element-text driver {:id :content})))))
      (finally
        (e/quit driver)
        (ig/halt! server)))))

(defn push-signal-view []
  [:div {:id "view"}
   [:button
    {:id "increment-button"
     :data-on-click
     (core/handler []
      (core/push-signal! {:foo 42}))}
    "Update Signal"]
   [:div {:data-signals-foo "0"}
    [:input#signal-value {:data-bind-foo true}]]])

(deftest push-signal-test-with-sse
  (let [server (core/run push-signal-view test-options)
        driver (e/chrome-headless (driver-options))]
    (try
      (testing "Test push-signal! functionality with SSE enabled"
        (e/go driver test-url)
        (e/wait-visible driver {:id :increment-button})
        (is (e/visible? driver {:id :increment-button}))

        (e/click driver {:id :increment-button})

        (e/wait-predicate
         #(= "42" (e/get-element-value driver {:id :signal-value})))
        (is (= "42" (e/get-element-value driver {:id :signal-value}))))
      (finally
        (e/quit driver)
        (ig/halt! server)))))

(deftest push-signal-test-without-sse
  (let [server (core/run push-signal-view
                 (assoc-in test-options [:sse :enabled] false))
        driver (e/chrome-headless (driver-options))]
    (try
      (testing "Test push-signal! functionality with SSE disabled"
        (e/go driver test-url)
        (e/wait-visible driver {:id :increment-button})
        (is (e/visible? driver {:id :increment-button}))

        (e/click driver {:id :increment-button})

        (e/wait-predicate
         #(= "42" (e/get-element-value driver {:id :signal-value})))
        (is (= "42" (e/get-element-value driver {:id :signal-value}))))
      (finally
        (e/quit driver)
        (ig/halt! server)))))

(defn set-cookie-view []
  [:div {:id "view"}
   [:div#cookie-status "No cookie set"]
   [:button
    {:id "set-cookie-button"
     :data-on-click
     (core/handler []
      (core/set-cookie! "test-cookie=cookie-value; Path=/; Max-Age=3600")
      (core/push-script! "document.getElementById('cookie-status').textContent = 'Cookie set: ' + document.cookie;"))}
    "Set Cookie"]])

(deftest set-cookie-test
  (let [server (core/run set-cookie-view test-options)
        driver (e/chrome-headless (driver-options))]
    (try
      (testing "Test set-cookie! functionality"
        (e/go driver test-url)
        (e/wait-visible driver {:id :set-cookie-button})
        (is (e/visible? driver {:id :set-cookie-button}))
        (is (= "No cookie set" (e/get-element-text driver {:id :cookie-status})))

        (e/click driver {:id :set-cookie-button})

        (e/wait-predicate #(str/includes?
                            (e/get-element-text driver {:id :cookie-status})
                            "test-cookie=cookie-value"))

        (is (str/includes?
             (e/get-element-text driver {:id :cookie-status})
             "test-cookie=cookie-value")))
      (finally
        (e/quit driver)
        (ig/halt! server)))))

(defn session-management-view []
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
  (let [server (core/run session-management-view
                         (assoc test-options :jwt-secret "test-jwt-secret"))
        driver (e/chrome-headless (driver-options))]
    (try
      (testing "Test session management functionality"
        (e/go driver test-url)
        (e/wait-visible driver {:id :sign-in-button})
        (is (e/visible? driver {:id :sign-in-button}))
        (is (= "Not authenticated" (e/get-element-text driver {:id :auth-status})))

        (e/click driver {:id :sign-in-button})

        (e/wait-visible driver {:id :sign-out-button})
        (e/wait-predicate #(= "Authenticated as TestUser"
                             (e/get-element-text driver {:id :auth-status})))
        (is (= "Authenticated as TestUser"
               (e/get-element-text driver {:id :auth-status})))

        (e/click driver {:id :sign-out-button})

        (e/wait-visible driver {:id :sign-in-button})
        (e/wait-predicate #(= "Not authenticated"
                             (e/get-element-text driver {:id :auth-status})))
        (is (= "Not authenticated"
               (e/get-element-text driver {:id :auth-status}))))
      (finally
        (e/quit driver)
        (ig/halt! server)))))

(defn auth-required-view []
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
     (core/handler []
      {:auth-required? true}
      (core/push-script! "document.getElementById('protected-result').textContent = 'Protected action executed!';"))}
    "Execute Protected Action"]
   [:div#protected-result "Protected action not executed"]])

(deftest auth-required-handler-test
  (let [server (core/run auth-required-view
                         (assoc test-options :jwt-secret "test-jwt-secret"))
        driver (e/chrome-headless (driver-options))]
    (try
      (testing "Test auth-required handler functionality"
        (e/go driver test-url)
        (e/wait-visible driver {:id :protected-action-button})
        (is (e/visible? driver {:id :protected-action-button}))
        (is (= "Not authenticated" (e/get-element-text driver {:id :auth-status})))

        (e/click driver {:id :protected-action-button})
        (Thread/sleep 500)

        (is (= "Protected action not executed"
               (e/get-element-text driver {:id :protected-result})))

        (e/click driver {:id :sign-in-button})

        (e/wait-visible driver {:id :sign-out-button})
        (e/wait-predicate #(= "Authenticated as TestUser"
                             (e/get-element-text driver {:id :auth-status})))

        (e/click driver {:id :protected-action-button})

        (e/wait-predicate #(= "Protected action executed!"
                             (e/get-element-text driver {:id :protected-result})))
        (is (= "Protected action executed!"
               (e/get-element-text driver {:id :protected-result}))))
      (finally
        (e/quit driver)
        (ig/halt! server)))))

(deftest custom-handlers-test
  (testing "Test custom handlers functionality"
    (let [response-body (str (random-uuid))
          handlers [(GET "/custom-route" []
                      {:status 200
                       :headers {"Content-Type" "text/plain"}
                       :body response-body})]
          server (core/run (fn [] [:div "Test view"])
                           (assoc test-options :handlers handlers))
          response (slurp (str test-url "/custom-route"))]
      (try
        (is (= response-body response))
        (finally
          (ig/halt! server))))))

(deftest authenticated-test
  (testing "authenticated? returns true when identity is present"
    (let [request-with-identity {:identity {:name "TestUser"}}
          request-without-identity {}]
      (is (true? (core/authenticated? request-with-identity)))
      (is (false? (core/authenticated? request-without-identity)))
      (is (false? (core/authenticated? nil))))))

(deftest throw-unauthorized-test
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
          (is (= custom-data (-> (ex-data e) :weave.core/payload))))))))

(defn secure-handlers-view []
  [:div {:id "view"}
   [:div#auth-status
    (if (:identity core/*request*)
      (str "Authenticated as " (get-in core/*request* [:identity :name]))
      "Not authenticated")]
   [:button
    {:id "sign-in-button"
     :data-on-click
     (core/handler []
      {:auth-required? false}
      (core/set-cookie! (session/sign-in {:name "TestUser" :role "User"}))
      (core/push-reload!))}
    "Sign In"]
   [:button
    {:id "sign-out-button"
     :data-on-click
     (core/handler []
      {:auth-required? false}  ;; Explicitly allow unauthenticated access
      (core/set-cookie! (session/sign-out))
      (core/push-reload!))}
    "Sign Out"]
   [:button
    {:id "secure-action-button"
     :data-on-click
     (core/handler []
      (core/push-script! "document.getElementById('secure-result').textContent = 'Secure action executed!';"))}
    "Execute Secure Action"]
   [:button
    {:id "public-action-button"
     :data-on-click
     (core/handler []
      {:auth-required? false}
      (core/push-script! "document.getElementById('public-result').textContent = 'Public action executed!';"))}
    "Execute Public Action"]
   [:div#secure-result "Secure action not executed"]
   [:div#public-result "Public action not executed"]])

(deftest secure-handlers-test
  (let [server (core/run secure-handlers-view
                         (assoc test-options
                                :jwt-secret "test-jwt-secret"
                                :secure-handlers true))
        driver (e/chrome-headless (driver-options))]
    (try
      (testing "Test secure-handlers functionality"
        (e/go driver test-url)
        (e/wait-visible driver {:id :secure-action-button})
        (is (e/visible? driver {:id :secure-action-button}))
        (is (= "Not authenticated"
               (e/get-element-text driver {:id :auth-status})))

        ;; Try secure action while not authenticated - should fail
        (e/click driver {:id :secure-action-button})
        (Thread/sleep 100)
        (is (= "Secure action not executed"
               (e/get-element-text driver {:id :secure-result})))

        ;; Try public action while not authenticated - should work
        (e/click driver {:id :public-action-button})
        (e/wait-predicate
         #(= "Public action executed!"
             (e/get-element-text driver {:id :public-result})))
        (is (= "Public action executed!"
               (e/get-element-text driver {:id :public-result})))

        ;; Sign in
        (e/click driver {:id :sign-in-button})
        (e/wait-visible driver {:id :sign-out-button})
        (e/wait-predicate
         #(= "Authenticated as TestUser"
             (e/get-element-text driver {:id :auth-status})))

        ;; Try secure action while authenticated - should work
        (e/click driver {:id :secure-action-button})
        (e/wait-predicate
         #(= "Secure action executed!"
             (e/get-element-text driver {:id :secure-result})))
        (is (= "Secure action executed!"
               (e/get-element-text driver {:id :secure-result}))))
      (finally
        (e/quit driver)
        (ig/halt! server)))))

(def read-json
  (charred/parse-json-fn
   {:async? false :bufsize 1024 :key-fn keyword}))

(deftest with-icon-options-test
  (let [server (core/run (fn [] [:div "Icon Test"])
                         (assoc test-options
                                :icon "public/weave.png"
                                :title "Icon Test App"))
        driver (e/chrome-headless (driver-options))]
    (try
      (testing "Test icon options in HTML when icon is provided"
        (e/go driver test-url)

        (is (= "Icon Test App" (e/get-title driver)))

        (is (e/js-execute
             driver
             "return document.querySelector('link[rel=\"icon\"][href=\"/favicon.png\"]') !== null"))

        (is (e/js-execute
             driver
             "return document.querySelector('link[rel=\"apple-touch-icon\"][href=\"/icon-180.png\"]') !== null"))

        (is (e/js-execute
             driver
             "return document.querySelector('link[rel=\"manifest\"][href=\"/manifest.json\"]') !== null"))

        (let [manifest-json (slurp (str test-url "/manifest.json"))
              manifest (read-json manifest-json)]
          (is (= "Icon Test App" (:name manifest)))
          (is (= "Icon Test App" (:short_name manifest)))
          (is (vector? (:icons manifest)))
          (is (= 2 (count (:icons manifest))))
          (is (= "/icon-192.png" (-> manifest :icons first :src)))
          (is (= "/icon-512.png" (-> manifest :icons second :src)))))
      (finally
        (e/quit driver)
        (ig/halt! server)))))

(deftest without-icon-options-test
  (let [server (core/run (fn [] [:div "No Icon Test"])
                         (assoc test-options
                                :title "No Icon Test App"))
        driver (e/chrome-headless (driver-options))]
    (try
      (testing "Test that icon elements are not generated when no icon is provided"
        (e/go driver test-url)

        (is (= "No Icon Test App" (e/get-title driver)))

        (is (e/js-execute
             driver
             "return document.querySelector('link[rel=\"icon\"][href=\"/favicon.png\"]') === null"))

        (is (e/js-execute
             driver
             "return document.querySelector('link[rel=\"apple-touch-icon\"][href=\"/icon-180.png\"]') === null"))

        (is (e/js-execute
             driver
             "return document.querySelector('link[rel=\"manifest\"][href=\"/manifest.json\"]') === null")))
      (finally
        (e/quit driver)
        (ig/halt! server)))))

(deftest with-pwa-options-test
  (let [pwa-options {:name "PWA Test App"
                     :short-name "PWA Test"
                     :description "Testing PWA options"
                     :display "fullscreen"
                     :background-color "#ff0000"
                     :theme-color "#00ff00"
                     :start-url "/start"}
        server (core/run (fn [] [:div "PWA Test"])
                         (assoc test-options
                                :icon "public/weave.png"
                                :pwa pwa-options))
        driver (e/chrome-headless (driver-options))]
    (try
      (testing "Test PWA options in manifest when PWA options are provided"
        (e/go driver test-url)

        (is (e/js-execute
             driver
             "return document.querySelector('link[rel=\"manifest\"][href=\"/manifest.json\"]') !== null"))

        (let [manifest-json (slurp (str test-url "/manifest.json"))
              manifest (read-json manifest-json)]
          (is (= "PWA Test App" (:name manifest)))
          (is (= "PWA Test" (:short_name manifest)))
          (is (= "Testing PWA options" (:description manifest)))
          (is (= "fullscreen" (:display manifest)))
          (is (= "#ff0000" (:background_color manifest)))
          (is (= "#00ff00" (:theme_color manifest)))
          (is (= "/start" (:start_url manifest)))))
      (finally
        (e/quit driver)
        (ig/halt! server)))))

(deftest without-pwa-options-test
  (let [server (core/run (fn [] [:div "No PWA Test"])
                         (assoc test-options
                                :icon "public/weave.png"
                                :title "No PWA Test App"))
        driver (e/chrome-headless (driver-options))]
    (try
      (testing "Test default PWA options in manifest when no PWA options are provided"
        (e/go driver test-url)

        (is (e/js-execute
             driver
             "return document.querySelector('link[rel=\"manifest\"][href=\"/manifest.json\"]') !== null"))

        (let [manifest-json (slurp (str test-url "/manifest.json"))
              manifest (read-json manifest-json)]
          ;; Check default values are used
          (is (= "No PWA Test App" (:name manifest)))
          (is (= "No PWA Test App" (:short_name manifest)))
          (is (nil? (:description manifest)))
          (is (= "standalone" (:display manifest)))
          (is (= "#f2f2f2" (:background_color manifest)))
          (is (= "#ffffff" (:theme_color manifest)))
          (is (= "/" (:start_url manifest)))))
      (finally
        (e/quit driver)
        (ig/halt! server)))))

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
          server (core/run (fn [] [:div "Middleware Test"])
                           (assoc test-options
                                  :handlers [custom-route]
                                  :middleware [middleware-1 middleware-2]))
          response (slurp (str test-url "/middleware-test"))]

      (try
        (is (= "Middleware test endpoint" response))

        (is (= [:middleware-1-before
                :middleware-2-before
                :middleware-2-after
                :middleware-1-after]
               @middleware-execution-order))

        (let [url (java.net.URL. (str test-url "/middleware-test"))
              connection (.openConnection url)
              _ (.connect connection)
              headers (into {} (.getHeaderFields connection))]

          (is (= "applied" (first (get headers "X-Middleware-1"))))
          (is (= "applied" (first (get headers "X-Middleware-2")))))

        (finally
          (ig/halt! server))))))
