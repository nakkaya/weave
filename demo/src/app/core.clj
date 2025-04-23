(ns app.core
  (:gen-class)
  (:require
   [clojure.string]
   [reitit.core :as r]
   [weave.core :as weave]
   [weave.session :as session]))

(defn tw
  "Combines multiple Tailwind CSS classes into a single string.
   Filters out nil values and trims whitespace."
  [& classes]
  (clojure.string/trim
   (clojure.string/join " "
                        (remove nil?
                                (map #(if (string? %)
                                        (clojure.string/trim %)
                                        %)
                                     classes)))))

(def view-container-class
  "max-w-md mx-auto mt-10 p-6 bg-white rounded-lg shadow-lg")

(def button-base-class "transition duration-200 rounded")
(def button-primary-class "bg-blue-500 hover:bg-blue-600 text-white")
(def button-danger-class "bg-red-500 hover:bg-red-600 text-white")
(def button-size-normal "px-4 py-2")
(def button-size-large "px-6 py-3 font-semibold text-lg")

(def input-base-class
  "border rounded focus:outline-none focus:ring-2 focus:ring-blue-500")
(def input-size-normal "px-4 py-2")
(def input-full-width "w-full")


(let [click-count (atom 0)]

  (defn click-count-view []
    [:div {:id "view" :class (tw view-container-class "text-center")}
     [:h1.text-2xl.font-bold.mb-4.text-gray-800
      "Counter Example"]
     [:div.text-6xl.font-bold.mb-6.text-blue-600
      @click-count]
     [:button
      {:class (tw button-primary-class button-size-large button-base-class)
       :data-on-click
       (weave/handler
        (swap! click-count inc)
        (weave/push-html!
         (click-count-view)))}
      "Increment Count"]]))


(let [todos (atom ["Pickup groceries"
                   "Finish Project"])]

  (defn todo-view []
    [:div {:id "view" :class view-container-class}
     [:h1.text-2xl.font-bold.mb-4.text-gray-800
      "Todo List"]
     [:ul {:class "space-y-2 mb-6"}
      (map-indexed (fn [idx x]
                     [:li.flex.items-center.justify-between.p-3.bg-gray-50.rounded
                      [:span.text-gray-700 x]
                      [:button
                       {:class (tw button-danger-class button-base-class "px-3 py-1")
                        :data-on-click
                        (weave/handler
                         (swap! todos (fn [items]
                                        (vec (concat
                                              (subvec items 0 idx)
                                              (subvec items (inc idx))))))
                         (weave/push-html!
                          (todo-view)))}
                       "Delete"]]) @todos)]
     [:form
      {:class "mt-4 space-y-4"
       :data-on-submit (weave/handler
                        {:type :form}
                        (swap! todos conj (-> weave/*request* :params :bar))
                        (weave/push-html!
                         (todo-view)))}
      [:div {:class "flex space-x-2 m-3"}
       [:input
        {:name "bar"
         :placeholder "Add new todo item"
         :required true
         :class (tw input-base-class input-size-normal "flex-grow")}]
       [:button
        {:class (tw button-primary-class button-size-normal button-base-class)}
        "Add"]]]]))

(let [router (r/router
              [["/views/one" ::view-one]
               ["/views/two" ::view-two]])]

  (defn navigation-view []
    [:div {:id "view" :class view-container-class}
     [:div.flex.justify-between.items-center.mb-6.bg-gray-200.p-3.rounded-lg
      [:a {:class (tw button-primary-class button-size-normal button-base-class)
           :data-on-click
           (weave/handler
            (weave/push-path! "/views/one" navigation-view))}
       "Page One"]
      [:a {:class (tw button-primary-class button-size-normal button-base-class)
           :data-on-click
           (weave/handler
            (weave/push-path! "/views/two" navigation-view))}
       "Page Two"]]

     [:div {:class "bg-white p-4 rounded-lg shadow"}
      (case (get-in (r/match-by-path router weave/*app-path*) [:data :name])
        ::view-one [:div.text-center
                    [:h2.text-xl.font-bold
                     "Page One Content"]
                    [:p
                     "This is the content for page one."]]
        ::view-two [:div.text-center
                    [:h2.text-xl.font-bold
                     "Page Two Content"]
                    [:p
                     "This is the content for page two."]]
        [:div.text-center.text-gray-500
         "Select a page from the navigation above"])]]))

(def routes
  (-> (r/router
       [["/sign-in" {:name :sign-in}]
        ["/app" {:name :app
                 :auth-required? true}]])))

(def router (weave/make-router))

(defn session-app-view []
  [:div
   [:div.mb-4.p-3.bg-green-100.text-green-800.rounded-lg
    [:p.font-semibold
     (str "Welcome, " (or (:name (:identity weave/*request*)) "User") "!")]
    [:p.text-sm
     "You are currently logged in."]]
   [:button
    {:class (tw button-danger-class button-size-large button-base-class)
     :data-on-click
     (weave/handler
      (weave/set-cookie! (session/sign-out))
      (weave/broadcast-path! "/sign-in")
      (weave/push-reload!))}
    "Sign Out"]])

(defn session-sign-in-view []
  [:div
   [:div.mb-4.p-3.bg-gray-100.text-gray-700.rounded-lg
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
     [:label {:for "username"
              :class "block text-sm font-medium text-gray-700 mb-1"}
      "Username"]
     [:input
      {:name "username"
       :id "username"
       :type "text"
       :placeholder "Enter your username"
       :required true
       :class (tw input-base-class input-size-normal input-full-width)}]]

    [:div {:class "text-left mb-4"}
     [:label {:for "password"
              :class "block text-sm font-medium text-gray-700 mb-1"}
      "Password"]
     [:input
      {:name "password"
       :id "password"
       :type "password"
       :placeholder "Enter your password"
       :required true
       :class (tw input-base-class input-size-normal input-full-width)}]]

    [:button
     {:class (tw button-primary-class button-size-large button-base-class "w-full")
      :type "submit"}
     "Sign In"]]])

(defn session-view []
  [:div {:id "view" :class (tw view-container-class "text-center")}
   [:h1.text-center.text-gray-500
    "Authentication Example"]
   (case (router routes)
     :sign-in (session-sign-in-view)
     (session-app-view))])

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
