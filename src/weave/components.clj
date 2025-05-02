(ns weave.components
  (:require
   [dev.onionpancakes.chassis.core :as c]
   [clojure.string :as str]))

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

(defmethod c/resolve-alias ::row
  [_ attrs content]
  (let [base-attrs {:class "flex flex-row"}
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
  (let [base-attrs {:class "overflow-hidden bg-white shadow-sm sm:rounded-lg"}
        merged-attrs (merge-attrs base-attrs attrs)]
    [:div merged-attrs
     [:div {:class "px-4 py-5 sm:p-6"}
      content]]))

(defmethod c/resolve-alias ::card-with-header
  [_ attrs content]
  (let [base-attrs {:class "divide-y divide-gray-200 overflow-hidden rounded-lg bg-white shadow-sm"}
        merged-attrs (merge-attrs base-attrs attrs)
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
        handler (:on-click attrs)
        type-styles {:primary {:bg "bg-indigo-600"
                               :hover "hover:bg-indigo-500"
                               :focus "focus-visible:outline-indigo-600"
                               :text "text-white"}
                     :danger {:bg "bg-red-600"
                              :hover "hover:bg-red-500"
                              :focus "focus-visible:outline-red-600"
                              :text "text-white"}
                     :secondary {:bg "bg-gray-200"
                                 :hover "hover:bg-gray-300"
                                 :focus "focus-visible:outline-gray-600"
                                 :text "text-gray-900"}}
        type-style (get type-styles button-type)
        button-sizes
        {:xs {:class (str "rounded-sm " (:bg type-style) " px-2 py-1 text-xs font-semibold " (:text type-style) " shadow-xs " (:hover type-style) " focus-visible:outline-2 focus-visible:outline-offset-2 " (:focus type-style))}
         :s {:class (str "rounded-sm " (:bg type-style) " px-2 py-1 text-sm font-semibold " (:text type-style) " shadow-xs " (:hover type-style) " focus-visible:outline-2 focus-visible:outline-offset-2 " (:focus type-style))}
         :md {:class (str "rounded-md " (:bg type-style) " px-2.5 py-1.5 text-base font-semibold " (:text type-style) " shadow-xs " (:hover type-style) " focus-visible:outline-2 focus-visible:outline-offset-2 " (:focus type-style))}
         :lg {:class (str "rounded-md " (:bg type-style) " px-3 py-2 text-lg font-semibold " (:text type-style) " shadow-xs " (:hover type-style) " focus-visible:outline-2 focus-visible:outline-offset-2 " (:focus type-style))}
         :xl {:class (str "rounded-md " (:bg type-style) " px-3.5 py-2.5 text-xl font-semibold " (:text type-style) " shadow-xs " (:hover type-style) " focus-visible:outline-2 focus-visible:outline-offset-2 " (:focus type-style))}}
        style (get button-sizes size)
        base-attrs {:type "button"
                    :class (:class style)
                    :data-on-click handler}
        ;; Remove special keys that we've already processed
        filtered-attrs (dissoc attrs :size :title :type :on-click)
        merged-attrs (merge-attrs base-attrs filtered-attrs)]
    [:button merged-attrs text]))

(defmethod c/resolve-alias ::navbar
  [_ attrs content]
  (let [base-attrs {:id "app-header"
                    :class "bg-gray-800 sm:flex sm:justify-between sm:items-center sm:px-4 sm:py-3"
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
         [:a.text-gray-300.hover:bg-gray-700.hover:text-white.rounded-md.px-3.py-2.text-sm.font-medium.cursor-pointer.block.mb-1.sm:mb-0.sm:inline-block
          {:data-on-click handler}
          name])]]]))
