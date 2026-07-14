(ns weave.theme.default
  "The default weave theme — a matching light + dark neutral palette with an
   indigo accent. Semantic states (success/danger/info) use Tailwind's named
   colour scale; the neutral ramp and accent use the palette below.

   This is also the base theme: weave.components/get-theme-class falls back here
   for any slot a named theme leaves unset, so shared defaults (status colours,
   chrome, sign-in) live here once."
  (:require [weave.theme.spec :as spec]
            [weave.theme.util :refer [cx u pre duo]]))

(def theme
  (spec/validate! :default
    (let [;; ── Neutral ramp (light / dark) ────────────────────────────
          page      "#f7f7f7"   ; page background (light)
          base      "#1a1a1a"   ; darkest surface (dark page / inputs)
          surface   "#252525"   ; dark card / panel
          line      "#e0e0e0"   ; border (light)
          line-d    "#333333"   ; border (dark)
          ctrl-line "#d0d0d0"   ; form-control border (light)
          soft      "#f0f0f0"   ; faint divider (light)
          soft-d    "#2a2a2a"   ; faint divider / hover (dark)
          muted     "#f5f5f5"   ; muted fill (light)
          panel-d   "#202020"   ; sidebar / navbar / table header (dark)
          ink       "#171717"   ; primary text (light)
          ink-d     "#e5e5e5"   ; primary text (dark)
          text2     "#525252"   ; secondary text (light)
          text2-d   "#d0d0d0"   ; secondary text (dark)
          text3     "#737373"   ; caption / label (light)
          text3-d   "#a0a0a0"   ; caption / label (dark)
          faint     "#a3a3a3"   ; placeholder / icons (light)
          faint-d   "#707070"   ; placeholder (dark)
          zebra     "#f9f9f9"   ; table header / row hover (light)
          zebra2    "#fafafa"   ; odd row (light)

         ;; ── Accent (indigo) ────────────────────────────────────────
          accent    "#4f46e5"
          accent-h  "#4338ca"
          accent-d  "#5b8ff9"
          accent-dh "#7ba8ff"]

      {:view {:bg (duo "bg" page base)}

       :hr {:border (duo "border" line line-d)
            :light  (duo "border" soft soft-d)}

       :card {:bg     (cx "bg-white" (pre "dark" (u "bg" surface)))
              :border (cx "border" (duo "border" line line-d))
              :radius "rounded-lg"}

       :card-with-header {:bg     (cx "bg-white" (pre "dark" (u "bg" surface)))
                          :border (cx "divide-y" (duo "divide" line line-d))
                          :shadow "shadow-sm"
                          :radius "rounded-lg"
                          :ring   (cx "ring-1" (duo "ring" line line-d))}

       :code {:bg   (duo "bg" muted base)
              :text (duo "text" ink ink-d)
              :base "font-mono text-sm rounded p-3 overflow-x-auto whitespace-pre-wrap"}

       :stat {:bg    (cx "bg-white" (pre "dark" (u "bg" surface)))
              :base  (cx "overflow-hidden rounded-lg px-4 py-5 shadow ring-1 ring-inset"
                         (duo "ring" line line-d) "sm:p-6")
              :label (cx "truncate text-sm font-medium" (duo "text" text3 text3-d))
              :value (cx "mt-1 text-3xl font-semibold tracking-tight" (duo "text" ink ink-d))
             ;; optional per-stat status colours (:color "green" etc.)
              :icon-default (duo "text" text3-d faint-d)
              :colors {"green"  {:value "text-green-600 dark:text-green-400"
                                 :icon  "text-green-500 dark:text-green-400"}
                       "red"    {:value "text-red-600 dark:text-red-400"
                                 :icon  "text-red-500 dark:text-red-400"}
                       "blue"   {:value "text-blue-600 dark:text-blue-400"
                                 :icon  "text-blue-500 dark:text-blue-400"}
                       "yellow" {:value "text-yellow-600 dark:text-yellow-400"
                                 :icon  "text-yellow-500 dark:text-yellow-400"}
                       "purple" {:value "text-purple-600 dark:text-purple-400"
                                 :icon  "text-purple-500 dark:text-purple-400"}}}

       :link {:base (cx (u "text" accent) (pre "hover" (u "text" accent-h))
                        (pre "dark" (u "text" accent-d)) (pre "dark:hover" (u "text" accent-dh)))}

       :sidebar {:bg     (duo "bg" page panel-d)
                 :text   (duo "text" text2 text2-d)
                 :hover  (cx (pre "hover" (u "bg" ink-d) (u "text" ink))
                             (pre "dark:hover" (u "bg" soft-d)) "dark:hover:text-white")
                 :active (cx (u "bg" line) (u "text" ink)
                             (pre "dark" (u "bg" soft-d)) "dark:text-white")
                 :radius "rounded-md"
                 :mobile-bg  (u "bg" surface)
                 :group-text (u "text" faint)}

       :navbar {:bg     (duo "bg" page panel-d)
                :text   (duo "text" text2 text2-d)
                :hover  (cx (pre "hover" (u "bg" ink-d) (u "text" ink))
                            (pre "dark:hover" (u "bg" soft-d)) "dark:hover:text-white")
                :radius "rounded-md"
                :title  (duo "text" text2-d text2-d)
                :toggle (u "text" text2-d)}

       :button {:base  (cx "inline-flex items-center justify-center text-center gap-2"
                           "rounded-lg shadow-theme-xs transition")
                :sizes {:xs "px-2 py-1.5 text-xs"
                        :sm "px-3 py-2 text-sm"
                        :md "px-4 py-2.5 text-sm"
                        :lg "px-5 py-3.5 text-base"
                        :xl "px-6 py-4 text-lg"
                        :icon "p-2"}
                :variants {:primary
                           {:bg    (duo "bg" accent accent-d)
                            :hover (cx (pre "hover" (u "bg" accent-h))
                                       (pre "dark:hover" (u "bg" accent-dh)))
                            :focus "focus:outline-none"
                            :text  "text-white font-medium"}
                           :danger
                           {:bg    "bg-red-600 dark:bg-red-500"
                            :hover "hover:bg-red-500 dark:hover:bg-red-400"
                            :focus "focus:outline-none"
                            :text  "text-white font-medium"}
                           :secondary
                           {:bg    (cx "bg-white" (pre "dark" (u "bg" surface)))
                            :hover (cx (pre "hover" (u "bg" muted))
                                       (pre "dark:hover" (u "bg" soft-d)))
                            :focus "focus:outline-none"
                            :text  (cx (duo "text" text2 text2-d)
                                       "font-medium ring-1 ring-inset"
                                       (duo "ring" line line-d))}
                           :success
                           {:bg    "bg-green-500 dark:bg-green-600"
                            :hover "hover:bg-green-600 dark:hover:bg-green-500"
                            :focus "focus:outline-none"
                            :text  "text-white font-medium"}
                           :info
                           {:bg    "bg-blue-500 dark:bg-blue-600"
                            :hover "hover:bg-blue-600 dark:hover:bg-blue-500"
                            :focus "focus:outline-none"
                            :text  "text-white font-medium"}
                           :ghost
                           {:bg    ""
                            :hover ""
                            :focus "focus:outline-none"
                            :text  (cx (duo "text" text3 text3-d)
                                       (pre "hover" (u "text" accent))
                                       (pre "dark:hover" (u "text" accent-d)) "font-medium")}}}

       :input {:base  (cx "block w-full h-11 rounded-lg border bg-transparent shadow-sm"
                          "focus:outline-hidden focus:ring-3")
               :sizes {:xs "px-3 py-2 text-xs"
                       :sm "px-3.5 py-2 text-sm"
                       :md "px-4 py-2.5 text-sm"
                       :lg "px-4 py-3 text-base"
                       :xl "px-5 py-3.5 text-lg"}
               :border (cx (duo "border" ctrl-line line-d)
                           (pre "focus" (u "border" accent))
                           (pre "dark:focus" (u "border" accent-d)))
               :focus  (cx (pre "focus" (u "ring" accent 10))
                           (pre "dark:focus" (u "ring" accent-d 20)))
               :bg     (cx "bg-white" (pre "dark" (u "bg" base)))
               :text   (duo "text" ink ink-d)
               :placeholder (cx (pre "placeholder" (u "text" faint))
                                (pre "dark:placeholder" (u "text" faint-d)))}

       :checkbox {:base (cx "relative [&+label]:mb-0 appearance-none"
                            "w-4 h-4 shrink-0 cursor-pointer rounded border"
                            (u "border" ctrl-line) "bg-white transition"
                            (pre "dark" (u "border" line-d) (u "bg" base))
                            (pre "hover" (u "border" accent))
                            (pre "dark:hover" (u "border" accent-d))
                            (pre "checked" (u "bg" accent) (u "border" accent))
                            (pre "dark:checked" (u "bg" accent-d) (u "border" accent-d))
                            "checked:after:content-['✓'] after:absolute after:left-1/2"
                            "after:top-1/2 after:-translate-x-1/2 after:-translate-y-1/2"
                            "after:text-[11px] after:font-bold after:leading-none after:text-white"
                            "focus:outline-none focus:ring-2"
                            (pre "focus" (u "ring" accent 40))
                            (pre "dark:focus" (u "ring" accent-d 40)))}

       :toggle {:track (cx "w-11 h-6" (duo "bg" line line-d)
                           "peer-focus:outline-none peer-focus:ring-4"
                           "peer-focus:ring-blue-300 dark:peer-focus:ring-blue-800"
                           "rounded-full peer peer-checked:after:translate-x-full"
                           "peer-checked:after:border-white after:content-['']"
                           "after:absolute after:top-[2px] after:left-[2px] after:bg-white"
                           (str "after:" (u "border" ctrl-line))
                           (str "dark:after:" (u "border" text3))
                           "after:border after:rounded-full after:h-5 after:w-5"
                           "after:transition-all peer-checked:bg-blue-600")}

       :label {:base "mb-1.5 block font-medium"
               :sizes {:xs "text-xs" :sm "text-sm" :md "text-sm" :lg "text-base" :xl "text-lg"}
               :text (duo "text" text2 text2-d)
               :required "text-red-500 dark:text-red-400"}

       :select {:base  (cx "block w-full h-11 rounded-lg border bg-transparent shadow-sm"
                           "focus:outline-hidden focus:ring-3 appearance-none")
                :sizes {:xs "px-3 py-2 text-xs"
                        :s  "px-3.5 py-2 text-sm"
                        :md "px-4 py-2.5 text-sm"
                        :lg "px-4 py-3 text-base"
                        :xl "px-5 py-3.5 text-lg"}
                :border (cx (duo "border" ctrl-line line-d)
                            (pre "focus" (u "border" accent))
                            (pre "dark:focus" (u "border" accent-d)))
                :focus  (cx (pre "focus" (u "ring" accent 10))
                            (pre "dark:focus" (u "ring" accent-d 20)))
                :bg     (cx "bg-white" (pre "dark" (u "bg" base)))
                :text   (duo "text" ink ink-d)
                :icon   "absolute inset-y-0 right-0 flex items-center pr-2 pointer-events-none"
                :chevron (u "text" faint)}

       :alert {:base "rounded-md p-4 border"
               :dismiss (cx (u "text" faint) (pre "hover" (u "text" text3)))
               :icon {:success "text-green-400 dark:text-green-500"
                      :warning "text-yellow-400 dark:text-yellow-500"
                      :error   "text-red-400 dark:text-red-500"
                      :info    "text-blue-400 dark:text-blue-500"}
               :variants {:success {:bg "bg-green-50 dark:bg-green-900/20"
                                    :border "border-green-400 dark:border-green-700"
                                    :text "text-green-800 dark:text-green-300"}
                          :warning {:bg (cx (u "bg" "#fff7ed") (pre "dark" (u "bg" "#ff9500" 20)))
                                    :border (cx (u "border" "#fb923c")
                                                (pre "dark" (u "border" "#ff9500")))
                                    :text (cx (u "text" "#c2410c")
                                              (pre "dark" (u "text" "#ffa500")))}
                          :error   {:bg "bg-red-50 dark:bg-red-900/20"
                                    :border "border-red-400 dark:border-red-700"
                                    :text "text-red-800 dark:text-red-300"}
                          :info    {:bg (cx "bg-blue-50" (pre "dark" (u "bg" accent-d 20)))
                                    :border (cx "border-blue-400"
                                                (pre "dark" (u "border" accent-d)))
                                    :text (cx "text-blue-800" (pre "dark" (u "text" accent-dh)))}}}

       :modal {:overlay   "fixed inset-0 z-50 bg-black/50 dark:bg-black/80 transition-opacity"
               :container "fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6"
               :dialog    (cx "relative bg-white" (pre "dark" (u "bg" surface))
                              (duo "text" ink ink-d)
                              "rounded-xl shadow-2xl max-w-md w-full max-h-[90vh] overflow-y-auto")
               :sizes {:sm "max-w-sm"
                       :md "max-w-md"
                       :lg "max-w-lg"
                       :xl "max-w-xl"
                       :2xl "max-w-2xl"
                       :full "max-w-full mx-4"}}

       :table {:container (cx "w-full overflow-x-auto shadow ring-1 ring-black ring-opacity-5"
                              "md:rounded-lg" (pre "dark" (u "ring" line-d)) "dark:ring-opacity-50")
               :base      (cx "min-w-full divide-y" (duo "divide" line line-d))
               :header {:bg      (duo "bg" zebra panel-d)
                        :text    (cx "text-xs font-medium" (duo "text" text3 text3-d)
                                     "uppercase tracking-wider")
                        :padding "px-6 py-3"}
               :body {:bg      (cx "bg-white" (pre "dark" (u "bg" base)))
                      :divider (cx "divide-y" (duo "divide" soft soft-d))}
               :row {:hover (cx (pre "hover" (u "bg" zebra)) (pre "dark:hover" (u "bg" surface)))
                     :even  (cx "bg-white" (pre "dark" (u "bg" base)))
                     :odd   (duo "bg" zebra2 panel-d)}
               :cell {:text    (cx "text-sm" (duo "text" ink ink-d))
                      :padding "px-6 py-4 whitespace-nowrap"}}

       :heading {:text (duo "text" ink ink-d)
                 :variants {:secondary {:text (duo "text" text2 text2-d)}
                            :caption   {:text (duo "text" text3 text3-d)}}}

       :text {:text (duo "text" ink ink-d)
              :variants {:secondary {:text (duo "text" text2 text2-d)}
                         :caption   {:text (duo "text" text3 text3-d)}}}

       :tab {:border (u "border" line)
             :text   (u "text" text3)
             :hover  (pre "hover" (u "border" ctrl-line) (u "text" text2))
             :active {:border "border-indigo-500" :text "text-indigo-600"}
             :icon   {:active "text-indigo-500" :inactive (u "text" faint)}}

       :dropdown
       {:menu {:bg      (cx "bg-white" (pre "dark" (u "bg" soft-d)))
               :border  "ring-1 ring-black ring-opacity-5 dark:ring-white dark:ring-opacity-10"
               :shadow  "shadow-xl"
               :divider (cx "divide-y" (duo "divide" soft soft-d))}
        :item {:base "flex items-center gap-3 px-4 py-3 text-sm font-medium transition-colors"
               :variants {:default {:text  (cx (u "text" text2) (pre "dark" (u "text" ink-d)))
                                    :hover (cx "hover:bg-indigo-50"
                                               (pre "dark:hover" (u "bg" line-d))
                                               "hover:text-indigo-600 dark:hover:text-white")}
                          :danger  {:text  "text-red-700 dark:text-red-400"
                                    :hover (cx "hover:bg-red-50 dark:hover:bg-red-900/20"
                                               "hover:text-red-600 dark:hover:text-red-300")}}}}

       :badge
       {:colors
        {:green  {:pill     "bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-400"
                  :outlined (cx "bg-green-50 dark:bg-green-900/20"
                                "text-green-700 dark:text-green-400"
                                "ring-green-600/20 dark:ring-green-500/30")}
         :red    {:pill     "bg-red-100 dark:bg-red-900/30 text-red-800 dark:text-red-400"
                  :outlined (cx "bg-red-50 dark:bg-red-900/20"
                                "text-red-700 dark:text-red-400"
                                "ring-red-600/20 dark:ring-red-500/30")}
         :blue   {:pill     "bg-blue-100 dark:bg-blue-900/30 text-blue-800 dark:text-blue-400"
                  :outlined (cx "bg-blue-50 dark:bg-blue-900/20"
                                "text-blue-700 dark:text-blue-400"
                                "ring-blue-600/20 dark:ring-blue-500/30")}
         :yellow {:pill     (cx "bg-yellow-100 dark:bg-yellow-900/30"
                                "text-yellow-800 dark:text-yellow-400")
                  :outlined (cx "bg-yellow-50 dark:bg-yellow-900/20"
                                "text-yellow-700 dark:text-yellow-400"
                                "ring-yellow-600/20 dark:ring-yellow-500/30")}
         :purple {:pill     (cx "bg-purple-100 dark:bg-purple-900/30"
                                "text-purple-800 dark:text-purple-400")
                  :outlined (cx "bg-purple-50 dark:bg-purple-900/20"
                                "text-purple-700 dark:text-purple-400"
                                "ring-purple-600/20 dark:ring-purple-500/30")}
         :gray   {:pill     (cx (u "bg" muted) (pre "dark" (u "bg" surface))
                                (duo "text" ink text2-d))
                  :outlined (cx (u "bg" zebra) (pre "dark" (u "bg" surface))
                                (duo "text" text2 faint)
                                (u "ring" text2 20) (str "dark:" (u "ring" text3 30)))}
         :indigo {:pill     (cx "bg-indigo-100 dark:bg-indigo-900/30"
                                "text-indigo-800 dark:text-indigo-400")
                  :outlined (cx "bg-indigo-50 dark:bg-indigo-900/20"
                                "text-indigo-700 dark:text-indigo-400"
                                "ring-indigo-600/20 dark:ring-indigo-500/30")}}}

      ;; weave.components/sign-in chrome (structural classes stay in the component)
       :sign-in {:heading (duo "text" ink muted)
                 :footer  (duo "text" text3 faint)
                 :link    (cx "text-indigo-600 hover:text-indigo-500"
                              "dark:text-indigo-400 dark:hover:text-indigo-300")
                 :error   (cx "text-red-700 bg-red-100"
                              "dark:text-red-400 dark:bg-red-900/20"
                              "border-red-400 dark:border-red-700")}})))
