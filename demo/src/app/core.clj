(ns app.core
  (:gen-class)
  (:require
   [clojure.string]
   [reitit.core :as r]
   [integrant.core :as ig]
   [weave.core :as weave]
   [weave.session :as session]
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

(let [router (r/router
              [["/views/one" ::view-one]
               ["/views/two" ::view-two]])]

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
           :data-on-click (weave/handler [navigation-view]
                           (weave/push-path! "/views/one" navigation-view))}
          "Page One"]
         [::c/button
          {:size :md
           :variant :primary
           :data-on-click (weave/handler [navigation-view]
                           (weave/push-path! "/views/two" navigation-view))}
          "Page Two"]]]]

      [::c/row
       [::c/card.w-full
        (case (get-in (r/match-by-path router weave/*app-path*) [:data :name])
          ::view-one [:div.text-center
                      [:h2.text-xl.font-bold
                       "Page One Content"]
                      [:p "This is the content for page one."]]
          ::view-two [:div.text-center
                      [:h2.text-xl.font-bold
                       "Page Two Content"]
                      [:p "This is the content for page two."]]
          [:div.text-center.text-gray-500
           "Select a page from the navigation above"])]]]]))

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
         :app     [::c/center-hv
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
    ;; Pre-built sidebar
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
      {:title "Content"}
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

     ;; Spacer to push content to bottom
     [:div.flex-1]

     ;; Bottom section
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

(defn run [options]
  (let [view (condp = (:view options)
               :click-count #'click-count-view
               :todo #'todo-view
               :session #'session-view
               :navigation #'navigation-view
               :navbar #'navbar-example
               :sidebar #'sidebar-example
               :modal #'modal-example)]
    (weave/run view options)))

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
