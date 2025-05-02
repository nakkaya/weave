(ns weave.components
  (:require
   [dev.onionpancakes.chassis.core :as c]
   [clojure.string :as str]))

(def ^:dynamic *theme*
  {:view {:bg "bg-white dark:bg-gray-900"}
   :card {:bg "bg-white dark:bg-gray-800"
          :border "border border-gray-300 dark:border-gray-700"
          :shadow "shadow-sm"}
   :card-with-header {:bg "bg-white dark:bg-gray-800"
                      :border "divide-y divide-gray-300 dark:divide-gray-700"
                      :shadow "shadow-sm"}
   :button {:primary {:bg "bg-indigo-600 dark:bg-indigo-500"
                      :hover "hover:bg-indigo-500 dark:hover:bg-indigo-400"
                      :focus "focus-visible:outline-indigo-600 dark:focus-visible:outline-indigo-500"
                      :text "text-white"}
            :danger {:bg "bg-red-600 dark:bg-red-500"
                     :hover "hover:bg-red-500 dark:hover:bg-red-400"
                     :focus "focus-visible:outline-red-600 dark:focus-visible:outline-red-500"
                     :text "text-white"}
            :secondary {:bg "bg-gray-200 dark:bg-gray-700"
                        :hover "hover:bg-gray-300 dark:hover:bg-gray-600"
                        :focus "focus-visible:outline-gray-600 dark:focus-visible:outline-gray-400"
                        :text "text-gray-900 dark:text-gray-100"}}
   :input {:border "ring-gray-300 dark:ring-gray-700"
           :focus "focus:ring-indigo-600 dark:focus:ring-indigo-500"
           :bg "bg-white dark:bg-gray-800"
           :text "text-gray-900 dark:text-gray-100"
           :placeholder "placeholder:text-gray-400"}
   :navbar {:bg "bg-gray-800 dark:bg-gray-900"
            :text "text-gray-300 dark:text-gray-300"
            :hover "hover:bg-gray-700 hover:text-white dark:hover:bg-gray-800 dark:hover:text-white"}})

(defn get-theme-class
  "Get a class string from the theme configuration"
  [component-type & path]
  (let [path-vec (into [component-type] path)
        class-str (get-in *theme* path-vec)]
    class-str))

#_:clj-kondo/ignore
(defn with-theme
  "Execute body with a custom theme configuration"
  [custom-theme & body]
  (binding [*theme* (merge-with merge *theme* custom-theme)]
    (do body)))

(defn- merge-classes
  "Merge base class with custom class if provided."
  [base-class custom-class]
  (if (and custom-class (not (str/blank? custom-class)))
    (str base-class " " custom-class)
    base-class))

(defn- merge-attrs
  "Merge component base attributes with user attributes.
   Special handling for :class to combine rather than override."
  [base-attrs user-attrs]
  (let [base-class (:class base-attrs)
        user-class (:class user-attrs)
        merged-class (merge-classes base-class user-class)]
    (-> (merge base-attrs user-attrs)
        (assoc :class merged-class))))

(defmethod c/resolve-alias ::view
  [_ attrs content]
  (let [theme-bg (or (:bg-class attrs) (get-theme-class :view :bg))
        base-attrs {:class (str "w-full h-full " theme-bg)}
        filtered-attrs (dissoc attrs :bg-class)
        merged-attrs (merge-attrs base-attrs filtered-attrs)]
    [:div merged-attrs
     content]))

(defmethod c/resolve-alias ::row
  [_ attrs content]
  (let [base-attrs {:class "flex flex-row"}
        merged-attrs (merge-attrs base-attrs attrs)]
    [:div merged-attrs
     content]))

(defmethod c/resolve-alias ::col
  [_ attrs content]
  (let [base-attrs {:class "flex flex-col"}
        merged-attrs (merge-attrs base-attrs attrs)]
    [:div merged-attrs
     content]))

(defmethod c/resolve-alias ::flex-between
  [_ attrs content]
  (let [base-attrs {:class "flex justify-between items-center"}
        merged-attrs (merge-attrs base-attrs attrs)]
    [:div merged-attrs
     content]))

(defmethod c/resolve-alias ::center-hv
  [_ attrs content]
  (let [base-attrs {:class "w-full h-full flex items-center justify-center"}
        merged-attrs (merge-attrs base-attrs attrs)]
    [:div merged-attrs
     content]))

(defmethod c/resolve-alias ::flex-between
  [_ attrs content]
  (let [base-attrs {:class "flex justify-between items-center"}
        merged-attrs (merge-attrs base-attrs attrs)]
    [:div merged-attrs
     content]))

(defmethod c/resolve-alias ::card
  [_ attrs content]
  (let [theme-bg (or (:bg-class attrs) (get-theme-class :card :bg))
        theme-border (or (:border-class attrs) (get-theme-class :card :border))
        theme-shadow (or (:shadow-class attrs) (get-theme-class :card :shadow))
        base-attrs {:class (str "overflow-hidden "
                                theme-bg " " theme-border " " theme-shadow
                                " sm:rounded-lg ring-1 ring-gray-200 dark:ring-gray-700")}
        filtered-attrs (dissoc attrs :bg-class :border-class :shadow-class)
        merged-attrs (merge-attrs base-attrs filtered-attrs)]
    [:div merged-attrs
     [:div {:class "px-4 py-5 sm:p-6"}
      content]]))

(defmethod c/resolve-alias ::card-with-header
  [_ attrs content]
  (let [theme-bg (or (:bg-class attrs) (get-theme-class :card-with-header :bg))
        theme-border (or (:border-class attrs) (get-theme-class :card-with-header :border))
        theme-shadow (or (:shadow-class attrs) (get-theme-class :card-with-header :shadow))
        base-attrs {:class (str theme-border
                                " overflow-hidden rounded-lg "
                                theme-bg " " theme-shadow
                                " ring-1 ring-gray-200 dark:ring-gray-700")}
        filtered-attrs (dissoc attrs :bg-class :border-class :shadow-class)
        merged-attrs (merge-attrs base-attrs filtered-attrs)
        header (first content)
        body (rest content)]
    [:div merged-attrs
     [:div {:class "px-4 py-5 sm:px-6"} header]
     [:div {:class "px-4 py-5 sm:p-6"} body]]))

(defmethod c/resolve-alias ::button
  [_ attrs content]
  (let [size (:size attrs)
        text (or (first content) (:title attrs))
        button-type (or (:type attrs) :primary)
        type-style {:bg (or (:bg-class attrs)
                            (get-theme-class :button button-type :bg))
                    :hover (or (:hover-class attrs)
                               (get-theme-class :button button-type :hover))
                    :focus (or (:focus-class attrs)
                               (get-theme-class :button button-type :focus))
                    :text (or (:text-class attrs)
                              (get-theme-class :button button-type :text))}
        button-sizes
        {:xs {:class (str "rounded-sm "
                          (:bg type-style)
                          " px-2 py-1 text-xs font-semibold "
                          (:text type-style)
                          " shadow-xs "
                          (:hover type-style)
                          " focus-visible:outline-2 focus-visible:outline-offset-2 "
                          (:focus type-style))}
         :s {:class (str "rounded-sm "
                         (:bg type-style)
                         " px-2 py-1 text-sm font-semibold "
                         (:text type-style)
                         " shadow-xs "
                         (:hover type-style)
                         " focus-visible:outline-2 focus-visible:outline-offset-2 "
                         (:focus type-style))}
         :md {:class (str "rounded-md "
                          (:bg type-style)
                          " px-2.5 py-1.5 text-base font-semibold "
                          (:text type-style)
                          " shadow-xs "
                          (:hover type-style)
                          " focus-visible:outline-2 focus-visible:outline-offset-2 "
                          (:focus type-style))}
         :lg {:class (str "rounded-md "
                          (:bg type-style)
                          " px-3 py-2 text-lg font-semibold "
                          (:text type-style)
                          " shadow-xs "
                          (:hover type-style)
                          " focus-visible:outline-2 focus-visible:outline-offset-2 "
                          (:focus type-style))}
         :xl {:class (str "rounded-md "
                          (:bg type-style)
                          " px-3.5 py-2.5 text-xl font-semibold "
                          (:text type-style)
                          " shadow-xs "
                          (:hover type-style)
                          " focus-visible:outline-2 focus-visible:outline-offset-2 "
                          (:focus type-style))}}
        style (get button-sizes size)
        base-attrs {:type (or (:button-type attrs) "button")
                    :class (:class style)}
        filtered-attrs (dissoc attrs :size :title :type :on-click)
        merged-attrs (merge-attrs base-attrs filtered-attrs)]
    [:button merged-attrs text]))

(defmethod c/resolve-alias ::input
  [_ attrs _content]
  (let [input-type (or (:type attrs) "text")
        size (or (:size attrs) :md)
        placeholder (or (:placeholder attrs) "")
        value (or (:value attrs) "")
        name (or (:name attrs) "")
        theme-border (or (:border-class attrs) (get-theme-class :input :border))
        theme-focus (or (:focus-class attrs) (get-theme-class :input :focus))
        theme-bg (or (:bg-class attrs) (get-theme-class :input :bg))
        theme-text (or (:text-class attrs) (get-theme-class :input :text))
        theme-placeholder (or (:placeholder-class attrs)
                              (get-theme-class :input :placeholder))
        input-sizes
        {:xs {:class (str "block w-full rounded-sm border-0 py-1 px-1.5 text-xs "
                          theme-text " shadow-sm ring-1 ring-inset "
                          theme-border " " theme-placeholder
                          " focus:ring-2 focus:ring-inset "
                          theme-focus " " theme-bg " sm:leading-6")}
         :s {:class (str "block w-full rounded-sm border-0 py-1.5 px-2 text-sm "
                         theme-text " shadow-sm ring-1 ring-inset "
                         theme-border " " theme-placeholder
                         " focus:ring-2 focus:ring-inset "
                         theme-focus " " theme-bg " sm:leading-6")}
         :md {:class (str "block w-full rounded-md border-0 py-1.5 px-2.5 text-base "
                          theme-text " shadow-sm ring-1 ring-inset "
                          theme-border " " theme-placeholder
                          " focus:ring-2 focus:ring-inset "
                          theme-focus " " theme-bg " sm:leading-6")}
         :lg {:class (str "block w-full rounded-md border-0 py-2 px-3 text-lg "
                          theme-text " shadow-sm ring-1 ring-inset "
                          theme-border " " theme-placeholder
                          " focus:ring-2 focus:ring-inset "
                          theme-focus " " theme-bg " sm:leading-6")}
         :xl {:class (str "block w-full rounded-md border-0 py-2.5 px-3.5 text-xl "
                          theme-text " shadow-sm ring-1 ring-inset "
                          theme-border " " theme-placeholder
                          " focus:ring-2 focus:ring-inset "
                          theme-focus " " theme-bg " sm:leading-6")}}
        style (get input-sizes size)
        base-attrs {:type input-type
                    :class (:class style)
                    :placeholder placeholder
                    :value value
                    :name name}
        filtered-attrs (dissoc attrs
                               :size :type :placeholder :value :name
                               :on-change  :border-class :focus-class
                               :bg-class :text-class :placeholder-class)
        merged-attrs (merge-attrs base-attrs filtered-attrs)]
    [:input merged-attrs]))

(defmethod c/resolve-alias ::navbar
  [_ attrs content]
  (let [theme-bg (or (:bg-class attrs) (get-theme-class :navbar :bg))
        theme-text (or (:text-class attrs) (get-theme-class :navbar :text))
        theme-hover (or (:hover-class attrs) (get-theme-class :navbar :hover))
        base-attrs {:id "app-header"
                    :class (str theme-bg
                                " sm:flex sm:justify-between sm:items-center sm:px-4 sm:py-3")
                    :data-signals-navbar-open "false"}
        merged-attrs (merge-attrs base-attrs attrs)
        logo-url (or (:logo-url attrs) "/weave.svg")
        nav-items (partition 2 content)]
    [:header merged-attrs
     [:div.flex.items-center.justify-between.px-4.py-3.sm:p-0
      [:div
       [:img.h-8
        {:src logo-url}]]
      [:div.sm:hidden
       [:button.block.text-gray-500.hover:text-white.focus:text-white.focus:outline-none
        {:data-on-click "$navbarOpen = !$navbarOpen"
         :type "button"}
        [:svg.h-6.w-6.fill-current {:viewBox "0 0 24 24"}
         [:path {:fill-rule "evenodd"
                 :data-attr-d "$navbarOpen ? 'M18.278 16.864a1 1 0 0 1-1.414 1.414l-4.829-4.828-4.828 4.828a1 1 0 0 1-1.414-1.414l4.828-4.829-4.828-4.828a1 1 0 0 1 1.414-1.414l4.829 4.828 4.828-4.828a1 1 0 1 1 1.414 1.414l-4.828 4.829 4.828 4.828z' : 'M4 5h16a1 1 0 0 1 0 2H4a1 1 0 1 1 0-2zm0 6h16a1 1 0 0 1 0 2H4a1 1 0 0 1 0-2zm0 6h16a1 1 0 0 1 0 2H4a1 1 0 0 1 0-2z'"}]]]]]
     [:nav.px-2.pt-2.pb-4.bg-gray-800.sm:flex.sm:p-0
      {:data-class-hidden "!$navbarOpen"
       :class "sm:block"}
      [:div.flex.flex-col.sm:flex-row
       (for [[name handler] nav-items]
         [:a {:class (str theme-text " " theme-hover " rounded-md px-3 py-2 text-sm font-medium cursor-pointer block mb-1 sm:mb-0 sm:inline-block")
              :data-on-click handler}
          name])]]]))
