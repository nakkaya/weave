(ns weave.components
  (:require
   [dev.onionpancakes.chassis.core :as c]
   [camel-snake-kebab.core :as csk]
   [clojure.string :as str]
   [clojure.java.io :as io]
   [weave.core :as core]
   [weave.squint :refer [clj->js]]))

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

(defn- merge-classes
  "Merge base class with custom class if provided."
  [base-class custom-class]
  (if (and custom-class (not (str/blank? custom-class)))
    (tw base-class custom-class)
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

(def ^:dynamic *theme*
  {:view {:bg "bg-white dark:bg-gray-900"}
   :card {:bg "bg-white dark:bg-gray-800"
          :border "border border-gray-300 dark:border-gray-700"
          :shadow "shadow-sm"}
   :card-with-header {:bg "bg-white dark:bg-gray-800"
                      :border "divide-y divide-gray-300 dark:divide-gray-700"
                      :shadow "shadow-sm"}
   :link {:base "text-indigo-600 hover:text-indigo-500 dark:text-indigo-400 dark:hover:text-indigo-300"}
   :sidebar {:bg "bg-gray-800 dark:bg-gray-900"
             :text "text-gray-300 dark:text-gray-300"
             :hover "hover:bg-gray-700 hover:text-white dark:hover:bg-gray-800 dark:hover:text-white"
             :active "bg-gray-900 text-white dark:bg-gray-800 dark:text-white"}
   :button {:base "inline-flex items-center justify-center text-center gap-2 rounded-lg shadow-theme-xs transition"
            :sizes {:xs "px-2 py-1.5 text-xs"
                    :s "px-3 py-2 text-sm"
                    :md "px-4 py-3 text-sm"
                    :lg "px-5 py-3.5 text-base"
                    :xl "px-6 py-4 text-lg"}
            :variants {:primary {:bg "bg-indigo-600 dark:bg-indigo-500"
                                 :hover "hover:bg-indigo-500 dark:hover:bg-indigo-400"
                                 :focus "focus:outline-none"
                                 :text "text-white font-medium"}
                       :danger {:bg "bg-red-600 dark:bg-red-500"
                                :hover "hover:bg-red-500 dark:hover:bg-red-400"
                                :focus "focus:outline-none"
                                :text "text-white font-medium"}
                       :secondary {:bg "bg-white dark:bg-gray-800"
                                   :hover "hover:bg-gray-50 dark:hover:bg-white/[0.03]"
                                   :focus "focus:outline-none"
                                   :text "text-gray-700 dark:text-gray-400 font-medium ring-1 ring-inset ring-gray-300 dark:ring-gray-700"}}}
   :input {:base "block w-full h-11 rounded-lg border bg-transparent shadow-sm focus:outline-hidden focus:ring-3"
           :sizes {:xs "px-3 py-2 text-xs"
                   :s "px-3.5 py-2 text-sm"
                   :md "px-4 py-2.5 text-sm"
                   :lg "px-4 py-3 text-base"
                   :xl "px-5 py-3.5 text-lg"}
           :border "border-gray-300 dark:border-gray-700 focus:border-indigo-300 dark:focus:border-indigo-800"
           :focus "focus:ring-indigo-500/10"
           :bg "bg-transparent dark:bg-gray-900"
           :text "text-gray-800 dark:text-white/90"
           :placeholder "placeholder:text-gray-400 dark:placeholder:text-white/30"}
   :label {:base "mb-1.5 block font-medium"
           :sizes {:xs "text-xs"
                   :s "text-sm"
                   :md "text-sm"
                   :lg "text-base"
                   :xl "text-lg"}
           :text "text-gray-700 dark:text-gray-400"
           :required "text-red-500 dark:text-red-400"}
   :alert {:base "rounded-md p-4 border"
           :variants {:success {:bg "bg-green-50 dark:bg-green-900/30"
                                :border "border-green-400 dark:border-green-700"
                                :text "text-green-800 dark:text-green-300"}
                      :warning {:bg "bg-yellow-50 dark:bg-yellow-900/30"
                                :border "border-yellow-400 dark:border-yellow-700"
                                :text "text-yellow-800 dark:text-yellow-300"}
                      :error {:bg "bg-red-50 dark:bg-red-900/30"
                              :border "border-red-400 dark:border-red-700"
                              :text "text-red-800 dark:text-red-300"}
                      :info {:bg "bg-blue-50 dark:bg-blue-900/30"
                             :border "border-blue-400 dark:border-blue-700"
                             :text "text-blue-800 dark:text-blue-300"}}}
   :navbar {:bg "bg-gray-800 dark:bg-gray-900"
            :text "text-gray-300 dark:text-gray-300"
            :hover "hover:bg-gray-700 hover:text-white dark:hover:bg-gray-800 dark:hover:text-white"}
   :select {:base "block w-full h-11 rounded-lg border bg-transparent shadow-sm focus:outline-hidden focus:ring-3 appearance-none"
            :sizes {:xs "px-3 py-2 text-xs"
                    :s "px-3.5 py-2 text-sm"
                    :md "px-4 py-2.5 text-sm"
                    :lg "px-4 py-3 text-base"
                    :xl "px-5 py-3.5 text-lg"}
            :border "border-gray-300 dark:border-gray-700 focus:border-indigo-300 dark:focus:border-indigo-800"
            :focus "focus:ring-indigo-500/10"
            :bg "bg-transparent dark:bg-gray-900"
            :text "text-gray-800 dark:text-white/90"
            :icon "absolute inset-y-0 right-0 flex items-center pr-2 pointer-events-none"}
   :modal {:overlay "fixed inset-0 z-50 bg-black/50 dark:bg-black/70 transition-opacity"
           :container "fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6"
           :dialog "relative bg-white dark:bg-gray-800 rounded-xl shadow-2xl max-w-md w-full max-h-[90vh] overflow-y-auto"
           :sizes {:sm "max-w-sm"
                   :md "max-w-md"
                   :lg "max-w-lg"
                   :xl "max-w-xl"
                   :2xl "max-w-2xl"
                   :full "max-w-full mx-4"}}
   :table {:container "w-full overflow-x-auto shadow ring-1 ring-black ring-opacity-5 md:rounded-lg dark:ring-gray-700 dark:ring-opacity-50"
           :base "min-w-full divide-y divide-gray-300 dark:divide-gray-600"
           :header {:bg "bg-gray-50 dark:bg-gray-800"
                    :text "text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider"
                    :padding "px-6 py-3"}
           :body {:bg "bg-white dark:bg-gray-900"
                  :divider "divide-y divide-gray-200 dark:divide-gray-700"}
           :row {:hover "hover:bg-gray-50 dark:hover:bg-gray-800"
                 :even "bg-white dark:bg-gray-900"
                 :odd "bg-gray-50 dark:bg-gray-800"}
           :cell {:text "text-sm text-gray-900 dark:text-gray-100"
                  :padding "px-6 py-4 whitespace-nowrap"}}})

#_:clj-kondo/ignore
(defn with-theme
  "Execute body with a custom theme configuration"
  [custom-theme & body]
  (binding [*theme* (merge-with merge *theme* custom-theme)]
    (do body)))

(defn- get-theme-class
  "Get a class string from the theme configuration"
  [component-type & path]
  (let [path-vec (into [component-type] path)
        class-str (get-in *theme* path-vec)]
    class-str))

(defn- get-size-class
  "Get the size-specific class for a component"
  [component-type size]
  (get-in *theme* [component-type :sizes size]))

(defn- get-variant-classes
  "Get all classes for a component variant"
  [component-type variant]
  (get-in *theme* [component-type :variants variant]))

(def ^:private load-heroicons-sprite
  (memoize
   (fn []
     (try
       (slurp (io/resource "public/heroicons-sprite.svg"))
       (catch Exception e
         (println "Error loading heroicons sprite:" (.getMessage e))
         nil)))))

(defn- get-icon-svg
  "Returns the SVG string for the specified icon from heroicons-sprite.svg"
  [icon-id]
  (let [sprite (load-heroicons-sprite)
        pattern (re-pattern (str "(?s)<symbol\\s+id=\"" icon-id "\".*?</symbol>"))]
    (when sprite
      (when-let [match (re-find pattern sprite)]
        (-> match
            (str/replace #"<symbol\s+id=\"([^\"]+)\"" "<svg")
            (str/replace #"</symbol>" "</svg>"))))))

(defmethod c/resolve-alias ::icon
  [_ attrs _content]
  (let [icon-id (or (:id attrs) "")
        icon-class (or (:class attrs) "")
        size (or (:size attrs) 24)
        size-class (str "h-" size " w-" size)
        svg (get-icon-svg icon-id)
        final-class (tw size-class icon-class)]

    (when svg
      (-> svg
          (str/replace
           #"<svg"
           (str "<svg class=\"" final-class "\""))
          c/raw))))

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
  (let [size (or (:size attrs) :md)
        text (or (first content) (:title attrs))
        variant (or (:variant attrs) :primary)

        ;; Get theme classes
        variant-classes (get-variant-classes :button variant)
        size-class (get-size-class :button size)
        base-class (get-theme-class :button :base)

        ;; Build class using tw function
        btn-class (tw
                   base-class
                   size-class
                   (or (:bg-class attrs) (:bg variant-classes))
                   (or (:text-class attrs) (:text variant-classes))
                   (or (:hover-class attrs) (:hover variant-classes))
                   (or (:focus-class attrs) (:focus variant-classes)))

        ;; Prepare attributes
        base-attrs {:type (or (:type attrs) "button")
                    :class btn-class}
        filtered-attrs (dissoc attrs
                               :size :title :variant :type
                               :bg-class :hover-class :focus-class :text-class)
        merged-attrs (merge-attrs base-attrs filtered-attrs)]

    [:button merged-attrs text]))

(defmethod c/resolve-alias ::input
  [_ attrs _content]
  (let [input-type (or (:type attrs) "text")
        size (or (:size attrs) :md)
        placeholder (or (:placeholder attrs) "")
        value (or (:value attrs) "")
        name (or (:name attrs) "")

        ;; Get theme classes
        base-class (get-theme-class :input :base)
        size-class (get-size-class :input size)
        theme-border (or (:border-class attrs) (get-theme-class :input :border))
        theme-focus (or (:focus-class attrs) (get-theme-class :input :focus))
        theme-bg (or (:bg-class attrs) (get-theme-class :input :bg))
        theme-text (or (:text-class attrs) (get-theme-class :input :text))
        theme-placeholder (or (:placeholder-class attrs) (get-theme-class :input :placeholder))

        ;; Build class using tw function
        input-class (tw
                     base-class
                     size-class
                     theme-text
                     theme-border
                     theme-placeholder
                     theme-focus
                     theme-bg)

        ;; Prepare attributes
        base-attrs {:type input-type
                    :class input-class
                    :placeholder placeholder
                    :value value
                    :name name}
        filtered-attrs (dissoc attrs
                               :size :type :placeholder :value :name
                               :on-change :border-class :focus-class
                               :bg-class :text-class :placeholder-class)
        merged-attrs (merge-attrs base-attrs filtered-attrs)]

    [:input merged-attrs]))

(defmethod c/resolve-alias ::label
  [_ attrs content]
  (let [size (or (:size attrs) :md)
        required? (:required? attrs)
        for-id (:for attrs)

        ;; Get theme classes
        base-class (get-theme-class :label :base)
        size-class (get-size-class :label size)
        theme-text (or (:text-class attrs) (get-theme-class :label :text))
        theme-required (or (:required-class attrs) (get-theme-class :label :required))

        ;; Build class using tw function
        label-class (tw base-class size-class theme-text)

        ;; Prepare attributes
        base-attrs {:class label-class}
        base-attrs (if for-id (assoc base-attrs :for for-id) base-attrs)
        filtered-attrs (dissoc attrs :size :required? :for :text-class :required-class)
        merged-attrs (merge-attrs base-attrs filtered-attrs)]

    [:label merged-attrs
     content
     (when required?
       [:span {:class theme-required} " *"])]))

(defmethod c/resolve-alias ::alert
  [_ attrs content]
  (let [alert-type (or (:type attrs) :info)

        ;; Get theme classes
        base-class (get-theme-class :alert :base)
        variant-classes (get-in *theme* [:alert :variants alert-type])

        ;; Alert icons using the icon component
        alert-icons
        {:success [::icon#solid-check-circle
                   {:class "h-5 w-5 text-green-400 dark:text-green-500"}]
         :warning [::icon#solid-exclamation-triangle
                   {:class "h-5 w-5 text-yellow-400 dark:text-yellow-500"}]
         :error [::icon#solid-x-circle
                 {:class "h-5 w-5 text-red-400 dark:text-red-500"}]
         :info [::icon#solid-information-circle
                {:class "h-5 w-5 text-blue-400 dark:text-blue-500"}]}

        ;; Build class using tw function
        alert-class (tw
                     base-class
                     (or (:bg-class attrs) (:bg variant-classes))
                     (or (:border-class attrs) (:border variant-classes))
                     (or (:text-class attrs) (:text variant-classes)))

        dismissible? (:dismissible? attrs)
        base-attrs {:class alert-class}
        filtered-attrs (dissoc attrs :type :dismissible? :bg-class :border-class :text-class)
        merged-attrs (merge-attrs base-attrs filtered-attrs)]

    [:div merged-attrs
     [:div.flex.relative.items-center
      [:div.flex-shrink-0
       (get alert-icons alert-type)]
      [:div.ml-3.flex-1
       [:div.text-sm
        content]]
      (when dismissible?
        [:div.ml-auto.pl-3
         [:button.text-gray-400.hover:text-gray-500.focus:outline-none
          {:type "button"
           :onclick "this.closest('[class*=\"rounded-md p-4 border\"]').remove()"}
          [:span.sr-only "Dismiss"]
          [::icon#solid-x-mark
           {:class "h-5 w-5"}]]])]]))

(defmethod c/resolve-alias ::navbar
  [_ attrs content]
  (let [theme-bg (or (:bg-class attrs) (get-theme-class :navbar :bg))
        theme-text (or (:text-class attrs) (get-theme-class :navbar :text))
        theme-hover (or (:hover-class attrs) (get-theme-class :navbar :hover))
        logo-url (or (:logo-url attrs) "/weave.svg")
        title (or (:title attrs) nil)
        base-attrs {:id "app-header"
                    :class (tw theme-bg
                               "sm:flex sm:justify-between sm:items-center sm:px-4 sm:py-3")
                    :data-signals-navbar-open "false"}
        merged-attrs (merge-attrs base-attrs attrs)]

    [:header merged-attrs
     ;; Navbar header with logo and mobile toggle
     [:div.flex.items-center.justify-between.px-4.py-3.sm:p-0
      [:div.flex.items-center.gap-3
       [:img.h-8.w-auto
        {:src logo-url}]
       (when title
         [:div
          {:class "font-medium text-lg text-gray-300 dark:text-gray-300"}
          title])]

      ;; Mobile menu toggle button
      [:div.sm:hidden
       [:button.block.text-gray-300.hover:text-white.focus:text-white.focus:outline-none
        {:data-on-click "$navbarOpen = !$navbarOpen"
         :type "button"}
        [:div
         [:div {:data-if "$navbarOpen"}
          [::icon#solid-x-mark {:class "h-6 w-6"}]]
         [:div {:data-if "!$navbarOpen"}
          [::icon#solid-bars-3 {:class "h-6 w-6"}]]]]]]

     ;; Navbar content/links
     [:nav.px-2.pt-2.pb-4.sm:flex.sm:p-0
      {:data-class-hidden "!$navbarOpen"
       :class (tw theme-bg "sm:block")}
      [:div.flex.flex-col.sm:flex-row
       content]]]))

(defmethod c/resolve-alias ::select
  [_ attrs _content]
  (let [size (or (:size attrs) :md)
        placeholder (or (:placeholder attrs) "Select an option")
        value (or (:selected attrs) "")
        name (or (:name attrs) "")
        id (or (:id attrs) name)
        options (or (:options attrs) [])

        base-class (get-theme-class :select :base)
        size-class (get-size-class :select size)
        theme-border (or (:border-class attrs) (get-theme-class :select :border))
        theme-focus (or (:focus-class attrs) (get-theme-class :select :focus))
        theme-bg (or (:bg-class attrs) (get-theme-class :select :bg))
        theme-text (or (:text-class attrs) (get-theme-class :select :text))
        theme-icon (or (:icon-class attrs) (get-theme-class :select :icon))

        select-class (tw
                      base-class
                      size-class
                      theme-text
                      theme-border
                      theme-focus
                      theme-bg)

        base-attrs {:class select-class
                    :name name
                    :id id}
        filtered-attrs (dissoc attrs
                               :size :placeholder :selected :name :id :options
                               :border-class :focus-class :bg-class :text-class
                               :icon-class)
        merged-attrs (merge-attrs base-attrs filtered-attrs)]

    [:div.relative
     [:select merged-attrs
      (when placeholder
        [:option {:value ""} placeholder])
      (for [option options]
        [:option {:value (:value option)
                  :selected (= value (:value option))}
         (:label option)])]
     [:div {:class theme-icon}
      [::icon#solid-chevron-up-down
       {:class "h-5 w-5 text-gray-400"}]]]))

(defmethod c/resolve-alias ::a
  [_ attrs content]
  (let [base-class (get-theme-class :link :base)
        base-attrs {:class base-class}
        merged-attrs (merge-attrs base-attrs attrs)]
    [:a merged-attrs (first content)]))

(defmethod c/resolve-alias ::sidebar
  [_ attrs content]
  (let [theme-bg (or (:bg-class attrs) (get-theme-class :sidebar :bg))
        logo-url (or (:logo-url attrs) "/weave.svg")
        title (or (:title attrs) nil)]

    [:div.flex.h-screen
     ;; Sidebar backdrop for mobile
     [:div#sidebar-backdrop.fixed.inset-0.bg-gray-800.bg-opacity-75.z-20.hidden.lg:hidden
      {:onclick (clj->js
                 (let [sidebar (js/document.getElementById "sidebar")
                       backdrop (js/document.getElementById "sidebar-backdrop")
                       content (js/document.getElementById "sidebar-content")]
                   (.add (.-classList sidebar) "-translate-x-full")
                   (.add (.-classList backdrop) "hidden")
                   ;; On mobile, add ml-0 to override the margin
                   (when content (.add (.-classList content) "ml-0"))))}]

     [:aside#sidebar
      {:class (tw theme-bg
                  "fixed inset-y-0 left-0 z-30 w-64"
                  "transform -translate-x-full lg:translate-x-0"
                  "transition-transform duration-300 ease-in-out")}

      ;; Header
      [:div.flex.items-center.justify-between.px-4.py-3.border-b.border-gray-700
       [:div.flex.items-center.gap-3
        [:img.h-8.w-auto
         {:src logo-url}]
        (when title
          [:div
           {:class "font-medium text-lg text-gray-300 dark:text-gray-300"}
           title])]]

      [:nav
       {:class "flex-1 px-2 py-4 overflow-y-auto flex flex-col h-[calc(100%-4rem)]"}
       content]]

     ;; Toggle button for mobile
     [:div.fixed.bottom-4.left-4.lg:hidden.z-30
      [:button.p-2.rounded-full.bg-gray-800.text-white.shadow-lg
       {:onclick (clj->js
                  (let [sidebar (js/document.getElementById "sidebar")
                        backdrop (js/document.getElementById "sidebar-backdrop")
                        content (js/document.getElementById "sidebar-content")]
                    (if (.contains (.-classList sidebar) "-translate-x-full")
                      (do
                        (.remove (.-classList sidebar) "-translate-x-full")
                        (.remove (.-classList backdrop) "hidden")
                        ;; On mobile, remove the ml-0 override to show the margin
                        (when content (.remove (.-classList content) "ml-0")))
                      (do
                        (.add (.-classList sidebar) "-translate-x-full")
                        (.add (.-classList backdrop) "hidden")
                        ;; On mobile, add ml-0 to override the margin
                        (when content (.add (.-classList content) "ml-0"))))))}
       [::icon#solid-bars-3 {:class "h-6 w-6"}]]]]))

(defmethod c/resolve-alias ::sidebar-group
  [_ attrs content]
  (if (contains? attrs :collapsed)
    ;; Collapsible version when :collapsed attribute is present
    (let [collapsed? (:collapsed attrs)
          group-id (or (:id attrs) (str "sidebar-group-" (gensym)))]
      [:div.mb-6
       ;; Hidden checkbox that controls the state
       [:input {:type "checkbox"
                :id group-id
                :class "peer hidden"
                :checked (not collapsed?)}]

       (when-let [title (:title attrs)]
         [:label.px-3.mb-2.text-xs.font-semibold.text-gray-400.uppercase.cursor-pointer.flex.items-center.justify-between.select-none
          {:for group-id}
          title
          ;; Chevron icon that rotates based on state
          [:svg.w-4.h-4.transition-transform.peer-checked:rotate-180
           {:fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
           [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
                   :d "M19 9l-7 7-7-7"}]]])

       ;; Content that shows/hides based on checkbox state
       [:ul.space-y-1.overflow-hidden.transition-all.duration-300.ease-in-out
        {:class (if collapsed?
                  "peer-checked:max-h-0 max-h-96"
                  "peer-checked:max-h-96 max-h-0")}
        content]])

    ;; Static version when :collapsed attribute is not present (original behavior)
    [:div.mb-6
     (when-let [title (:title attrs)]
       [:h3.px-3.mb-2.text-xs.font-semibold.text-gray-400.uppercase
        title])
     [:ul.space-y-1
      content]]))

(defmethod c/resolve-alias ::sidebar-item
  [_ attrs content]
  (let [theme-text (or (:text-class attrs) (get-theme-class :sidebar :text))
        theme-hover (or (:hover-class attrs) (get-theme-class :sidebar :hover))
        theme-active (or (:active-class attrs) (get-theme-class :sidebar :active))
        active? (get attrs :active false)
        icon (get attrs :icon nil)
        handler (get attrs :handler nil)
        item-class (tw
                    theme-text
                    (if active? theme-active theme-hover)
                    "flex items-center px-3 py-2 rounded-md text-sm font-medium cursor-pointer")]

    [:li
     [:a {:class item-class
          :data-on-click handler}
      (when icon
        [::icon {:id icon :class "h-5 w-5 mr-2"}])
      (or content (:label attrs))]]))

(defmethod c/resolve-alias ::sidebar-layout
  [_ attrs content]
  (let [sidebar-element (first content)
        content-element (second content)]
    [:div.flex.h-screen
     sidebar-element
     [:div#sidebar-content
      {:class "flex-1 transition-all duration-300 ease-in-out ml-0 lg:ml-64 overflow-auto"}
      content-element]]))

(defmethod c/resolve-alias ::tabs
  [_ attrs content]
  [:div attrs
   ;; Mobile vertical tabs (hidden on larger screens)
   [:div.sm:hidden
    [:nav {:aria-label "Tabs"
           :class "flex flex-col space-y-1"}
     content]]

   ;; Desktop horizontal tabs (hidden on mobile)
   [:div.hidden.sm:block
    [:div.border-b.border-gray-200
     [:nav {:aria-label "Tabs"
            :class "-mb-px flex space-x-8"}
      content]]]])

(defmethod c/resolve-alias ::tab-item
  [_ attrs content]
  (let [active? (get attrs :active false)
        icon (get attrs :icon nil)
        handler (get attrs :handler nil)
        label (or content (:label attrs))
        base-classes "group inline-flex items-center text-sm font-medium"
        desktop-classes "sm:border-b-2 sm:border-l-0 sm:px-1 sm:py-4"
        mobile-classes "border-l-2 border-b-0 px-4 py-3 w-full sm:w-auto"
        active-classes (if active?
                         "border-indigo-500 text-indigo-600"
                         "border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700")
        icon-classes (if active?
                       "text-indigo-500"
                       "text-gray-400 group-hover:text-gray-500")]
    [:a {:class (tw base-classes desktop-classes mobile-classes active-classes "cursor-pointer")
         :aria-current (when active? "page")
         :data-on-click handler}
     (when icon
       [::icon {:id icon
                :size 5
                :class (tw "mr-2 -ml-0.5" icon-classes)}])
     [:span label]]))

(defmethod c/resolve-alias ::navbar-item
  [_ attrs content]
  (let [theme-text (or (:text-class attrs) (get-theme-class :navbar :text))
        theme-hover (or (:hover-class attrs) (get-theme-class :navbar :hover))
        active? (get attrs :active false)
        theme-active (or (:active-class attrs) (get-theme-class :navbar :active))
        icon (get attrs :icon nil)
        handler (get attrs :handler nil)
        item-class (tw
                    theme-text
                    (if active? theme-active theme-hover)
                    "rounded-md px-3 py-2 text-sm font-medium cursor-pointer block mb-1 sm:mb-0 sm:inline-block")]

    [:a {:class item-class
         :data-on-click handler}
     (when icon
       [::icon {:id icon :class "h-5 w-5 mr-2 inline-block"}])
     (or content (:label attrs))]))

(defmethod c/resolve-alias ::sign-in
  [_ attrs content]
  (let [logo-url (or (:logo-url attrs) "/weave.svg")
        logo-alt (or (:logo-alt attrs) "Your Company")
        title (or (:title attrs) "Sign in to your account")
        username-label (or (:username-label attrs) "Email")
        username-placeholder (or (:username-placeholder attrs) "Enter your email")
        password-label (or (:password-label attrs) "Password")
        password-placeholder (or (:password-placeholder attrs) "Enter your password")
        submit-text (or (:submit-text attrs) "Sign In")
        forgot-password-text (or (:forgot-password-text attrs) "Forgot your password?")
        forgot-password-url (or (:forgot-password-url attrs) "/#/forgot-password")
        register-text (or (:register-text attrs) "Don't have an account? Sign up")
        register-url (or (:register-url attrs) "/#/register")
        error-message (or (:error-message attrs) "Invalid username or password.")
        error-signal (or (:error-signal attrs) "$_error")
        on-submit (or (:on-submit attrs) (core/handler []))

        ;; Container classes
        container-classes (get-theme-class :view :bg)
        card-container-classes "mx-auto w-full sm:max-w-lg"
        heading-classes "mt-10 text-center text-2xl/9 font-bold tracking-tight text-gray-900"
        form-container-classes "mt-10 mx-auto w-full sm:max-w-lg"
        form-classes "space-y-6"
        footer-text-classes "mt-10 text-center text-sm/6 text-gray-500"
        link-classes "font-semibold text-indigo-600 hover:text-indigo-500 cursor-pointer"

        ;; Prepare base attributes
        base-attrs {:class (tw "flex min-h-full flex-col w-full md:w-96 justify-center px-6 py-6 lg:px-8" container-classes)}
        merged-attrs (merge-attrs
                      base-attrs (dissoc attrs
                                         :logo-url :logo-alt :title
                                         :username-label :username-placeholder
                                         :password-label :password-placeholder
                                         :submit-text :forgot-password-text
                                         :forgot-password-url
                                         :register-text :register-handler
                                         :error-message :error-signal
                                         :on-submit))]

    [::center-hv
     [:div merged-attrs
      [:div {:class card-container-classes}
       [:img {:class "mx-auto h-10 w-auto"
              :src logo-url
              :alt logo-alt}]
       [:h2 {:class heading-classes}
        title]]

      [:div {:class form-container-classes
             :data-signals-_error "false"}
       [:div {:id "sign-in-error"
              :class "p-3 mb-4 text-sm text-red-700 bg-red-100 rounded-lg"
              :data-show error-signal
              :style "display: none"}
        error-message]

       [:form {:class form-classes
               :data-on-submit on-submit}

        [:div
         [::label {:for "username"}
          username-label]
         [:div {:class "mt-2"}
          [::input
           {:name "username"
            :id "username"
            :autocomplete "username"
            :placeholder username-placeholder
            :required true}]]]

        [:div
         [:div {:class "flex flex-col sm:flex-row sm:items-baseline sm:justify-between gap-2"}
          [::label {:for "password"}
           password-label]
          [:div {:class "text-sm"}
           [:a {:href forgot-password-url :class link-classes}
            forgot-password-text]]]
         [:div {:class "mt-2"}
          [::input
           {:name "password"
            :id "password"
            :type "password"
            :autocomplete "current-password"
            :placeholder password-placeholder
            :required true}]]]

        [:div
         [::button {:class "w-full"
                    :type "submit"
                    :size :s
                    :variant :primary}
          submit-text]]]

       [:p {:class footer-text-classes}
        register-text
        [:a {:class link-classes
             :href register-url}
         " Sign up"]]

       content]]]))

(defmethod c/resolve-alias ::modal
  [_ attrs content]
  (let [size (or (:size attrs) :md)
        signal (or (csk/->camelCase (:id attrs)) "modal")
        overlay-class (get-theme-class :modal :overlay)
        container-class (get-theme-class :modal :container)
        dialog-class (get-theme-class :modal :dialog)
        size-class (get-size-class :modal size)
        dialog-class-with-size (tw dialog-class size-class)
        base-attrs {:id (or (:id attrs) "modal")
                    :data-show (str "$" signal)}
        filtered-attrs (dissoc attrs :size :id)
        merged-attrs (merge-attrs base-attrs filtered-attrs)]
    [:div
     {(-> (str "data-signals-" signal) keyword) "false"}
     [:div merged-attrs
      [:div {:class overlay-class}]
      [:div {:class container-class}
       [:div {:class dialog-class-with-size}
        content]]]]))

;;    Example:
;;    [::table {:columns [{:name "name" :label "Name" :align :left}
;;                        {:name "age" :label "Age" :align :right}]
;;              :rows [{:name "Alice" :age 30}
;;                     {:name "Bob" :age 25}]}]
(defmethod c/resolve-alias ::table
  [_ attrs _content]
  (let [{:keys [id columns rows class class-table class-header class-row]} attrs

        ;; Get theme classes
        container-class (or (:container-class attrs) (get-theme-class :table :container))
        table-class (merge-classes (or (:base-class attrs) (get-theme-class :table :base)) (or class-table class))
        header-bg (or (:header-bg-class attrs) (get-theme-class :table :header :bg))
        header-text (or (:header-text-class attrs) (get-theme-class :table :header :text))
        header-padding (or (:header-padding-class attrs) (get-theme-class :table :header :padding))
        body-bg (or (:body-bg-class attrs) (get-theme-class :table :body :bg))
        body-divider (or (:body-divider-class attrs) (get-theme-class :table :body :divider))
        row-hover (or (:row-hover-class attrs) (get-theme-class :table :row :hover))
        row-even (or (:row-even-class attrs) (get-theme-class :table :row :even))
        row-odd (or (:row-odd-class attrs) (get-theme-class :table :row :odd))
        cell-text (or (:cell-text-class attrs) (get-theme-class :table :cell :text))
        cell-padding (or (:cell-padding-class attrs) (get-theme-class :table :cell :padding))

        filtered-attrs (dissoc attrs :container-class :base-class :header-bg-class
                               :header-text-class :header-padding-class :body-bg-class
                               :body-divider-class :row-hover-class :row-even-class
                               :row-odd-class :cell-text-class :cell-padding-class
                               :class :class-table :class-header :class-row)]

    [:div {:id id :class container-class}
     [:table (merge-attrs {:class table-class} filtered-attrs)
      ;; Header
      [:thead {:class (tw header-bg class-header)}
       [:tr
        (for [column columns]
          [:th
           {:key (:name column)
            :class (tw header-padding header-text
                       (case (:align column)
                         :right "text-right"
                         :left "text-left"
                         "text-left"))}
           (:label column)])]]

      ;; Body
      [:tbody {:class (tw body-divider body-bg)}
       (for [[index row] (map-indexed vector rows)]
         [:tr
          {:class (tw row-hover
                      (if (even? index) row-even row-odd)
                      class-row)}
          (for [column columns]
            [:td
             {:key (:name column)
              :class (tw cell-padding cell-text
                         (case (:align column)
                           :right "text-right"
                           :left "text-left"
                           "text-left"))}
             (get row (keyword (:name column)) "-")])])]]]))
