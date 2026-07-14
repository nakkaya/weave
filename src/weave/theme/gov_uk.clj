(ns weave.theme.gov-uk
  "GOV.UK Design System theme. Light-mode only.
   Based on https://design-system.service.gov.uk"
  (:require [weave.theme.spec :as spec]
            [weave.theme.util :refer [cx u pre]]))

(def theme
  (spec/validate! :gov-uk
    (let [;; ── Palette ────────────────────────────────────────────────
          ink        "#0b0c0c"   ; near-black text
          paper      "#f3f3f3"   ; light grey surface
          line       "#cecece"   ; borders / dividers
          grey-text  "#484949"   ; secondary text
          panel-blue "#f4f8fb"   ; sidebar / selected background
          link-blue  "#1a65a6"   ; links
          link-hover "#0f385c"
          link-visit "#54319f"
          blue       "#1d70b8"   ; info / navbar
          blue-dark  "#16548a"
          yellow     "#ffdd00"   ; focus highlight
          green      "#0f7a52"   ; primary / success
          green-dark "#0b5c3e"
          green-drop "#083d29"   ; button drop shadow
          red        "#ca3535"   ; danger
          red-dark   "#982828"
          orange     "#f47738"   ; warning
          grey-drop  "#858686"   ; secondary button drop shadow

         ;; ── Fragments ──────────────────────────────────────────────
         ;; the GOV.UK yellow focus state (outline + fill + ink text)
          focus-yellow (cx "focus:outline-3"
                           (pre "focus" (u "outline" yellow) (u "bg" yellow) (u "text" ink)))
          btn-shadow       (str "shadow-[0_2px_0_" green-drop "]")
          focus-btn-shadow (str "focus:shadow-[0_2px_0_" ink "]")]

      {:view {:bg (u "bg" paper)}

       :hr {:border (u "border" line)
            :light  (u "border" paper)}

       :card {:bg     "bg-white"
              :border (cx "border" (u "border" line))
              :shadow ""
              :radius "rounded-none"
              :ring   ""}

       :card-with-header {:bg        "bg-white"
                          :border    (cx "divide-y" (u "divide" line))
                          :shadow    ""
                          :radius    "rounded-none"
                          :ring      (cx "ring-1" (u "ring" line))
                          :header-bg (u "bg" paper)}

       :code {:bg   (u "bg" paper)
              :text (u "text" ink)
              :base "font-mono text-sm rounded-none p-3 overflow-x-auto whitespace-pre-wrap"}

       :stat {:bg    "bg-white"
              :base  (cx "overflow-hidden px-4 py-5 border-l-4" (u "border" blue) "sm:p-6")
              :label (cx "truncate text-base font-normal" (u "text" grey-text))
              :value (cx "mt-1 text-3xl font-bold tracking-tight" (u "text" ink))}

       :link {:base (cx (u "text" link-blue) "underline"
                        (pre "hover" (u "text" link-hover))
                        (pre "visited" (u "text" link-visit))
                        focus-yellow "focus:no-underline")}

       :sidebar {:bg         (cx (u "bg" panel-blue) "lg:bg-transparent")
                 :text       (u "text" grey-text)
                 :hover      (pre "hover" (u "bg" panel-blue) (u "text" ink))
                 :active     (cx "border-l-[5px]" (u "border" link-blue) (u "bg" panel-blue)
                                 (u "text" ink) "font-bold")
                 :radius     "rounded-none"
                 :mobile-bg  (u "bg" ink)
                 :group-text (u "text" grey-text)}

       :navbar {:bg     (u "bg" blue)
                :text   "text-white"
                :hover  (cx "hover:underline hover:decoration-2"
                            "hover:underline-offset-[10px] hover:text-white")
                :active "underline decoration-2 underline-offset-[10px] text-white font-bold"
                :radius "rounded-none"}

       :button {:base  (cx "inline-flex items-center justify-center text-center gap-2 rounded-none"
                           btn-shadow "transition font-sans font-bold")
                :link  (cx "flex items-center gap-2 text-sm transition-colors"
                           (u "text" link-blue) "hover:underline"
                           (pre "hover" (u "text" link-hover)))
                :sizes {:xs "px-2 py-1.5 text-sm"
                        :s  "px-3 py-2 text-base"
                        :sm "px-3 py-2 text-base"
                        :md "px-4 py-2.5 text-base"
                        :lg "px-5 py-3 text-lg"
                        :xl "px-6 py-3.5 text-lg"
                        :icon "p-2"}
                :variants {:primary   {:bg    (u "bg" green)
                                       :hover (pre "hover" (u "bg" green-dark))
                                       :focus (cx focus-yellow focus-btn-shadow)
                                       :text  "text-white font-bold"}
                           :danger    {:bg    (u "bg" red)
                                       :hover (pre "hover" (u "bg" red-dark))
                                       :focus (cx focus-yellow focus-btn-shadow)
                                       :text  "text-white font-bold"}
                           :secondary {:bg    (cx (u "bg" paper)
                                                  (str "shadow-[0_2px_0_" grey-drop "]"))
                                       :hover (pre "hover" (u "bg" line))
                                       :focus (cx focus-yellow focus-btn-shadow)
                                       :text  (cx (u "text" ink) "font-bold")}
                           :success   {:bg    (u "bg" green)
                                       :hover (pre "hover" (u "bg" green-dark))
                                       :focus (cx focus-yellow focus-btn-shadow)
                                       :text  "text-white font-bold"}
                           :info      {:bg    (u "bg" blue)
                                       :hover (pre "hover" (u "bg" blue-dark))
                                       :focus (cx focus-yellow focus-btn-shadow)
                                       :text  "text-white font-bold"}
                           :ghost     {:bg    "bg-transparent"
                                       :hover (pre "hover" (u "bg" paper) (u "text" ink))
                                       :focus focus-yellow
                                       :text  (cx (u "text" link-blue) "underline font-bold")}}}

       :input {:base  (cx "block w-full h-11 border-2 bg-transparent shadow-none focus:outline-3"
                          (pre "focus" (u "outline" yellow)) "rounded-none font-sans")
               :sizes {:xs "px-3 py-2 text-sm"
                       :s  "px-3 py-2 text-base"
                       :sm "px-3 py-2 text-base"
                       :md "px-4 py-2.5 text-base"
                       :lg "px-4 py-3 text-lg"
                       :xl "px-5 py-3.5 text-lg"}
               :border (cx (u "border" ink) (pre "focus" (u "border" ink)))
               :focus  "focus:ring-0"
               :bg     "bg-white"
               :text   (u "text" ink)
               :placeholder (pre "placeholder" (u "text" grey-text))}

       :checkbox {:base (cx "relative [&+label]:mb-0 appearance-none shrink-0 cursor-pointer"
                            "w-[40px] h-[40px] bg-white border-2" (u "border" ink) "rounded-none"
                            "after:content-[''] after:absolute after:top-[11px] after:left-[9px]"
                            "after:w-[23px] after:h-[12px] after:border-solid"
                            (str "after:" (u "border" ink))
                            "after:border-b-[5px] after:border-l-[5px] after:-rotate-45"
                            "after:opacity-0 checked:after:opacity-100 focus:outline-none"
                            (str "focus:shadow-[0_0_0_3px_" yellow "]"))}

       :toggle {:track (cx "w-11 h-6" (u "bg" paper) "border-2" (u "border" ink)
                           "rounded-full peer peer-focus:outline-none"
                           (str "peer-focus:shadow-[0_0_0_3px_" yellow "]")
                           "after:content-[''] after:absolute after:top-[2px] after:left-[2px]"
                           (str "after:" (u "bg" ink))
                           "after:rounded-full after:h-4 after:w-4 after:transition-all"
                           (str "peer-checked:" (u "bg" green))
                           (str "peer-checked:" (u "border" green))
                           "peer-checked:after:translate-x-full peer-checked:after:bg-white")}

       :label {:base "mb-1.5 block font-bold"
               :sizes {:xs "text-sm" :s "text-base" :sm "text-base"
                       :md "text-base" :lg "text-lg" :xl "text-xl"}
               :text (u "text" ink)
               :required (u "text" red)}

       :select {:base  (cx "block w-full h-11 border-2 bg-transparent shadow-none focus:outline-3"
                           (pre "focus" (u "outline" yellow))
                           "appearance-none rounded-none font-sans")
                :sizes {:xs "px-3 py-2 text-sm"
                        :s  "px-3 py-2 text-base"
                        :md "px-4 py-2.5 text-base"
                        :lg "px-4 py-3 text-lg"
                        :xl "px-5 py-3.5 text-lg"}
                :border (cx (u "border" ink) (pre "focus" (u "border" ink)))
                :focus  "focus:ring-0"
                :bg     "bg-white"
                :text   (u "text" ink)
                :icon   (cx "absolute inset-y-0 right-0 flex items-center pr-2 pointer-events-none"
                            (u "text" ink))}

       :table {:container (cx "w-full overflow-x-auto border" (u "border" line))
               :base      (cx "min-w-full divide-y" (u "divide" line))
               :header {:bg      (u "bg" paper)
                        :text    (cx "text-sm font-bold" (u "text" ink))
                        :padding "px-5 py-3"}
               :body {:bg      "bg-white"
                      :divider (cx "divide-y" (u "divide" line))}
               :row {:hover (pre "hover" (u "bg" paper))
                     :even  "bg-white"
                     :odd   "bg-white"}
               :cell {:text    (cx "text-base" (u "text" ink))
                      :padding "px-5 py-3 whitespace-nowrap"}}

       :alert {:base "p-4 border-l-4"
               :variants {:success {:bg "bg-white" :border (u "border" green) :text (u "text" ink)}
                          :warning {:bg "bg-white" :border (u "border" orange) :text (u "text" ink)}
                          :error   {:bg "bg-white" :border (u "border" red) :text (u "text" ink)}
                          :info    {:bg "bg-white" :border (u "border" blue) :text (u "text" ink)}}}

       :modal {:overlay   "fixed inset-0 z-50 bg-black/50 transition-opacity"
               :container "fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6"
               :dialog    (cx "relative bg-white" (u "text" ink)
                              "rounded-none shadow-lg overflow-hidden")
               :sizes {:sm "max-w-sm w-full"
                       :md "max-w-md w-full"
                       :lg "max-w-lg w-full"
                       :xl "max-w-xl w-full"
                       :2xl "max-w-2xl w-full"
                       :full "max-w-full mx-4"}}

       :heading {:text (u "text" ink)
                 :variants {:secondary {:text (u "text" grey-text)}
                            :caption   {:text (u "text" grey-text)}}}

       :text {:text (u "text" ink)
              :variants {:secondary {:text (u "text" grey-text)}
                         :caption   {:text (u "text" grey-text)}}}

       :tab {:border (u "border" line)
             :text   (u "text" grey-text)
             :hover  (pre "hover" (u "border" line) (u "text" ink))
             :active {:border (u "border" link-blue) :text (u "text" ink)}
             :icon   {:inactive (u "text" grey-text) :active (u "text" link-blue)}}

       :dropdown
       {:menu {:bg      "bg-white"
               :border  (cx "ring-1" (u "ring" line))
               :shadow  "shadow-lg"
               :divider (cx "divide-y" (u "divide" line))}
        :item {:base "flex items-center gap-3 px-4 py-3 text-base font-normal transition-colors"
               :variants {:default {:text  (u "text" ink)
                                    :hover (pre "hover" (u "bg" paper) (u "text" ink))}
                          :danger  {:text  (u "text" red)
                                    :hover (pre "hover" (u "bg" "#fcf5f5") (u "text" red-dark))}}}}

       :badge
       {:colors
        {:green  {:pill     (cx (u "bg" "#cfe4dc") (u "text" "#083d29"))
                  :outlined (cx (u "bg" "#cfe4dc") (u "text" "#083d29") (u "ring" "#083d29" 20))}
         :red    {:pill     (cx (u "bg" "#f4d7d7") (u "text" "#651b1b"))
                  :outlined (cx (u "bg" "#f4d7d7") (u "text" "#651b1b") (u "ring" "#651b1b" 20))}
         :blue   {:pill     (cx (u "bg" "#d2e2f1") (u "text" "#0f385c"))
                  :outlined (cx (u "bg" "#d2e2f1") (u "text" "#0f385c") (u "ring" "#0f385c" 20))}
         :yellow {:pill     (cx (u "bg" "#ffee80") (u "text" "#7a3c1c"))
                  :outlined (cx (u "bg" "#ffee80") (u "text" "#7a3c1c") (u "ring" "#7a3c1c" 20))}
         :purple {:pill     (cx (u "bg" "#ddd6ec") (u "text" "#2a1950"))
                  :outlined (cx (u "bg" "#ddd6ec") (u "text" "#2a1950")
                                (u "ring" "#2a1950" 20))}
         :gray   {:pill     (cx (u "bg" "#cecece") (u "text" "#0b0c0c"))
                  :outlined (cx (u "bg" "#cecece") (u "text" "#0b0c0c") (u "ring" "#0b0c0c" 20))}
         :orange {:pill     (cx (u "bg" "#fde4d7") (u "text" "#7a3c1c"))
                  :outlined (cx (u "bg" "#fde4d7") (u "text" "#7a3c1c") (u "ring" "#7a3c1c" 20))}
         :indigo {:pill     (cx (u "bg" "#ddd6ec") (u "text" "#2a1950"))
                  :outlined (cx (u "bg" "#ddd6ec") (u "text" "#2a1950")
                                (u "ring" "#2a1950" 20))}}}})))
