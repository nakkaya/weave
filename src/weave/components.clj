(ns weave.components
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.string :as str]
   [dev.onionpancakes.chassis.core :as c]
   [weave.core :as core]
   [weave.squint :refer [clj->js]])
  (:import
   [java.time
    Instant
    LocalDateTime
    ZoneId
    ZonedDateTime]
   [java.time.format DateTimeFormatter]))

(defn tw
  "Combines multiple Tailwind CSS classes into a single string.
   Filters out nil values and trims whitespace."
  [& classes]
  (str/trim
   (str/join " "
             (remove nil?
                     (map #(if (string? %) (str/trim %) %)
                          classes)))))

(defn- normalize-class [class-val]
  (cond
    (string? class-val) class-val
    (sequential? class-val) (str/join " " class-val)
    :else class-val))

(defn- merge-classes
  "Merge base class with custom class if provided."
  [base-class custom-class]
  (let [custom-class (normalize-class custom-class)]
    (if (and custom-class (not (str/blank? custom-class)))
      (tw base-class custom-class)
      base-class)))

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
  {:view {:bg "bg-[#f7f7f7] dark:bg-[#1a1a1a]"}
   :hr {:border "border-[#e0e0e0] dark:border-[#333333]"
        :light "border-[#f0f0f0] dark:border-[#2a2a2a]"}
   :card {:bg "bg-white dark:bg-[#252525]"
          :border "border border-[#e0e0e0] dark:border-[#333333]"
          :radius "rounded-lg"}
   :card-with-header {:bg "bg-white dark:bg-[#252525]"
                      :border "divide-y divide-[#e0e0e0] dark:divide-[#333333]"
                      :shadow "shadow-sm"
                      :radius "rounded-lg"
                      :ring "ring-1 ring-[#e0e0e0] dark:ring-[#333333]"}
   :code {:bg "bg-[#f5f5f5] dark:bg-[#1a1a1a]"
          :text "text-[#171717] dark:text-[#e5e5e5]"
          :base "font-mono text-sm rounded p-3 overflow-x-auto whitespace-pre-wrap"}
   :stat {:bg "bg-white dark:bg-[#252525]"
          :base "overflow-hidden rounded-lg px-4 py-5 shadow ring-1 ring-inset ring-[#e0e0e0] dark:ring-[#333333] sm:p-6"
          :label "truncate text-sm font-medium text-[#737373] dark:text-[#a0a0a0]"
          :value "mt-1 text-3xl font-semibold tracking-tight text-[#171717] dark:text-[#e5e5e5]"}
   :link {:base "text-[#4f46e5] hover:text-[#4338ca] dark:text-[#5b8ff9] dark:hover:text-[#7ba8ff]"}
   :sidebar {:bg "bg-[#f7f7f7] dark:bg-[#202020]"
             :text "text-[#525252] dark:text-[#d0d0d0]"
             :hover "hover:bg-[#e5e5e5] hover:text-[#171717] dark:hover:bg-[#2a2a2a] dark:hover:text-white"
             :active "bg-[#e0e0e0] text-[#171717] dark:bg-[#2a2a2a] dark:text-white"
             :radius "rounded-md"}
   :button {:base "inline-flex items-center justify-center text-center gap-2 rounded-lg shadow-theme-xs transition"
            :sizes {:xs "px-2 py-1.5 text-xs"
                    :sm "px-3 py-2 text-sm"
                    :md "px-4 py-2.5 text-sm"
                    :lg "px-5 py-3.5 text-base"
                    :xl "px-6 py-4 text-lg"
                    :icon "p-2"}
            :variants {:primary {:bg "bg-[#4f46e5] dark:bg-[#5b8ff9]"
                                 :hover "hover:bg-[#4338ca] dark:hover:bg-[#7ba8ff]"
                                 :focus "focus:outline-none"
                                 :text "text-white font-medium"}
                       :danger {:bg "bg-red-600 dark:bg-red-500"
                                :hover "hover:bg-red-500 dark:hover:bg-red-400"
                                :focus "focus:outline-none"
                                :text "text-white font-medium"}
                       :secondary {:bg "bg-white dark:bg-[#252525]"
                                   :hover "hover:bg-[#f5f5f5] dark:hover:bg-[#2a2a2a]"
                                   :focus "focus:outline-none"
                                   :text "text-[#525252] dark:text-[#d0d0d0] font-medium ring-1 ring-inset ring-[#e0e0e0] dark:ring-[#333333]"}
                       :success {:bg "bg-green-500 dark:bg-green-600"
                                 :hover "hover:bg-green-600 dark:hover:bg-green-500"
                                 :focus "focus:outline-none"
                                 :text "text-white font-medium"}
                       :info {:bg "bg-blue-500 dark:bg-blue-600"
                              :hover "hover:bg-blue-600 dark:hover:bg-blue-500"
                              :focus "focus:outline-none"
                              :text "text-white font-medium"}
                       :ghost {:bg ""
                               :hover ""
                               :focus "focus:outline-none"
                               :text "text-[#737373] dark:text-[#a0a0a0] hover:text-[#4f46e5] dark:hover:text-[#5b8ff9] font-medium"}}}
   :input {:base "block w-full h-11 rounded-lg border bg-transparent shadow-sm focus:outline-hidden focus:ring-3"
           :sizes {:xs "px-3 py-2 text-xs"
                   :sm "px-3.5 py-2 text-sm"
                   :md "px-4 py-2.5 text-sm"
                   :lg "px-4 py-3 text-base"
                   :xl "px-5 py-3.5 text-lg"}
           :border "border-[#d0d0d0] dark:border-[#333333] focus:border-[#4f46e5] dark:focus:border-[#5b8ff9]"
           :focus "focus:ring-[#4f46e5]/10 dark:focus:ring-[#5b8ff9]/20"
           :bg "bg-white dark:bg-[#1a1a1a]"
           :text "text-[#171717] dark:text-[#e5e5e5]"
           :placeholder "placeholder:text-[#a3a3a3] dark:placeholder:text-[#707070]"}
   :label {:base "mb-1.5 block font-medium"
           :sizes {:xs "text-xs"
                   :sm "text-sm"
                   :md "text-sm"
                   :lg "text-base"
                   :xl "text-lg"}
           :text "text-[#525252] dark:text-[#d0d0d0]"
           :required "text-red-500 dark:text-red-400"}
   :alert {:base "rounded-md p-4 border"
           :variants {:success {:bg "bg-green-50 dark:bg-green-900/20"
                                :border "border-green-400 dark:border-green-700"
                                :text "text-green-800 dark:text-green-300"}
                      :warning {:bg "bg-[#fff7ed] dark:bg-[#ff9500]/20"
                                :border "border-[#fb923c] dark:border-[#ff9500]"
                                :text "text-[#c2410c] dark:text-[#ffa500]"}
                      :error {:bg "bg-red-50 dark:bg-red-900/20"
                              :border "border-red-400 dark:border-red-700"
                              :text "text-red-800 dark:text-red-300"}
                      :info {:bg "bg-blue-50 dark:bg-[#5b8ff9]/20"
                             :border "border-blue-400 dark:border-[#5b8ff9]"
                             :text "text-blue-800 dark:text-[#7ba8ff]"}}}
   :navbar {:bg "bg-[#f7f7f7] dark:bg-[#202020]"
            :text "text-[#525252] dark:text-[#d0d0d0]"
            :hover "hover:bg-[#e5e5e5] hover:text-[#171717] dark:hover:bg-[#2a2a2a] dark:hover:text-white"
            :radius "rounded-md"}
   :select {:base "block w-full h-11 rounded-lg border bg-transparent shadow-sm focus:outline-hidden focus:ring-3 appearance-none"
            :sizes {:xs "px-3 py-2 text-xs"
                    :s "px-3.5 py-2 text-sm"
                    :md "px-4 py-2.5 text-sm"
                    :lg "px-4 py-3 text-base"
                    :xl "px-5 py-3.5 text-lg"}
            :border "border-[#d0d0d0] dark:border-[#333333] focus:border-[#4f46e5] dark:focus:border-[#5b8ff9]"
            :focus "focus:ring-[#4f46e5]/10 dark:focus:ring-[#5b8ff9]/20"
            :bg "bg-white dark:bg-[#1a1a1a]"
            :text "text-[#171717] dark:text-[#e5e5e5]"
            :icon "absolute inset-y-0 right-0 flex items-center pr-2 pointer-events-none"}
   :modal {:overlay "fixed inset-0 z-50 bg-black/50 dark:bg-black/80 transition-opacity"
           :container "fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6"
           :dialog "relative bg-white dark:bg-[#252525] text-[#171717] dark:text-[#e5e5e5] rounded-xl shadow-2xl max-w-md w-full max-h-[90vh] overflow-y-auto"
           :sizes {:sm "max-w-sm"
                   :md "max-w-md"
                   :lg "max-w-lg"
                   :xl "max-w-xl"
                   :2xl "max-w-2xl"
                   :full "max-w-full mx-4"}}
   :table {:container "w-full overflow-x-auto shadow ring-1 ring-black ring-opacity-5 md:rounded-lg dark:ring-[#333333] dark:ring-opacity-50"
           :base "min-w-full divide-y divide-[#e0e0e0] dark:divide-[#333333]"
           :header {:bg "bg-[#f9f9f9] dark:bg-[#202020]"
                    :text "text-xs font-medium text-[#737373] dark:text-[#a0a0a0] uppercase tracking-wider"
                    :padding "px-6 py-3"}
           :body {:bg "bg-white dark:bg-[#1a1a1a]"
                  :divider "divide-y divide-[#f0f0f0] dark:divide-[#2a2a2a]"}
           :row {:hover "hover:bg-[#f9f9f9] dark:hover:bg-[#252525]"
                 :even "bg-white dark:bg-[#1a1a1a]"
                 :odd "bg-[#fafafa] dark:bg-[#202020]"}
           :cell {:text "text-sm text-[#171717] dark:text-[#e5e5e5]"
                  :padding "px-6 py-4 whitespace-nowrap"}}
   :heading {:text "text-[#171717] dark:text-[#e5e5e5]"
             :variants {:secondary {:text "text-[#525252] dark:text-[#d0d0d0]"}
                        :caption {:text "text-[#737373] dark:text-[#a0a0a0]"}}}
   :text {:text "text-[#171717] dark:text-[#e5e5e5]"
          :variants {:secondary {:text "text-[#525252] dark:text-[#d0d0d0]"}
                     :caption {:text "text-[#737373] dark:text-[#a0a0a0]"}}}
   :tab {:border "border-[#e0e0e0]"
         :text "text-[#737373]"
         :hover "hover:border-[#d0d0d0] hover:text-[#525252]"
         :active {:border "border-indigo-500"
                  :text "text-indigo-600"}
         :icon {:active "text-indigo-500"
                :inactive "text-[#a3a3a3]"}}
   :dropdown {:menu {:bg "bg-white dark:bg-[#2a2a2a]"
                     :border "ring-1 ring-black ring-opacity-5 dark:ring-white dark:ring-opacity-10"
                     :shadow "shadow-xl"
                     :divider "divide-y divide-[#f0f0f0] dark:divide-[#2a2a2a]"}
              :item {:base "flex items-center gap-3 px-4 py-3 text-sm font-medium transition-colors"
                     :variants {:default {:text "text-[#525252] dark:text-[#e5e5e5]"
                                          :hover "hover:bg-indigo-50 dark:hover:bg-[#333333] hover:text-indigo-600 dark:hover:text-white"}
                                :danger {:text "text-red-700 dark:text-red-400"
                                         :hover "hover:bg-red-50 dark:hover:bg-red-900/20 hover:text-red-600 dark:hover:text-red-300"}}}}})


(defn- get-theme-class
  "Get a class string from the theme configuration"
  [component-type & path]
  (get-in *theme* (into [component-type] path)))

(defn- get-size-class
  "Get the size-specific class for a component"
  [component-type size]
  (get-in *theme* [component-type :sizes size]))

(defn- get-variant-classes
  "Get all classes for a component variant"
  [component-type variant]
  (get-in *theme* [component-type :variants variant]))

(defn- resolve-theme-classes
  "Returns a map of {key resolved-class} from a spec.
   Each spec entry is {attr-key [theme-path...]}.
   Uses (attr-key attrs) if present, else (get-theme-class theme-key ...path)."
  [theme-key spec attrs]
  (into {}
        (map (fn [[attr-key theme-path]]
               [attr-key (or (get attrs attr-key)
                             (apply get-theme-class theme-key theme-path))])
             spec)))

(defn- build-themed-attrs
  "Resolve theme overrides from spec, combine into a single class, merge with attrs.
   `spec` is a map of {attr-override-key [theme-path...]}.
   Override keys are removed from the returned attrs."
  [theme-key spec attrs]
  (let [resolved (resolve-theme-classes theme-key spec attrs)
        combined (apply tw (vals resolved))
        filtered (apply dissoc attrs (keys spec))]
    (merge-attrs {:class combined} filtered)))


(defmethod c/resolve-alias ::icon
  [_ attrs _content]
  (let [icon-id (or (:id attrs) "")
        icon-class (or (:class attrs) "")
        size (:size attrs)
        size-class (when size (str "h-" size " w-" size))
        final-class (tw (or size-class (when-not (seq icon-class) "h-5 w-5")) icon-class)]
    [:svg {:class final-class
           :viewBox "0 0 24 24"
           :fill "currentColor"
           :xmlns "http://www.w3.org/2000/svg"}
     [:use {:href (str "/heroicons-sprite.svg#" icon-id)}]]))

(defmethod c/resolve-alias ::view
  [_ attrs content]
  (let [theme-bg (or (:bg-class attrs) (get-theme-class :view :bg))
        base-attrs {:class (tw "w-full min-h-full" theme-bg)}
        filtered-attrs (dissoc attrs :bg-class)
        merged-attrs (merge-attrs base-attrs filtered-attrs)]
    [:div merged-attrs
     content]))

(defn- layout-element [base-class attrs content]
  [:div (merge-attrs {:class base-class} attrs) content])

(defmethod c/resolve-alias ::row [_ attrs content]
  (layout-element "flex flex-row" attrs content))

(defmethod c/resolve-alias ::col [_ attrs content]
  (layout-element "flex flex-col" attrs content))

(defmethod c/resolve-alias ::flex-between [_ attrs content]
  (layout-element "flex justify-between items-center" attrs content))

(defmethod c/resolve-alias ::center-hv [_ attrs content]
  (layout-element "w-full h-full flex items-center justify-center" attrs content))

(defn- resolve-text-variant [theme-key variant]
  (if-let [variant-text (get-in *theme* [theme-key :variants variant :text])]
    variant-text
    (get-in *theme* [theme-key :text])))

(defn- themed-text-element [tag theme-key attrs content]
  (let [theme-text (resolve-text-variant theme-key (:variant attrs))
        attrs (dissoc attrs :variant)]
    (into [tag (merge-attrs {:class theme-text} attrs)] content)))

(defmethod c/resolve-alias ::h1 [_ attrs content]
  (themed-text-element :h1 :heading attrs content))

(defmethod c/resolve-alias ::h2 [_ attrs content]
  (themed-text-element :h2 :heading attrs content))

(defmethod c/resolve-alias ::h3 [_ attrs content]
  (themed-text-element :h3 :heading attrs content))

(defmethod c/resolve-alias ::h4 [_ attrs content]
  (themed-text-element :h4 :heading attrs content))

(defmethod c/resolve-alias ::span [_ attrs content]
  (themed-text-element :span :text attrs content))

(defmethod c/resolve-alias ::p [_ attrs content]
  (themed-text-element :p :text attrs content))

(defmethod c/resolve-alias ::code
  [_ attrs content]
  (into [:pre (build-themed-attrs :code
                {:base-class [:base]
                 :bg-class   [:bg]
                 :text-class [:text]}
                attrs)]
        content))

(defmethod c/resolve-alias ::hr
  [_ attrs _content]
  (let [variant (:variant attrs)
        theme-border (if (= variant :light)
                       (get-theme-class :hr :light)
                       (get-theme-class :hr :border))
        base-attrs {:class (tw "border-t" theme-border)}
        filtered-attrs (dissoc attrs :variant)
        merged-attrs (merge-attrs base-attrs filtered-attrs)]
    [:hr merged-attrs]))

(defmethod c/resolve-alias ::card
  [_ attrs content]
  [:div (build-themed-attrs :card
          {:bg-class     [:bg]
           :border-class [:border]
           :shadow-class [:shadow]
           :radius-class [:radius]
           :ring-class   [:ring]}
          attrs)
   content])

(defmethod c/resolve-alias ::card-with-header
  [_ attrs content]
  (let [themed (resolve-theme-classes :card-with-header
                 {:bg-class        [:bg]
                  :border-class    [:border]
                  :shadow-class    [:shadow]
                  :radius-class    [:radius]
                  :ring-class      [:ring]
                  :header-bg-class [:header-bg]}
                 attrs)
        base-attrs {:class (tw "overflow-hidden"
                               (:border-class themed) (:bg-class themed)
                               (:shadow-class themed) (:radius-class themed)
                               (:ring-class themed))}
        filtered-attrs (apply dissoc attrs (keys themed))
        merged-attrs (merge-attrs base-attrs filtered-attrs)
        header (first content)
        body (rest content)]
    [:div merged-attrs
     [:div {:class (tw "px-4 py-5 sm:px-6" (:header-bg-class themed))} header]
     [:div {:class "px-4 py-5 sm:p-6"} body]]))

(def ^:private stat-colors
  {"green"  {:value "text-green-600 dark:text-green-400"
             :icon  "text-green-500 dark:text-green-400"}
   "red"    {:value "text-red-600 dark:text-red-400"
             :icon  "text-red-500 dark:text-red-400"}
   "blue"   {:value "text-blue-600 dark:text-blue-400"
             :icon  "text-blue-500 dark:text-blue-400"}
   "yellow" {:value "text-yellow-600 dark:text-yellow-400"
             :icon  "text-yellow-500 dark:text-yellow-400"}
   "purple" {:value "text-purple-600 dark:text-purple-400"
             :icon  "text-purple-500 dark:text-purple-400"}})

(defmethod c/resolve-alias ::stat
  [_ attrs content]
  (let [theme-bg (get-theme-class :stat :bg)
        theme-base (get-theme-class :stat :base)
        theme-label (get-theme-class :stat :label)
        theme-value (get-theme-class :stat :value)
        color (:color attrs)
        colors (get stat-colors color)
        icon (:icon attrs)
        filtered-attrs (dissoc attrs :color :value :label :icon)]
    [:div (merge-attrs {:class (tw theme-bg theme-base)} filtered-attrs)
     [:div.flex.items-center.justify-between
      [:div
       [:dt {:class theme-label} (:label attrs)]
       [:dd {:class (if (:value colors)
                      (str "mt-1 text-3xl font-semibold tracking-tight " (:value colors))
                      theme-value)}
        (:value attrs)]]
      (when icon
        [:div {:class (tw "h-10 w-10 opacity-50" (:icon colors "text-[#a0a0a0] dark:text-[#707070]"))}
         icon])]]))

(defmethod c/resolve-alias ::button
  [_ attrs content]
  (let [size (or (:size attrs) :md)
        variant (or (:variant attrs) :primary)
        variant-classes (get-variant-classes :button variant)
        size-class (get-size-class :button size)
        base-class (get-theme-class :button :base)
        btn-class (tw
                   base-class
                   size-class
                   (or (:bg-class attrs) (:bg variant-classes))
                   (or (:text-class attrs) (:text variant-classes))
                   (or (:hover-class attrs) (:hover variant-classes))
                   (or (:focus-class attrs) (:focus variant-classes)))
        base-attrs {:type (or (:type attrs) "button")
                    :class btn-class}
        filtered-attrs (dissoc attrs
                               :size :title :variant :type
                               :bg-class :hover-class :focus-class :text-class)
        merged-attrs (merge-attrs base-attrs filtered-attrs)
        body (or (seq content) [(:title attrs)])]

    (into [:button merged-attrs] body)))

(defn- form-control-classes
  "Resolve themed classes common to form controls (input, select).
   Returns {:class combined-string, :themed resolved-map}.
   `extra-spec` merges into the default border/focus/bg/text spec.
   `exclude-from-class` is a set of keys to resolve but not include in the class string."
  ([theme-key attrs extra-spec]
   (form-control-classes theme-key attrs extra-spec #{}))
  ([theme-key attrs extra-spec exclude-from-class]
   (let [size (or (:size attrs) :md)
         spec (merge {:border-class [:border]
                      :focus-class  [:focus]
                      :bg-class     [:bg]
                      :text-class   [:text]}
                     extra-spec)
         themed (resolve-theme-classes theme-key spec attrs)
         class-vals (vals (apply dissoc themed exclude-from-class))
         control-class (apply tw (get-theme-class theme-key :base)
                                  (get-size-class theme-key size)
                                  class-vals)]
     {:class control-class :themed themed})))

(defmethod c/resolve-alias ::input
  [_ attrs _content]
  (let [{:keys [class]} (form-control-classes :input attrs
                          {:placeholder-class [:placeholder]})
        base-attrs (cond-> {:type (or (:type attrs) "text")
                            :class class}
                     (:placeholder attrs) (assoc :placeholder (:placeholder attrs))
                     (:value attrs)       (assoc :value (:value attrs))
                     (:name attrs)        (assoc :name (:name attrs)))
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
        themed (resolve-theme-classes :label
                 {:text-class     [:text]
                  :required-class [:required]}
                 attrs)
        label-class (tw (get-theme-class :label :base)
                        (get-size-class :label size)
                        (:text-class themed))
        base-attrs (cond-> {:class label-class}
                     for-id (assoc :for for-id))
        filtered-attrs (dissoc attrs :size :required? :for :text-class :required-class)
        merged-attrs (merge-attrs base-attrs filtered-attrs)]
    [:label merged-attrs
     content
     (when required?
       [:span {:class (:required-class themed)} " *"])]))

(def ^:private alert-icons
  {:success [::icon#solid-check-circle
             {:class "h-5 w-5 text-green-400 dark:text-green-500"}]
   :warning [::icon#solid-exclamation-triangle
             {:class "h-5 w-5 text-yellow-400 dark:text-yellow-500"}]
   :error [::icon#solid-x-circle
           {:class "h-5 w-5 text-red-400 dark:text-red-500"}]
   :info [::icon#solid-information-circle
          {:class "h-5 w-5 text-blue-400 dark:text-blue-500"}]})

(defmethod c/resolve-alias ::alert
  [_ attrs content]
  (let [alert-type (or (:type attrs) :info)
        base-class (get-theme-class :alert :base)
        variant-classes (get-in *theme* [:alert :variants alert-type])
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
         [:button.focus:outline-none
          {:class "text-[#a3a3a3] hover:text-[#737373]"
           :type "button"
           :onclick "this.closest('[class*=\"rounded-md p-4 border\"]').remove()"}
          [:span.sr-only "Dismiss"]
          [::icon#solid-x-mark
           {:class "h-5 w-5"}]]])]]))

(defmethod c/resolve-alias ::navbar
  [_ attrs content]
  (let [theme-bg (or (:bg-class attrs) (get-theme-class :navbar :bg))
        logo-url (or (:logo-url attrs) "/weave.svg")
        title (:title attrs)
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
          {:class "font-medium text-lg text-[#d0d0d0] dark:text-[#d0d0d0]"}
          title])]

      ;; Mobile menu toggle button
      [:div.sm:hidden
       [:button.block.hover:text-white.focus:text-white.focus:outline-none
        {:class "text-[#d0d0d0]"
         :data-on-click "$navbarOpen = !$navbarOpen"
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
  (let [{:keys [class themed]} (form-control-classes :select attrs
                                 {:icon-class [:icon]}
                                 #{:icon-class})
        placeholder (:placeholder attrs)
        value (:selected attrs)
        name-val (:name attrs)
        id (or (:id attrs) name-val)
        options (or (:options attrs) [])
        base-attrs (cond-> {:class class}
                     name-val (assoc :name name-val)
                     id       (assoc :id id))
        filtered-attrs (dissoc attrs
                               :size :placeholder :selected :name :id :options
                               :border-class :focus-class :bg-class :text-class
                               :icon-class)
        merged-attrs (merge-attrs base-attrs filtered-attrs)]
    [:div.relative
     [:select merged-attrs
      (when (and placeholder (str/blank? value))
        [:option {:value ""} placeholder])
      (for [option options]
        [:option {:value (:value option)
                  :selected (= value (:value option))}
         (:label option)])]
     [:div {:class (:icon-class themed)}
      [::icon#solid-chevron-up-down
       {:class "h-5 w-5 text-[#a3a3a3]"}]]]))

(defmethod c/resolve-alias ::a
  [_ attrs content]
  [:a (build-themed-attrs :link {:base-class [:base]} attrs)
   (first content)])

(def ^:private sidebar-close-js
  (clj->js
   (let [sidebar (js/document.getElementById "sidebar")
         backdrop (js/document.getElementById "sidebar-backdrop")
         content (js/document.getElementById "sidebar-content")
         toggle (js/document.getElementById "sidebar-toggle")]
     (.add (.-classList sidebar) "-translate-x-full")
     (.add (.-classList backdrop) "hidden")
     (.remove (.-classList toggle) "hidden")
     (when content (.add (.-classList content) "ml-0")))))

(def ^:private sidebar-toggle-js
  (clj->js
   (let [sidebar (js/document.getElementById "sidebar")
         backdrop (js/document.getElementById "sidebar-backdrop")
         content (js/document.getElementById "sidebar-content")
         toggle (js/document.getElementById "sidebar-toggle")]
     (if (.contains (.-classList sidebar) "-translate-x-full")
       (do
         (.remove (.-classList sidebar) "-translate-x-full")
         (.remove (.-classList backdrop) "hidden")
         (.add (.-classList toggle) "hidden")
         (when content (.remove (.-classList content) "ml-0")))
       (do
         (.add (.-classList sidebar) "-translate-x-full")
         (.add (.-classList backdrop) "hidden")
         (.remove (.-classList toggle) "hidden")
         (when content (.add (.-classList content) "ml-0")))))))

(defmethod c/resolve-alias ::sidebar
  [_ attrs content]
  (let [theme-bg (or (:bg-class attrs) (get-theme-class :sidebar :bg))
        theme-mobile-bg (or (get-theme-class :sidebar :mobile-bg) "bg-[#252525]")]

    [:div.flex.min-h-full
     [:div#sidebar-backdrop.fixed.inset-0.bg-opacity-75.z-20.hidden.lg:hidden
      {:class theme-mobile-bg
       :onclick sidebar-close-js}]

     [:aside#sidebar
      {:class (tw theme-bg
                  "fixed left-0 bottom-0 z-30 w-64"
                  "transform -translate-x-full lg:translate-x-0"
                  "transition-transform duration-300 ease-in-out")}
      [:nav
       {:class "flex-1 px-2 py-4 overflow-y-auto flex flex-col h-full"}
       content]]

     [:div#sidebar-toggle.fixed.bottom-4.left-4.lg:hidden.z-30
      [:button.p-2.rounded-full.text-white.shadow-lg
       {:class theme-mobile-bg
        :onclick sidebar-toggle-js}
       [::icon#solid-bars-3 {:class "h-6 w-6"}]]]]))

(defmethod c/resolve-alias ::sidebar-group
  [_ attrs content]
  (let [theme-group-text (or (get-theme-class :sidebar :group-text) "text-[#a3a3a3]")
        collapsible? (contains? attrs :collapsed)
        collapsed? (:collapsed attrs)
        group-id (when collapsible?
                   (or (:id attrs) (str "sidebar-group-" (gensym))))]
    [:div.mb-6
     (when collapsible?
       [:input {:type "checkbox"
                :id group-id
                :class "peer hidden"
                :checked (not collapsed?)}])

     (when-let [title (:title attrs)]
       (if collapsible?
         [:label.px-3.mb-2.text-xs.font-semibold.uppercase.cursor-pointer.flex.items-center.justify-between.select-none
          {:class theme-group-text
           :for group-id}
          title
          [:svg.w-4.h-4.transition-transform.peer-checked:rotate-180
           {:fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
           [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
                   :d "M19 9l-7 7-7-7"}]]]
         [:h3.px-3.mb-2.text-xs.font-semibold.uppercase
          {:class theme-group-text}
          title]))

     (if collapsible?
       [:ul.space-y-1.overflow-hidden.transition-all.duration-300.ease-in-out
        {:class (if collapsed?
                  "peer-checked:max-h-0 max-h-96"
                  "peer-checked:max-h-96 max-h-0")}
        content]
       [:ul.space-y-1
        content])]))

(defn- nav-item-classes
  "Resolve themed classes and build item class for navigation components.
   Returns {:themed resolved-map, :item-class combined-string, :active-signal? bool}."
  [theme-key attrs extra-classes]
  (let [themed (resolve-theme-classes theme-key
                 {:text-class   [:text]
                  :hover-class  [:hover]
                  :active-class [:active]
                  :radius-class [:radius]}
                 attrs)
        active (:active attrs false)
        active-signal? (string? active)
        base-classes (tw extra-classes (:radius-class themed))
        item-class (if active-signal?
                     (tw (:text-class themed) (:hover-class themed) base-classes)
                     (tw (:text-class themed)
                         (if active (:active-class themed) (:hover-class themed))
                         base-classes))]
    {:themed themed :item-class item-class :active-signal? active-signal?}))

(defmethod c/resolve-alias ::sidebar-item
  [_ attrs content]
  (let [{:keys [themed item-class active-signal?]}
        (nav-item-classes :sidebar attrs
          "flex items-center px-3 py-2 text-sm font-medium cursor-pointer")
        active (:active attrs false)
        icon (:icon attrs)
        handler (:handler attrs)
        href (:href attrs)
        anchor-attrs (cond-> {:class item-class}
                       active-signal? (assoc :data-class (str "{ '" (:active-class themed) "': " active " }"))
                       (and handler href) (assoc :data-on-click__prevent handler)
                       (and handler (not href)) (assoc :data-on-click handler)
                       href (assoc :href href))]
    [:li
     [:a anchor-attrs
      (when icon
        [::icon {:id icon :class "h-5 w-5 mr-2"}])
      (or content (:label attrs))]]))

(defmethod c/resolve-alias ::sidebar-layout
  [_ _ content]
  (let [sidebar-element (first content)
        content-element (second content)]
    [:div.flex.min-h-full
     sidebar-element
     [:div#sidebar-content
      {:class "flex-1 transition-all duration-300 ease-in-out ml-0 lg:ml-64 overflow-auto"}
      content-element]]))

(defmethod c/resolve-alias ::tabs
  [_ attrs content]
  (let [theme-border (get-theme-class :tab :border)]
    [:div attrs
     ;; Mobile vertical tabs (hidden on larger screens)
     [:div.sm:hidden
      [:nav {:aria-label "Tabs"
             :class "flex flex-col space-y-1"}
       content]]

     ;; Desktop horizontal tabs (hidden on mobile)
     [:div.hidden.sm:block
      [:div.border-b {:class theme-border}
       [:nav {:aria-label "Tabs"
              :class "-mb-px flex space-x-8"}
        content]]]]))

(defmethod c/resolve-alias ::tab-item
  [_ attrs content]
  (let [active? (:active attrs false)
        icon (:icon attrs)
        handler (:handler attrs)
        label (or content (:label attrs))
        base-classes "group inline-flex items-center text-sm font-medium"
        desktop-classes "sm:border-b-2 sm:border-l-0 sm:px-1 sm:py-4"
        mobile-classes "border-l-2 border-b-0 px-4 py-3 w-full sm:w-auto"
        theme-active-border (get-theme-class :tab :active :border)
        theme-active-text (get-theme-class :tab :active :text)
        theme-text (get-theme-class :tab :text)
        theme-hover (get-theme-class :tab :hover)
        theme-icon-active (get-theme-class :tab :icon :active)
        theme-icon-inactive (get-theme-class :tab :icon :inactive)
        active-classes (if active?
                         (tw theme-active-border theme-active-text)
                         (tw "border-transparent" theme-text theme-hover))
        icon-classes (if active?
                       theme-icon-active
                       theme-icon-inactive)]
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
  (let [{:keys [item-class]} (nav-item-classes :navbar attrs
                               "px-3 py-2 text-sm font-medium cursor-pointer block mb-1 sm:mb-0 sm:inline-block")]
    [:a {:class item-class
         :data-on-click (:handler attrs)}
     (when-let [icon (:icon attrs)]
       [::icon {:id icon :class "h-5 w-5 mr-2 inline-block"}])
     (or content (:label attrs))]))

(defmethod c/resolve-alias ::sign-in
  [_ attrs content]
  (let [{:keys [logo-url logo-alt title
                username-label username-placeholder
                password-label password-placeholder
                submit-text forgot-password-text forgot-password-url
                register-text register-url
                error-message error-signal on-submit]
         :or {logo-url "/weave.svg"
              logo-alt "Your Company"
              title "Sign in to your account"
              username-label "Email"
              username-placeholder "Enter your email"
              password-label "Password"
              password-placeholder "Enter your password"
              submit-text "Sign In"
              forgot-password-text "Forgot your password?"
              forgot-password-url "/#/forgot-password"
              register-text "Don't have an account? Sign up"
              register-url "/#/register"
              error-message "Invalid username or password."
              error-signal "$_error"}}
        attrs
        on-submit (or on-submit (core/handler []))

        container-classes (get-theme-class :view :bg)
        card-container-classes "mx-auto w-full sm:max-w-lg"
        heading-classes "mt-10 text-center text-2xl/9 font-bold tracking-tight text-[#171717] dark:text-[#f5f5f5]"
        form-container-classes "mt-10 mx-auto w-full sm:max-w-lg"
        form-classes "space-y-6"
        footer-text-classes "mt-10 text-center text-sm/6 text-[#737373] dark:text-[#a3a3a3]"
        link-classes "font-semibold text-indigo-600 hover:text-indigo-500 dark:text-indigo-400 dark:hover:text-indigo-300 cursor-pointer"
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
              :class "p-3 mb-4 text-sm text-red-700 bg-red-100 dark:text-red-400 dark:bg-red-900/20 rounded-lg border border-red-400 dark:border-red-700"
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
        signal (-> (or (:id attrs) "modal")
                   (csk/->camelCase))
        overlay-class (get-theme-class :modal :overlay)
        container-class (get-theme-class :modal :container)
        dialog-class (get-theme-class :modal :dialog)
        size-class (get-size-class :modal size)
        dialog-class-with-size (tw dialog-class size-class)
        base-attrs {:id signal
                    :data-show (str "$" signal)}
        filtered-attrs (dissoc attrs :size :id)
        merged-attrs (merge-attrs base-attrs filtered-attrs)]
    [:div merged-attrs
     [:div {:class overlay-class}]
     [:div {:class container-class}
      [:div {:class dialog-class-with-size}
       content]]]))

;;    Example:
;;    [::table {:columns [{:name "name" :label "Name" :align :left}
;;                        {:name "age" :label "Age" :align :right}]
;;              :rows [{:name "Alice" :age 30}
;;                     {:name "Bob" :age 25}]}]
(defmethod c/resolve-alias ::table
  [_ attrs _content]
  (let [{:keys [id columns rows class class-table class-header class-row]} attrs
        themed (resolve-theme-classes :table
                 {:container-class      [:container]
                  :base-class           [:base]
                  :header-bg-class      [:header :bg]
                  :header-text-class    [:header :text]
                  :header-padding-class [:header :padding]
                  :body-bg-class        [:body :bg]
                  :body-divider-class   [:body :divider]
                  :row-hover-class      [:row :hover]
                  :row-even-class       [:row :even]
                  :row-odd-class        [:row :odd]
                  :cell-text-class      [:cell :text]
                  :cell-padding-class   [:cell :padding]}
                 attrs)
        table-class (merge-classes (:base-class themed) (or class-table class))
        filtered-attrs (dissoc attrs
                               :container-class :base-class :header-bg-class
                               :header-text-class :header-padding-class :body-bg-class
                               :body-divider-class :row-hover-class :row-even-class
                               :row-odd-class :cell-text-class :cell-padding-class
                               :class :class-table :class-header :class-row)]
    [:div {:id id :class (:container-class themed)}
     [:table (merge-attrs {:class table-class} filtered-attrs)
      [:thead {:class (tw (:header-bg-class themed) class-header)}
       [:tr
        (for [column columns]
          [:th
           {:key (:name column)
            :class (tw (:header-padding-class themed) (:header-text-class themed)
                       (case (:align column)
                         :right "text-right"
                         :left "text-left"
                         "text-left")
                       (:class column))}
           (:label column)])]]
      [:tbody {:class (tw (:body-divider-class themed) (:body-bg-class themed))}
       (for [[index row] (map-indexed vector rows)]
         [:tr
          {:class (tw (:row-hover-class themed)
                      (if (even? index) (:row-even-class themed) (:row-odd-class themed))
                      class-row)}
          (for [column columns]
            [:td
             {:key (:name column)
              :class (tw (:cell-padding-class themed) (:cell-text-class themed)
                         (case (:align column)
                           :right "text-right"
                           :left "text-left"
                           "text-left")
                         (:class column))}
             (get row (keyword (:name column)) "-")])])]]]))

(defmethod c/resolve-alias ::dropdown
  [_ attrs content]
  (let [{:keys [label icon variant size use-fixed position min-width z-index]
         :or {variant :secondary, size :s, use-fixed false
              position :right, min-width "min-w-[200px]", z-index "z-[9999]"}}
        attrs
        position-class (case position :left "left-0" :right "right-0" "right-0")
        positioning-type (if use-fixed "fixed" "absolute")
        menu-classes (tw positioning-type position-class "hidden"
                         min-width z-index "dropdown-menu")
        variant-classes (get-variant-classes :button variant)
        size-class (get-size-class :button size)
        base-class (get-theme-class :button :base)
        btn-class (tw base-class
                      size-class
                      (:bg variant-classes)
                      (:text variant-classes)
                      (:hover variant-classes)
                      (:focus variant-classes)
                      "gap-x-1.5 dropdown-trigger")
        menu-bg (get-theme-class :dropdown :menu :bg)
        menu-border (get-theme-class :dropdown :menu :border)
        menu-shadow (get-theme-class :dropdown :menu :shadow)
        menu-divider (get-theme-class :dropdown :menu :divider)
        container-attrs (dissoc attrs :label :icon :variant :size :use-fixed
                                :position :min-width :z-index)
        button-attrs (cond-> {:type "button"
                              :class btn-class
                              :onclick "toggleDropdown(this)"}
                       use-fixed (assoc :onmouseenter "positionDropdown(this)"))]
    [:div.relative.dropdown-container container-attrs
     ;; Trigger button
     [:button button-attrs
      [:span.flex.items-center.gap-1.5
       (when icon [::icon {:id icon :class "w-4 h-4"}])
       label
       [::icon {:id "solid-chevron-down"
                :class "w-3 h-3 transition-transform dropdown-chevron"}]]]
     ;; Menu container
     [:div {:class menu-classes
            :style (when use-fixed "pointer-events: auto;")}
      [:div {:class (tw "mt-2 py-1 rounded-lg" menu-border menu-bg menu-shadow)}
       [:ul {:class menu-divider}
        content]]]]))

(defmethod c/resolve-alias ::dropdown-item
  [_ attrs content]
  (let [variant (or (:variant attrs) :default)
        item-base (get-theme-class :dropdown :item :base)
        variant-classes (get-in *theme* [:dropdown :item :variants variant])
        item-class (tw item-base
                       (:text variant-classes)
                       (:hover variant-classes))
        ;; Destructure child element to merge item classes into it
        child (first content)
        [tag child-attrs & child-content] (if (vector? child) child [:div {} child])
        child-attrs (if (map? child-attrs) child-attrs {})
        child-content (if (map? child-attrs) child-content (cons child-attrs child-content))
        merged-class (merge-classes item-class (:class child-attrs ""))
        filtered-attrs (dissoc attrs :variant)
        final-attrs (merge filtered-attrs child-attrs {:class merged-class})]
    [:li (into [tag final-attrs] child-content)]))

(defmethod c/resolve-alias ::time
  [_ attrs _content]
  (let [time-value (:time attrs)
        format-pattern (or (:format attrs) "yyyy-MM-dd HH:mm:ss")
        timezone-str (or (:timezone attrs) core/*timezone*)
        zone (ZoneId/of timezone-str)
        zdt (cond
              (instance? Instant time-value)
              (.atZone ^Instant time-value zone)

              (instance? ZonedDateTime time-value)
              (.withZoneSameInstant ^ZonedDateTime time-value zone)

              (instance? LocalDateTime time-value)
              (.atZone ^LocalDateTime time-value zone)

              (instance? Long time-value)
              (.atZone (Instant/ofEpochMilli time-value) zone)

              (string? time-value)
              (.atZone (Instant/parse time-value) zone)

              :else
              (throw (ex-info "Unsupported time type" {:type (type time-value)})))
        formatter (DateTimeFormatter/ofPattern format-pattern)
        formatted (.format zdt formatter)
        iso-datetime (.format zdt DateTimeFormatter/ISO_OFFSET_DATE_TIME)
        base-attrs {:datetime iso-datetime}
        filtered-attrs (dissoc attrs :time :format :timezone)
        merged-attrs (merge-attrs base-attrs filtered-attrs)]
    [:time merged-attrs formatted]))

(def ^:private default-badge-colors
  "Default badge colour classes keyed by [color variant]."
  {"green"  {:outlined "bg-green-50 dark:bg-green-900/20 text-green-700 dark:text-green-400 ring-green-600/20 dark:ring-green-500/30"
             :pill     "bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-400"}
   "red"    {:outlined "bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-400 ring-red-600/20 dark:ring-red-500/30"
             :pill     "bg-red-100 dark:bg-red-900/30 text-red-800 dark:text-red-400"}
   "blue"   {:outlined "bg-blue-50 dark:bg-blue-900/20 text-blue-700 dark:text-blue-400 ring-blue-600/20 dark:ring-blue-500/30"
             :pill     "bg-blue-100 dark:bg-blue-900/30 text-blue-800 dark:text-blue-400"}
   "yellow" {:outlined "bg-yellow-50 dark:bg-yellow-900/20 text-yellow-700 dark:text-yellow-400 ring-yellow-600/20 dark:ring-yellow-500/30"
             :pill     "bg-yellow-100 dark:bg-yellow-900/30 text-yellow-800 dark:text-yellow-400"}
   "purple" {:outlined "bg-purple-50 dark:bg-purple-900/20 text-purple-700 dark:text-purple-400 ring-purple-600/20 dark:ring-purple-500/30"
             :pill     "bg-purple-100 dark:bg-purple-900/30 text-purple-800 dark:text-purple-400"}
   "gray"   {:outlined "bg-[#f9f9f9] dark:bg-[#252525] text-[#525252] dark:text-[#a3a3a3] ring-[#525252]/20 dark:ring-[#737373]/30"
             :pill     "bg-[#f5f5f5] dark:bg-[#252525] text-[#171717] dark:text-[#d0d0d0]"}
   "indigo" {:outlined "bg-indigo-50 dark:bg-indigo-900/20 text-indigo-700 dark:text-indigo-400 ring-indigo-600/20 dark:ring-indigo-500/30"
             :pill     "bg-indigo-100 dark:bg-indigo-900/30 text-indigo-800 dark:text-indigo-400"}})

(defmethod c/resolve-alias ::badge
  [_ attrs content]
  (let [color (or (:color attrs) "gray")
        variant (or (:variant attrs) :pill)
        size (or (:size attrs) :md)
        base-class "inline-flex items-center text-xs font-medium"
        size-class (case size
                     :sm "px-2.5 py-0.5"
                     :md "px-2 py-1")
        shape-class (case variant
                      :pill "rounded-full"
                      :outlined "rounded-md ring-1 ring-inset")
        theme-colors (get-in *theme* [:badge :colors (keyword color)])
        fallback-colors (get default-badge-colors color (get default-badge-colors "gray"))
        colors (or theme-colors fallback-colors)
        color-class (if (= variant :outlined)
                      (or (:outlined colors) (:pill colors))
                      (:pill colors))
        full-class (tw base-class size-class shape-class color-class (:class attrs))
        title (or (:title attrs)
                  (when (every? string? content)
                    (str/join " " content)))
        filtered-attrs (-> (dissoc attrs :color :variant :size)
                           (assoc :class full-class)
                           (cond-> title (assoc :title title)))]
    (into [:span filtered-attrs] content)))

(defmethod c/resolve-alias ::toggle
  [_ attrs _content]
  (let [disabled (:disabled attrs)
        input-keys #{:name :id :checked :disabled}
        input-attrs (-> (select-keys attrs input-keys)
                        (assoc :type "checkbox")
                        (merge (into {}
                                 (filter (fn [[k _]] (and (keyword? k)
                                                         (str/starts-with? (name k) "data-")))
                                         attrs))))
        label-class (tw "relative inline-flex items-center"
                        (if disabled "cursor-not-allowed opacity-50" "cursor-pointer")
                        (:class attrs))]
    [:label {:class label-class}
     [:input.sr-only.peer input-attrs]
     [:div
      {:class "w-11 h-6 bg-[#e0e0e0] dark:bg-[#333333] peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-blue-300 dark:peer-focus:ring-blue-800 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-[#d0d0d0] dark:after:border-[#737373] after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-blue-600"}]]))
