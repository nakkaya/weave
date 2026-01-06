(ns app.core
  (:gen-class)
  (:require
   [clojure.string]
   [reitit.core :as r]
   [integrant.core :as ig]
   [weave.core :as weave]
   [weave.view :as view]
   [weave.session :as session]
   [weave.push :as push]
   [weave.components :as c]))

(let [click-count (atom 0)]

  (defn click-count-view []
    [::c/view#app
     [::c/center-hv
      [::c/card
       [:div.text-center.text-6xl.font-bold.mb-6.text-blue-600
        @click-count]
       [::c/button
        {:size :xl
         :variant :primary
         :data-on-click (weave/handler [click-count]
                         (swap! click-count inc)
                         (weave/push-html!
                          (click-count-view)))}
        "Increment Count"]]]]))


(let [todos (atom ["Pickup groceries"
                   "Finish Project"])]

  (defn todo-view []
    [::c/view#app
     [::c/row.justify-center
      [::c/card.mt-3
       [:h1.text-2xl.font-bold.mb-4.text-gray-800
        "Todo List"]
       [:ul {:class "space-y-2 mb-6"}
        (map-indexed
         (fn [idx x]
           [:li.flex.items-center.justify-between.p-3.rounded
            [:span.text-gray-700 x]
            [::c/button
             {:size :md
              :variant :danger
              :data-on-click (weave/handler [todos]
                              (swap! todos (fn [items]
                                             (vec (concat
                                                   (subvec items 0 idx)
                                                   (subvec items (inc idx))))))
                              (weave/push-html!
                               (todo-view)))}
             "Delete"]])
         @todos)]
       [:form
        {:class "mt-4 space-y-4"
         :data-on-submit (weave/handler ^{:type :form} [todos]
                          (swap! todos conj (-> weave/*request* :params :bar))
                          (weave/push-html!
                           (todo-view)))}
        [:div {:class "flex space-x-2 m-3"}
         [::c/input
          {:name "bar"
           :placeholder "Add new todo item"
           :required true}]
         [::c/button
          {:type "submit"
           :size :md
           :variant :primary}
          "Add"]]]]]]))

(defn welcome-view [_]
  [:div.text-center.text-gray-500
   "Select a page from the navigation above"])

(defn page-one-view [_]
  [:div.text-center.text-gray-500
   [:h2.text-xl.font-bold
    "Page One Content"]
   [:p "This is the content for page one."]])

(defn page-two-view [_]
  [:div.text-center.text-gray-500
   [:h2.text-xl.font-bold
    "Page Two Content"]
   [:p "This is the content for page two."]])

(def nav-app
  (-> (view/new {:id :nav-content
                 :default :welcome})
      (view/add {:id :welcome
                 :render #'welcome-view})
      (view/add {:id :page-one
                 :render #'page-one-view})
      (view/add {:id :page-two
                 :render #'page-two-view})))

(defn navigation-view []
  [::c/view#app
   [::c/col
    {:class "w-1/3 m-5"}
    [::c/row
     [::c/card.mb-5.w-full
      [::c/flex-between
       [::c/button
        {:size :md
         :variant :primary
         :href (view/href nav-app :page-one)
         :data-on-click (weave/handler [nav-app]
                          (view/render nav-app :page-one))}
        "Page One"]
       [::c/button
        {:size :md
         :variant :primary
         :href (view/href nav-app :page-two)
         :data-on-click (weave/handler [nav-app]
                          (view/render nav-app :page-two))}
        "Page Two"]]]]

    [::c/row
     [::c/card.w-full
      (view/render nav-app)]]]])

(def router
  (-> (r/router
       [["/sign-in" {:name :sign-in}]
        ["/app"     {:name :app}]])))

(defn session-app-view []
  [:div
   [::c/alert.m-5 {:type :info}
    [:p.font-semibold
     (str "Welcome, " (or (:name (:identity weave/*request*)) "User") "!")]
    [:p.text-sm
     "You are currently logged in."]]
   [::c/row.justify-center
    [::c/button
     {:size :xl
      :variant :primary
      :data-on-click (weave/handler []
                       (weave/set-cookie! (session/sign-out))
                       (weave/broadcast-path! "/sign-in")
                       (weave/push-reload!))}
     "Sign Out"]]])

(defn session-sign-in-view []
  [::c/sign-in
   {:title "Welcome Back"
    :username-label "Username"
    :username-placeholder "Enter your username"
    :password-label "Password"
    :password-placeholder "Enter your password"
    :submit-text "Sign In"
    :forgot-password-text "Forgot your password?"
    :forgot-password-url "/#/forgot-password"
    :register-text "Don't have an account?"
    :register-url "/#/register"
    :on-submit (weave/handler ^{:type :form} []
                 (weave/set-cookie!
                   (session/sign-in
                     {:name (:username (:params weave/*request*)) :role "User"}))
                 (weave/broadcast-path! "/app")
                 (weave/push-reload!))}])

(defn session-view []
  (if (:identity weave/*request*)
    (let [route-match (r/match-by-path
                       router weave/*app-path*)
          route-name (get-in route-match [:data :name])]
      [::c/view#app
       (case route-name
         :sign-in (session-sign-in-view)
         :app [::c/center-hv
               [::c/card
                (session-app-view)]])])
    (session-sign-in-view)))

(defn navbar-example []
  [::c/view#app
   [::c/navbar
    {:logo-url "/weave.svg"
     :title "Weave Demo"}

    [::c/navbar-item
     {:icon "solid-home"
      :active true
      :handler (weave/handler []
                 (println "Home clicked"))}
     "Home"]

    [::c/navbar-item
     {:icon "solid-user"
      :handler (weave/handler []
                 (println "Profile clicked"))}
     "Profile"]

    [::c/navbar-item
     {:icon "solid-cog"
      :handler (weave/handler []
                 (println "Settings clicked"))}
     "Settings"]]

   [::c/center-hv
    [::c/card
     [:h1.text-2xl.font-bold.mb-4 "Navbar Example"]
     [:p "This example demonstrates the navbar component."]]]])

(defn sidebar-example []
  [::c/view#app
   [::c/sidebar-layout
    [::c/sidebar
     {:logo-url "/weave.svg"
      :title "Weave Demo"}

     [::c/sidebar-group
      {:title "Main"}
      [::c/sidebar-item
       {:icon "solid-home"
        :active true
        :handler (weave/handler []
                   (println "Home clicked"))}
       "Home"]

      [::c/sidebar-item
       {:icon "solid-user"
        :handler (weave/handler []
                   (println "Profile clicked"))}
       "Profile"]

      [::c/sidebar-item
       {:icon "solid-cog"
        :handler (weave/handler []
                   (println "Settings clicked"))}
       "Settings"]]

     [::c/sidebar-group
      {:title "Content"
       :collapsed true}
      [::c/sidebar-item
       {:icon "solid-document-text"
        :handler (weave/handler []
                   (println "Documents clicked"))}
       "Documents"]

      [::c/sidebar-item
       {:icon "solid-photo"
        :handler (weave/handler []
                   (println "Photos clicked"))}
       "Photos"]]

     [::c/sidebar-group
      {:title "Tools"
       :collapsed false}
      [::c/sidebar-item
       {:icon "solid-wrench"
        :handler (weave/handler []
                   (println "Tools clicked"))}
       "Tools"]

      [::c/sidebar-item
       {:icon "solid-chart-bar"
        :handler (weave/handler []
                   (println "Analytics clicked"))}
       "Analytics"]]

     [:div.flex-1]

     [::c/sidebar-group
      {:title "Account"}
      [::c/sidebar-item
       {:icon "solid-user-circle"
        :handler (weave/handler []
                  (println "Account clicked"))}
       "My Account"]

      [::c/sidebar-item
       {:icon "solid-arrow-right-on-rectangle"
        :handler (weave/handler []
                  (println "Logout clicked"))}
       "Logout"]]]

    ;; Main content
    [::c/center-hv
     [::c/card
      [:h1.text-2xl.font-bold.mb-4 "Sidebar Example"]
      [:p "This example demonstrates the sidebar component with multiple groups and items."]
      [:p.mt-4.text-sm.text-gray-600 "Try toggling the sidebar on smaller screens to see the responsive behavior."]]]]])

(defn modal-example []
  [::c/view#app
   [::c/center-hv
    [::c/card
     [:h1.text-2xl.font-bold.mb-4 "Modal Example"]
     [:p.mb-6 "Click the button below to open a modal dialog."]

     [::c/modal
      {:id "demo_modal"
       :size :xl
       :title "Example Modal"}

      [:div
       [:div.m-4
        [:p.mb-4 "This is a modal dialog built with Weave components!"]
        [:p.mb-4 "Howto use this modal:"]
        [:ul.list-disc.list-inside.space-y-2.mb-4
         [:li "Each ::c/modal automatically declares a signal by its id"]
         [:li "Set the signal true or false to toggle the modal"]]]

       [::c/flex-between.m-4
        [::c/button
         {:variant :secondary
          :data-on-click "$demo_modal = false"}
         "Cancel"]
        [::c/button
         {:variant :primary
          :data-on-click (weave/handler []
                           (weave/push-signal! {:demo_modal false}))}
         "Confirm"]]]]

     [::c/button
      {:size :lg
       :variant :primary
       :data-on-click "$demo_modal = true"}
      "Open Modal"]]]])

(defn tabs-example []
  [::c/view#app
   [::c/col.max-w-4xl.mx-auto.p-6
    [:h1.text-2xl.font-bold.mb-6 "Tabs Example"]

    [::c/tabs
     [::c/tab-item
      {:icon "solid-user"
       :active true
       :handler (weave/handler []
                  (weave/push-html! [:h2#message.text-xl.font-bold "My Account"]))}
      "My Account"]

     [::c/tab-item
      {:icon "solid-building-office"
       :handler (weave/handler []
                  (weave/push-html! [:h2#message.text-xl.font-bold "Company"]))}
      "Company"]

     [::c/tab-item
      {:icon "solid-users"
       :handler (weave/handler []
                  (weave/push-html! [:h2#message.text-xl.font-bold "Team Members"]))}
      "Team Members"]

     [::c/tab-item
      {:icon "solid-credit-card"
       :handler (weave/handler []
                  (weave/push-html! [:h2#message.text-xl.font-bold "Billing"]))}
      "Billing"]]

    [::c/card#tab-content.mt-6
     [:h2#message.text-xl.font-bold "Team Members"]]]])

(def push-subscriptions (atom {}))
(def push-vapid-keys (push/generate-vapid-keypair))
(def push-options {:vapid-public-key (:public-key push-vapid-keys)
                   :vapid-private-key (:private-key push-vapid-keys)
                   :vapid-subject "mailto:demo@example.com"
                   :save-subscription! (fn [sid sub]
                                         (swap! push-subscriptions assoc sid sub)
                                         (println "Saved subscription for" sid))
                   :delete-subscription! (fn [sid _]
                                           (swap! push-subscriptions dissoc sid)
                                           (println "Deleted subscription for" sid))
                   :get-subscriptions (fn [sid]
                                        (when-let [sub (get @push-subscriptions sid)]
                                          [sub]))})

(defn push-example []
  [::c/view#app
   [::c/center-hv
    [::c/card
     [:h1.text-2xl.font-bold.mb-4 "Push Notifications"]
     [:p.mb-6.text-gray-600 "Test Web Push notifications"]

     [:div#push-status.mb-4.p-3.bg-gray-100.rounded
      "Status: Ready"]

     [:div.space-y-4
      [::c/button
       {:size :lg
        :variant :primary
        :class "w-full"
        :data-on-click (weave/handler []
                         (weave/push-script!
                          "weave.push.subscribe().then(() => {
                                 document.getElementById('push-status').textContent = 'Status: Subscribed!';
                               }).catch(e => {
                                 document.getElementById('push-status').textContent = 'Status: Error - ' + e.message;
                               });"))}
       "Subscribe to Push"]

      [::c/button
       {:size :lg
        :variant :secondary
        :class "w-full"
        :data-on-click (weave/handler [push-options]
                         (let [results (push/send!
                                        weave/*session-id*
                                        {:title "Hello from Weave!"
                                         :body "This is a test push notification"
                                         :url "/"}
                                        push-options)]
                           (weave/push-html!
                            [:div#push-status.mb-4.p-3.bg-gray-100.rounded
                             (if (some :success (vals results))
                               "Status: Push sent successfully!"
                               (str "Status: Push failed - " (pr-str results)))])))}
       "Send Push Notification"]

      [::c/button
       {:size :lg
        :variant :danger
        :class "w-full"
        :data-on-click (weave/handler []
                         (weave/push-script!
                          "weave.push.unsubscribe().then(() => {
                                 document.getElementById('push-status').textContent = 'Status: Unsubscribed';
                               }).catch(e => {
                                 document.getElementById('push-status').textContent = 'Status: Error - ' + e.message;
                               });"))}
       "Unsubscribe"]]]]])

(defn run [options]
  (let [view (condp = (:view options)
               :click-count #'click-count-view
               :todo #'todo-view
               :session #'session-view
               :navigation #'navigation-view
               :navbar #'navbar-example
               :sidebar #'sidebar-example
               :modal #'modal-example
               :tabs #'tabs-example
               :push #'push-example)
        push-opts (when (= :push (:view options))
                    push-options)]
    (weave/run view (cond-> options
                      push-opts (assoc :push push-opts)))))

(defn -main
  [& args]
  (let [[view] args
        view (keyword (or view "click-count"))]
    (run {:view view :port 8080})))

(comment
  (def s (weave/run
          #'sidebar-example {:port 8080
                             :icon "public/weave.png"
                             :csrf-secret "my-csrf-secret"
                             :jwt-secret "my-jwt-secret"}))

  (ig/halt! s)
  ;;
  )
