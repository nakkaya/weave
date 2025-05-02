(ns app.core
  (:gen-class)
  (:require
   [clojure.string]
   [reitit.core :as r]
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
         :type :primary
         :data-on-click (weave/handler
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
           [:li.flex.items-center.justify-between.p-3.bg-gray-50.rounded
            [:span.text-gray-700 x]
            [::c/button
             {:size :md
              :type :danger
              :data-on-click (weave/handler
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
         :data-on-submit (weave/handler
                          {:type :form}
                          (swap! todos conj (-> weave/*request* :params :bar))
                          (weave/push-html!
                           (todo-view)))}
        [:div {:class "flex space-x-2 m-3"}
         [::c/input
          {:name "bar"
           :placeholder "Add new todo item"
           :required true}]
         [::c/button
          {:button-type "submit"
           :size :md
           :type :primary}
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
           :type :primary
           :data-on-click (weave/handler
                           (weave/push-path! "/views/one" navigation-view))}
          "Page One"]
         [::c/button
          {:size :md
           :type :primary
           :data-on-click (weave/handler
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

(def routes
  (-> (r/router
       [["/sign-in" {:name :sign-in}]
        ["/app" {:name :app
                 :auth-required? true}]])))

(def router (weave/make-router))

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
      :type :primary
      :data-on-click (weave/handler
                      (weave/set-cookie! (session/sign-out))
                      (weave/broadcast-path! "/sign-in")
                      (weave/push-reload!))}
     "Sign Out"]]])

(defn session-sign-in-view []
  [:div
   [::c/alert.m-5 {:type :info}
    [:p
     "You are not logged in."]
    [:p.text-sm
     "Please sign in with your credentials."]]
   [:form
    {:class "mt-4 space-y-4"
     :data-on-submit
     (weave/handler
      (weave/set-cookie!
       (session/sign-in
        {:name (:username (:params weave/*request*)) :role "User"}))
      (weave/broadcast-path! "/app")
      (weave/push-reload!))}
    [:div {:class "text-left mb-3"}
     [::c/label {:for "username"}
      "Username"]
     [::c/input
      {:name "username"
       :placeholder "Enter your username"
       :required true}]]
    [:div {:class "text-left mb-4"}
     [::c/label {:for "password"}
      "Password"]
     [::c/input
      {:name "password"
       :type "password"
       :placeholder "Enter your password"
       :required true}]]
    [::c/row.justify-center
     [::c/button
      {:button-type "submit"
       :size :xl
       :type :primary}
      "Sign In"]]]])

(defn session-view []
  [::c/view#app
   [::c/center-hv
    [::c/card
     (case (router routes)
       :sign-in (session-sign-in-view)
       (session-app-view))]]])

(defn run [options]
  (let [view (condp = (:view options)
               :click-count #'click-count-view
               :todo #'todo-view
               :session #'session-view
               :navigation #'navigation-view)]
    (weave/run view options)))

(defn -main
  [& args]
  (let [[view] args
        view (keyword (or view "click-count"))]
    (run {:view view :port 8080})))

(comment
  (def s (weave/run
          #'click-count-view {:port 8080
                              :csrf-secret "my-csrf-secret"
                              :jwt-secret "my-jwt-secret"}))

  (s :timeout 100)
  ;;
  )
