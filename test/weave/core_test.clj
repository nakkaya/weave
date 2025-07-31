(ns weave.core-test
  (:require
   [charred.api :as charred]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [compojure.core :refer [GET]]
   [etaoin.api :as e]
   [integrant.core :as ig]
   [weave.browser :refer [*browser* with-browser url weave-options] :as browser]
   [weave.core :as core]
   [weave.session :as session]))

(defn event-handler-fixture
  "Test fixture that binds a fresh event-handlers atom for each test."
  [test-fn]
  (binding [core/*event-handlers* (atom {})]
    (test-fn)))

(use-fixtures :each event-handler-fixture)

(defn clean-string [s]
  (-> s
      (str/replace #"\n" " ")
      (str/replace #"\s+" " ")
      str/trim))

(deftest request-headers-test
  (testing "Test request header options"
    (is (= "{}"
           (clean-string
            (#'core/request-options {}))))
    (is (= "{contentType: 'form'}"
           (clean-string
            (#'core/request-options {:type :form}))))
    (is (= "{openWhenHidden: true}"
           (clean-string
            (#'core/request-options {:keep-alive true}))))
    (is (= "{contentType: 'form', openWhenHidden: true}"
           (clean-string
            (#'core/request-options {:type :form :keep-alive true}))))
    (is (= "{selector: '#myform'}"
           (clean-string
            (#'core/request-options {:selector "#myform"}))))
    (is (= "{contentType: 'form', selector: '#myform'}"
           (clean-string
            (#'core/request-options {:type :form :selector "#myform"}))))
    (is (= "{contentType: 'form', openWhenHidden: true, selector: '#myform'}"
           (clean-string
            (#'core/request-options {:type :form :keep-alive true :selector "#myform"}))))))

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
        (str "document.getElementById('instance-id').textContent = window.weave.instance();"
             "document.getElementById('session-storage-id').textContent = sessionStorage.getItem('weave-tab-id');")))}
    "Get Instance ID"]])

(deftest multiple-tabs-different-instance-ids-test
  (with-browser instance-id-test-view weave-options
    (testing "Test that multiple tabs get different instance IDs"
      ;; Open first tab
      (e/wait-visible *browser* {:id :get-instance-id})
      (is (e/visible? *browser* {:id :get-instance-id}))

      ;; Get instance ID from first tab
      (e/click *browser* {:id :get-instance-id})
      (e/wait-predicate #(not= "Loading..." (e/get-element-text *browser* {:id :instance-id})))
      (let [tab1-instance-id (e/get-element-text *browser* {:id :instance-id})]
        (is (not (str/blank? tab1-instance-id)))

        ;; Open second tab
        (e/js-execute *browser* "window.open(arguments[0], '_blank');" url)
        (let [handles (e/get-window-handles *browser*)
              tab1 (first handles)
              tab2 (second handles)]

          ;; Switch to second tab and get its instance ID
          (e/switch-window *browser* tab2)
          (e/wait-visible *browser* {:id :get-instance-id})
          (e/click *browser* {:id :get-instance-id})
          (e/wait-predicate #(not= "Loading..." (e/get-element-text *browser* {:id :instance-id})))
          (let [tab2-instance-id (e/get-element-text *browser* {:id :instance-id})]
            (is (not (str/blank? tab2-instance-id)))

            ;; Verify that the two tabs have different instance IDs
            (is (not= tab1-instance-id tab2-instance-id)
                (str "Tab 1 ID: " tab1-instance-id ", Tab 2 ID: " tab2-instance-id))))))))

(deftest page-reload-preserves-instance-id-test
  (with-browser instance-id-test-view weave-options
    (testing "Test that page reloads preserve the same instance ID"
      ;; Open tab and get initial instance ID
      (e/wait-visible *browser* {:id :get-instance-id})
      (e/click *browser* {:id :get-instance-id})
      (e/wait-predicate #(not= "Loading..." (e/get-element-text *browser* {:id :instance-id})))
      (let [initial-instance-id (e/get-element-text *browser* {:id :instance-id})]
        (is (not (str/blank? initial-instance-id)))

        ;; Reload the page
        (e/refresh *browser*)
        (e/wait-visible *browser* {:id :get-instance-id})
        (e/click *browser* {:id :get-instance-id})
        (e/wait-predicate #(not= "Loading..." (e/get-element-text *browser* {:id :instance-id})))
        (let [reloaded-instance-id (e/get-element-text *browser* {:id :instance-id})]
          (is (not (str/blank? reloaded-instance-id)))

          ;; Verify that the instance ID is preserved across reload
          (is (= initial-instance-id reloaded-instance-id)
              (str "Initial ID: " initial-instance-id ", Reloaded ID: " reloaded-instance-id)))))))

(defn simple-view []
  [:div
   [:h1#content "Hello, Weave!"]])

(deftest page-load-test-with-sse
  (with-browser simple-view weave-options
    (testing "App renders with SSE enabled"
      (e/wait-visible *browser* {:id :content})
      (is (e/visible? *browser* {:id :content}))
      (is (= "Hello, Weave!" (e/get-element-text *browser* {:id :content}))))))

(deftest page-load-test-without-sse
  (with-browser simple-view (assoc-in weave-options [:sse :enabled] false)
    (testing "App renders with SSE disabled"
      (e/wait-visible *browser* {:id :content})
      (is (e/visible? *browser* {:id :content}))
      (is (= "Hello, Weave!" (e/get-element-text *browser* {:id :content}))))))

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
        view (fn [] (push-click-count-view counter))]
    (with-browser view weave-options
      (testing "push-html! updates the view with SSE enabled"
        (e/wait-visible *browser* {:id :increment-button})
        (is (e/visible? *browser* {:id :increment-button}))
        (is (= "41" (e/get-element-text *browser* {:id :count})))
        (e/click *browser* {:id :increment-button})
        (e/wait-predicate #(= "42" (e/get-element-text *browser* {:id :count})))
        (is (= "42" (e/get-element-text *browser* {:id :count})))))))

(deftest push-html-test-without-sse
  (let [counter (atom 41)
        view (fn [] (push-click-count-view counter))]
    (with-browser view (assoc-in weave-options [:sse :enabled] false)
      (testing "push-html! updates the view with SSE disabled"
        (e/wait-visible *browser* {:id :increment-button})
        (is (e/visible? *browser* {:id :increment-button}))
        (is (= "41" (e/get-element-text *browser* {:id :count})))
        (e/click *browser* {:id :increment-button})
        (e/wait-predicate #(= "42" (e/get-element-text *browser* {:id :count})))
        (is (= "42" (e/get-element-text *browser* {:id :count})))))))

(defn push-html-append-view []
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
  (let [view (fn [] (push-html-append-view))]
    (with-browser view weave-options
      (testing "push-html! with append mode adds elements to list"
        (e/wait-visible *browser* {:id :append-button})
        (is (e/visible? *browser* {:id :append-button}))

        (let [items (e/query-all *browser* {:css "#item-list li"})]
          (is (= 2 (count items))))

        (e/click *browser* {:id :append-button})

        (e/wait-predicate
         #(= 3 (count (e/query-all *browser* {:css "#item-list li"}))))

        (let [items (e/query-all *browser* {:css "#item-list li"})]
          (is (= 3 (count items))))

        (e/click *browser* {:id :append-button})

        (e/wait-predicate
         #(= 4 (count (e/query-all *browser* {:css "#item-list li"}))))

        (let [items (e/query-all *browser* {:css "#item-list li"})]
          (is (= 4 (count items))))))))

(defn broadcast-html-append-view []
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
  (let [view (fn [] (broadcast-html-append-view))]
    (with-browser view weave-options
      (testing "broadcast-html! with append mode adds elements to list across tabs"
        (e/wait-visible *browser* {:id :append-button})

        (e/js-execute *browser* "window.open(arguments[0], '_blank');" url)

        (let [handles (e/get-window-handles *browser*)
              tab1 (first handles)
              tab2 (second handles)]

          (e/switch-window *browser* tab1)
          (e/wait-visible *browser* {:id :append-button})
          (let [items (e/query-all *browser* {:css "#item-list li"})]
            (is (= 2 (count items))))

          (e/switch-window *browser* tab2)
          (e/wait-visible *browser* {:id :append-button})
          (let [items (e/query-all *browser* {:css "#item-list li"})]
            (is (= 2 (count items))))

          (e/switch-window *browser* tab1)
          (e/click *browser* {:id :append-button})

          (e/wait-predicate
           #(= 3 (count (e/query-all *browser* {:css "#item-list li"}))))

          (e/switch-window *browser* tab2)
          (e/wait-predicate
           #(= 3 (count (e/query-all *browser* {:css "#item-list li"}))))

          (let [items (e/query-all *browser* {:css "#item-list li"})]
            (is (= 3 (count items)))))))))

(defn form-submission-view []
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

(deftest form-submission-test-with-sse
  (with-browser form-submission-view weave-options
    (testing "Test form submission with SSE enabled"
      (e/wait-visible *browser* {:id :test-form})
      (is (e/visible? *browser* {:id :test-form}))

      (e/fill *browser* {:id :name} "John Doe")
      (e/fill *browser* {:id :email} "john@example.com")

      (e/click *browser* {:id :submit-btn})

      (e/wait-visible *browser* {:id :result})
      (is (e/visible? *browser* {:id :result}))

      (is (str/includes? (e/get-element-text *browser* {:id :result}) "Hello John Doe!"))
      (is (str/includes? (e/get-element-text *browser* {:id :result}) "Email: john@example.com")))))

(deftest form-submission-test-without-sse
  (with-browser form-submission-view (assoc-in weave-options [:sse :enabled] false)
    (testing "Test form submission without SSE"
      (e/wait-visible *browser* {:id :test-form})
      (is (e/visible? *browser* {:id :test-form}))

      (e/fill *browser* {:id :name} "Jane Smith")
      (e/fill *browser* {:id :email} "jane@example.com")

      (e/click *browser* {:id :submit-btn})

      (e/wait-visible *browser* {:id :result})
      (is (e/visible? *browser* {:id :result}))

      (is (str/includes? (e/get-element-text *browser* {:id :result}) "Hello Jane Smith!"))
      (is (str/includes? (e/get-element-text *browser* {:id :result}) "Email: jane@example.com")))))

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
        view (fn [] (broadcast-click-count-view counter))]
    (with-browser view weave-options
      (testing "broadcast-html! updates all connected tabs"
        ;; Open first tab
        (e/wait-visible *browser* {:id :increment-button})

        ;; Open a new tab using JavaScript
        (e/js-execute *browser* "window.open(arguments[0], '_blank');" url)

        ;; Get handles for both tabs
        (let [handles (e/get-window-handles *browser*)
              tab1 (first handles)
              tab2 (second handles)]

          ;; Make sure first tab is loaded
          (e/switch-window *browser* tab1)
          (e/wait-visible *browser* {:id :increment-button})
          (is (e/visible? *browser* {:id :increment-button}))

          ;; Make sure second tab is loaded
          (e/switch-window *browser* tab2)
          (e/wait-visible *browser* {:id :increment-button})
          (is (e/visible? *browser* {:id :increment-button}))

          ;; Verify initial state in both tabs
          (is (= "0" (e/get-element-text *browser* {:id :count})))

          (e/switch-window *browser* tab1)
          (is (= "0" (e/get-element-text *browser* {:id :count})))

          ;; Click button in first tab
          (e/click *browser* {:id :increment-button})
          (e/wait-predicate #(= "1" (e/get-element-text *browser* {:id :count})))

          ;; Switch to second tab and verify it was updated too
          (e/switch-window *browser* tab2)
          (e/wait-predicate #(= "1" (e/get-element-text *browser* {:id :count})))
          (is (= "1" (e/get-element-text *browser* {:id :count}))))))))

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
  (with-browser push-path-view weave-options
    (testing "Test push-path! functionality with SSE enabled"
      (e/wait-visible *browser* {:id :default-content})
      (is (e/visible? *browser* {:id :default-content}))
      (e/click *browser* {:id :trigger-view-two})
      (e/wait-predicate
       #(= "Page Two Content" (e/get-element-text *browser* {:id :page-two-content})))
      (is (= "Page Two Content" (e/get-element-text *browser* {:id :page-two-content}))))))

(deftest push-path-test-without-sse
  (with-browser push-path-view (assoc-in weave-options [:sse :enabled] false)
    (testing "Test push-path! functionality with SSE disabled"
      (e/wait-visible *browser* {:id :default-content})
      (is (e/visible? *browser* {:id :default-content}))
      (e/click *browser* {:id :trigger-view-two})
      (e/wait-predicate
       #(= "Page Two Content" (e/get-element-text *browser* {:id :page-two-content})))
      (is (= "Page Two Content" (e/get-element-text *browser* {:id :page-two-content}))))))

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
  (with-browser broadcast-path-view weave-options
    (testing "Test broadcast-path! functionality across multiple tabs"
        ;; Open first tab
      (e/wait-visible *browser* {:id :default-content})
      (is (e/visible? *browser* {:id :default-content}))

      (e/js-execute *browser* "window.open(arguments[0], '_blank');" url)

      (let [handles (e/get-window-handles *browser*)
            tab1 (first handles)
            tab2 (second handles)]

        (e/switch-window *browser* tab1)
        (e/wait-visible *browser* {:id :default-content})
        (is (e/visible? *browser* {:id :default-content}))

        (e/switch-window *browser* tab2)
        (e/wait-visible *browser* {:id :default-content})
        (is (e/visible? *browser* {:id :default-content}))

        (e/switch-window *browser* tab1)
        (e/click *browser* {:id :trigger-view-one})
        (e/wait-predicate
         #(= "Page One Content" (e/get-element-text *browser* {:id :page-one-content})))
        (is (= "Page One Content" (e/get-element-text *browser* {:id :page-one-content})))

        (e/switch-window *browser* tab2)
        (e/wait-predicate
         #(= "Page One Content" (e/get-element-text *browser* {:id :page-one-content})))
        (is (= "Page One Content" (e/get-element-text *browser* {:id :page-one-content})))

        (e/click *browser* {:id :trigger-view-two})
        (e/wait-predicate
         #(= "Page Two Content" (e/get-element-text *browser* {:id :page-two-content})))
        (is (= "Page Two Content" (e/get-element-text *browser* {:id :page-two-content})))

        (e/switch-window *browser* tab1)
        (e/wait-predicate
         #(= "Page Two Content" (e/get-element-text *browser* {:id :page-two-content})))
        (is (= "Page Two Content" (e/get-element-text *browser* {:id :page-two-content})))))))

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
  (with-browser manual-hash-change-view weave-options
    (testing "Test manual hash change detection"
      (e/wait-visible *browser* {:id :default-content})
      (is (e/visible? *browser* {:id :default-content}))

      (e/js-execute
       *browser* "window.location.hash = '#/views/one'; window.location.reload();")

      (e/wait-visible *browser* {:id :page-one-content})
      (is (= "Page One Content" (e/get-element-text *browser* {:id :page-one-content}))))))

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
  (with-browser push-script-view weave-options
    (testing "Test push-script! functionality with SSE enabled"
      (e/wait-visible *browser* {:id :execute-script-button})
      (is (e/visible? *browser* {:id :execute-script-button}))
      (is (= "Initial content" (e/get-element-text *browser* {:id :content})))

      (e/click *browser* {:id :execute-script-button})
      (e/wait-predicate #(= "Script executed!" (e/get-element-text *browser* {:id :content})))
      (is (= "Script executed!" (e/get-element-text *browser* {:id :content}))))))

(deftest push-script-test-without-sse
  (with-browser push-script-view (assoc-in weave-options [:sse :enabled] false)
    (testing "Test push-script! functionality with SSE disabled"
      (e/wait-visible *browser* {:id :execute-script-button})
      (is (e/visible? *browser* {:id :execute-script-button}))
      (is (= "Initial content" (e/get-element-text *browser* {:id :content})))

      (e/click *browser* {:id :execute-script-button})
      (e/wait-predicate #(= "Script executed!" (e/get-element-text *browser* {:id :content})))
      (is (= "Script executed!" (e/get-element-text *browser* {:id :content}))))))

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
  (with-browser broadcast-script-view weave-options
    (testing "Test broadcast-script! functionality across multiple tabs"
      (e/wait-visible *browser* {:id :execute-script-button})
      (is (e/visible? *browser* {:id :execute-script-button}))
      (is (= "Initial content" (e/get-element-text *browser* {:id :content})))

      (e/js-execute *browser* "window.open(arguments[0], '_blank');" url)

      (let [handles (e/get-window-handles *browser*)
            tab1 (first handles)
            tab2 (second handles)]

        (e/switch-window *browser* tab1)
        (e/wait-visible *browser* {:id :execute-script-button})
        (is (e/visible? *browser* {:id :execute-script-button}))

        (e/switch-window *browser* tab2)
        (e/wait-visible *browser* {:id :execute-script-button})
        (is (e/visible? *browser* {:id :execute-script-button}))

        (is (= "Initial content" (e/get-element-text *browser* {:id :content})))

        (e/switch-window *browser* tab1)
        (is (= "Initial content" (e/get-element-text *browser* {:id :content})))

        (e/click *browser* {:id :execute-script-button})
        (e/wait-predicate
         #(= "Broadcast script executed!" (e/get-element-text *browser* {:id :content})))
        (is (= "Broadcast script executed!" (e/get-element-text *browser* {:id :content})))

        (e/switch-window *browser* tab2)
        (e/wait-predicate
         #(= "Broadcast script executed!" (e/get-element-text *browser* {:id :content})))
        (is (= "Broadcast script executed!" (e/get-element-text *browser* {:id :content})))))))

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
  (with-browser push-signal-view weave-options
    (testing "Test push-signal! functionality with SSE enabled"
      (e/wait-visible *browser* {:id :increment-button})
      (is (e/visible? *browser* {:id :increment-button}))

      (e/click *browser* {:id :increment-button})

      (e/wait-predicate
       #(= "42" (e/get-element-value *browser* {:id :signal-value})))
      (is (= "42" (e/get-element-value *browser* {:id :signal-value}))))))

(deftest push-signal-test-without-sse
  (with-browser push-signal-view (assoc-in weave-options [:sse :enabled] false)
    (testing "Test push-signal! functionality with SSE disabled"
      (e/wait-visible *browser* {:id :increment-button})
      (is (e/visible? *browser* {:id :increment-button}))

      (e/click *browser* {:id :increment-button})

      (e/wait-predicate
       #(= "42" (e/get-element-value *browser* {:id :signal-value})))
      (is (= "42" (e/get-element-value *browser* {:id :signal-value}))))))

(defn local-signal-view []
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

(deftest local-signal-test-with-sse
  (with-browser local-signal-view weave-options
    (testing "Test local signal with underscore prefix"
      (e/wait-visible *browser* {:id :show-button})
      (is (e/visible? *browser* {:id :show-button}))

        ;; Initially the div should be hidden
      (is (not (e/visible? *browser* {:id :hidden-div})))

        ;; Click button to show the div
      (e/click *browser* {:id :show-button})

        ;; Wait for the div to become visible
      (e/wait-visible *browser* {:id :hidden-div})
      (is (e/visible? *browser* {:id :hidden-div})))))

(deftest local-signal-test-without-sse
  (with-browser local-signal-view (assoc-in weave-options [:sse :enabled] false)
    (testing "Test local signal with underscore prefix."
      (e/wait-visible *browser* {:id :show-button})
      (is (e/visible? *browser* {:id :show-button}))

        ;; Initially the div should be hidden
      (is (not (e/visible? *browser* {:id :hidden-div})))

        ;; Click button to show the div
      (e/click *browser* {:id :show-button})

        ;; Wait for the div to become visible
      (e/wait-visible *browser* {:id :hidden-div})
      (is (e/visible? *browser* {:id :hidden-div})))))

(defn data-call-with-view []
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

(deftest data-call-with-test-with-sse
  (with-browser data-call-with-view weave-options
    (testing "Test data-call-with attribute functionality with SSE enabled"
      (e/wait-visible *browser* {:id :edit-button})
      (is (e/visible? *browser* {:id :edit-button}))
      (is (= "No action performed yet" (e/get-element-text *browser* {:id :result})))

      (e/click *browser* {:id :edit-button})
      (e/wait-predicate
       #(= "Action: edit, Item: 123" (e/get-element-text *browser* {:id :result})))
      (is (= "Action: edit, Item: 123" (e/get-element-text *browser* {:id :result})))

      (e/click *browser* {:id :delete-button})
      (e/wait-predicate
       #(= "Action: delete, Item: 456" (e/get-element-text *browser* {:id :result})))
      (is (= "Action: delete, Item: 456" (e/get-element-text *browser* {:id :result}))))))

(deftest data-call-with-test-without-sse
  (with-browser data-call-with-view (assoc-in weave-options [:sse :enabled] false)
    (testing "Test data-call-with attribute functionality with SSE disabled"
      (e/wait-visible *browser* {:id :edit-button})
      (is (e/visible? *browser* {:id :edit-button}))
      (is (= "No action performed yet" (e/get-element-text *browser* {:id :result})))

      (e/click *browser* {:id :edit-button})
      (e/wait-predicate
       #(= "Action: edit, Item: 123" (e/get-element-text *browser* {:id :result})))
      (is (= "Action: edit, Item: 123" (e/get-element-text *browser* {:id :result})))

      (e/click *browser* {:id :delete-button})
      (e/wait-predicate
       #(= "Action: delete, Item: 456" (e/get-element-text *browser* {:id :result})))
      (is (= "Action: delete, Item: 456" (e/get-element-text *browser* {:id :result}))))))

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
  (with-browser set-cookie-view weave-options
    (testing "Test set-cookie! functionality"
      (e/wait-visible *browser* {:id :set-cookie-button})
      (is (e/visible? *browser* {:id :set-cookie-button}))
      (is (= "No cookie set" (e/get-element-text *browser* {:id :cookie-status})))

      (e/click *browser* {:id :set-cookie-button})

      (e/wait-predicate #(str/includes?
                          (e/get-element-text *browser* {:id :cookie-status})
                          "test-cookie=cookie-value"))

      (is (str/includes?
           (e/get-element-text *browser* {:id :cookie-status})
           "test-cookie=cookie-value")))))

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
  (with-browser session-management-view (assoc weave-options :jwt-secret "test-jwt-secret")
    (testing "Test session management functionality"
      (e/wait-visible *browser* {:id :sign-in-button})
      (is (e/visible? *browser* {:id :sign-in-button}))
      (is (= "Not authenticated" (e/get-element-text *browser* {:id :auth-status})))

      (e/click *browser* {:id :sign-in-button})

      (e/wait-visible *browser* {:id :sign-out-button})
      (e/wait-predicate #(= "Authenticated as TestUser"
                            (e/get-element-text *browser* {:id :auth-status})))
      (is (= "Authenticated as TestUser"
             (e/get-element-text *browser* {:id :auth-status})))

      (e/click *browser* {:id :sign-out-button})

      (e/wait-visible *browser* {:id :sign-in-button})
      (e/wait-predicate #(= "Not authenticated"
                            (e/get-element-text *browser* {:id :auth-status})))
      (is (= "Not authenticated"
             (e/get-element-text *browser* {:id :auth-status}))))))

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
     (core/handler ^{:auth-required? true} []
       (core/push-script! "document.getElementById('protected-result').textContent = 'Protected action executed!';"))}
    "Execute Protected Action"]
   [:div#protected-result "Protected action not executed"]])

(deftest auth-required-handler-test
  (with-browser auth-required-view (assoc weave-options :jwt-secret "test-jwt-secret")
    (testing "Test auth-required handler functionality"
      (e/wait-visible *browser* {:id :protected-action-button})
      (is (e/visible? *browser* {:id :protected-action-button}))
      (is (= "Not authenticated" (e/get-element-text *browser* {:id :auth-status})))

      (e/click *browser* {:id :protected-action-button})
      (Thread/sleep 500)

      (is (= "Protected action not executed"
             (e/get-element-text *browser* {:id :protected-result})))

      (e/click *browser* {:id :sign-in-button})

      (e/wait-visible *browser* {:id :sign-out-button})
      (e/wait-predicate #(= "Authenticated as TestUser"
                            (e/get-element-text *browser* {:id :auth-status})))

      (e/click *browser* {:id :protected-action-button})

      (e/wait-predicate #(= "Protected action executed!"
                            (e/get-element-text *browser* {:id :protected-result})))
      (is (= "Protected action executed!"
             (e/get-element-text *browser* {:id :protected-result}))))))

(deftest custom-handlers-test
  (testing "Test custom handlers functionality"
    (let [response-body (str (random-uuid))
          handlers [(GET "/custom-route" []
                      {:status 200
                       :headers {"Content-Type" "text/plain"}
                       :body response-body})]]
      (with-browser (fn [] [:div "Test view"])
        (assoc weave-options :handlers handlers)
        (let [response (slurp (str url "/custom-route"))]
          (is (= response-body response)))))))

(deftest authenticated-test
  (with-browser (fn [] [:div]) weave-options
    (testing "authenticated? returns true when identity is present"
      (let [request-with-identity {:identity {:name "TestUser"}}
            request-without-identity {}]
        (is (true? (core/authenticated? request-with-identity)))
        (is (false? (core/authenticated? request-without-identity)))
        (is (false? (core/authenticated? nil)))))))

(deftest throw-unauthorized-test
  (with-browser (fn [] [:div]) weave-options
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

(defn secure-handlers-view []
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
       (core/push-script! "document.getElementById('secure-result').textContent = 'Secure action executed!';"))}
    "Execute Secure Action"]
   [:button
    {:id "public-action-button"
     :data-on-click
     (core/handler ^{:auth-required? false} []
       (core/push-script! "document.getElementById('public-result').textContent = 'Public action executed!';"))}
    "Execute Public Action"]
   [:div#secure-result "Secure action not executed"]
   [:div#public-result "Public action not executed"]])

(deftest secure-handlers-test
  (with-browser secure-handlers-view (assoc weave-options
                                            :jwt-secret "test-jwt-secret"
                                            :secure-handlers true)
    (testing "Test secure-handlers functionality"
      (e/wait-visible *browser* {:id :secure-action-button})
      (is (e/visible? *browser* {:id :secure-action-button}))
      (is (= "Not authenticated"
             (e/get-element-text *browser* {:id :auth-status})))

        ;; Try secure action while not authenticated - should fail
      (e/click *browser* {:id :secure-action-button})
      (Thread/sleep 100)
      (is (= "Secure action not executed"
             (e/get-element-text *browser* {:id :secure-result})))

        ;; Try public action while not authenticated - should work
      (e/click *browser* {:id :public-action-button})
      (e/wait-predicate
       #(= "Public action executed!"
           (e/get-element-text *browser* {:id :public-result})))
      (is (= "Public action executed!"
             (e/get-element-text *browser* {:id :public-result})))

        ;; Sign in
      (e/click *browser* {:id :sign-in-button})
      (e/wait-visible *browser* {:id :sign-out-button})
      (e/wait-predicate
       #(= "Authenticated as TestUser"
           (e/get-element-text *browser* {:id :auth-status})))

        ;; Try secure action while authenticated - should work
      (e/click *browser* {:id :secure-action-button})
      (e/wait-predicate
       #(= "Secure action executed!"
           (e/get-element-text *browser* {:id :secure-result})))
      (is (= "Secure action executed!"
             (e/get-element-text *browser* {:id :secure-result}))))))

(def read-json
  (charred/parse-json-fn
   {:async? false :bufsize 1024 :key-fn keyword}))

(deftest with-icon-options-test
  (with-browser (fn [] [:div "Icon Test"])
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
  (with-browser (fn [] [:div "No Icon Test"]) (assoc weave-options :title "No Icon Test App")
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
    (with-browser (fn [] [:div "PWA Test"]) (assoc weave-options
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
  (with-browser (fn [] [:div "No PWA Test"]) (assoc weave-options
                                                    :icon "public/weave.png"
                                                    :title "No PWA Test App")
    (testing "Test default PWA options in manifest when no PWA options are provided"

      (is (e/js-execute
           *browser*
           "return document.querySelector('link[rel=\"manifest\"][href=\"/manifest.json\"]') !== null"))

      (let [manifest-json (slurp (str url "/manifest.json"))
            manifest (read-json manifest-json)]
          ;; Check default values are used
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
