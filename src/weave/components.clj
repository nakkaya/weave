(ns weave.components
  (:require
   [weave.core :as core]))

#_:clj-kondo/ignore
(defmacro navbar [& items]
  `((fn toggle# [& [is-open#]]
      (let [is-open# (or is-open# (atom false))]
        [:header#app-header.bg-gray-800.sm:flex.sm:justify-between.sm:items-center.sm:px-4.sm:py-3
         [:div.flex.items-center.justify-between.px-4.py-3.sm:p-0
          [:div
           [:img.h-8
            {:src "https://tailwindcss.com/plus-assets/img/logos/mark.svg?color=indigo&shade=600"}]]
          [:div.sm:hidden
           [:button.block.text-gray-500.hover:text-white.focus:text-white.focus:outline-none
            {:data-on-click
             (core/handler
              (swap! is-open# not)
              (core/push-html! (toggle# is-open#)))
             :type "button"}
            [:svg.h-6.w-6.fill-current {:viewBox "0 0 24 24"}
             (if @is-open#
               [:path {:fill-rule "evenodd"
                       :d "M18.278 16.864a1 1 0 0 1-1.414 1.414l-4.829-4.828-4.828 4.828a1 1 0 0 1-1.414-1.414l4.828-4.829-4.828-4.828a1 1 0 0 1 1.414-1.414l4.829 4.828 4.828-4.828a1 1 0 1 1 1.414 1.414l-4.828 4.829 4.828 4.828z"}]
               [:path {:fill-rule "evenodd"
                       :d "M4 5h16a1 1 0 0 1 0 2H4a1 1 0 1 1 0-2zm0 6h16a1 1 0 0 1 0 2H4a1 1 0 0 1 0-2zm0 6h16a1 1 0 0 1 0 2H4a1 1 0 0 1 0-2z"}])]]]]
         [:nav.px-2.pt-2.pb-4.sm:flex.sm:p-0 {:class (if @is-open# "block" "hidden")}
          [:div.flex.flex-col.sm:flex-row
           ~@(for [[name# handler#] (partition 2 items)]
               `[:a.text-gray-300.hover:bg-gray-700.hover:text-white.rounded-md.px-3.py-2.text-sm.font-medium.cursor-pointer.block.mb-1.sm:mb-0.sm:inline-block
                 {:data-on-click ~handler#}
                 ~name#])]]]))))

#_:clj-kondo/ignore
(defmacro button
  [options handler]
  (let [size (:size options)
        text (:title options)
        custom-class (:class options)
        button-type (or (:type options) :primary)
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
         :md {:class (str "rounded-md " (:bg type-style) " px-2.5 py-1.5 text-sm font-semibold " (:text type-style) " shadow-xs " (:hover type-style) " focus-visible:outline-2 focus-visible:outline-offset-2 " (:focus type-style))}
         :lg {:class (str "rounded-md " (:bg type-style) " px-3 py-2 text-sm font-semibold " (:text type-style) " shadow-xs " (:hover type-style) " focus-visible:outline-2 focus-visible:outline-offset-2 " (:focus type-style))}
         :xl {:class (str "rounded-md " (:bg type-style) " px-3.5 py-2.5 text-sm font-semibold " (:text type-style) " shadow-xs " (:hover type-style) " focus-visible:outline-2 focus-visible:outline-offset-2 " (:focus type-style))}}
        style (get button-sizes size)
        class-str (if custom-class
                    (str (:class style) " " custom-class)
                    (:class style))]
    `[:button
      {:type "button"
       :class ~class-str
       :data-on-click ~handler}
      ~text]))

#_:clj-kondo/ignore
(defmacro flex-between
  [options & children]
  (let [custom-class (:class options)
        base-class "flex justify-between items-center"
        class-str (if custom-class
                    (str base-class " " custom-class)
                    base-class)]
    `[:div
      {:class ~class-str}
      ~@children]))

#_:clj-kondo/ignore
(defmacro card-with-header
  [options header content]
  (let [custom-class (:class options)
        base-class "divide-y divide-gray-200 overflow-hidden rounded-lg bg-white shadow-sm"
        class-str (if custom-class
                    (str base-class " " custom-class)
                    base-class)]
    `[:div
      {:class ~class-str}
      [:div
       {:class "px-4 py-5 sm:px-6"}
       ~header]
      [:div
       {:class "px-4 py-5 sm:p-6"}
       ~content]]))
