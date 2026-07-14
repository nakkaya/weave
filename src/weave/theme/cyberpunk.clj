(ns weave.theme.cyberpunk
  "Cyberpunk theme — neon-on-void, corner-cut panels, mono type.
   Dark only. The decorative corner cuts, accents and grid are pure Tailwind
   arbitrary utilities (no CSS file needed)."
  (:require [weave.theme.spec :as spec]
            [weave.theme.util :refer [cx u pre]]))

;; ── Decoration fragments ─────────────────────────────────────────────
(def ^:private clip-xs
  "[clip-path:polygon(3px_0,100%_0,100%_calc(100%-3px),calc(100%-3px)_100%,0_100%,0_3px)]")
(def ^:private clip-sm
  "[clip-path:polygon(6px_0,100%_0,100%_calc(100%-6px),calc(100%-6px)_100%,0_100%,0_6px)]")
(def ^:private clip-lg
  "[clip-path:polygon(12px_0,100%_0,100%_calc(100%-12px),calc(100%-12px)_100%,0_100%,0_12px)]")

(def ^:private glow-sm "shadow-[0_0_10px_rgba(0,255,136,0.1)]")

(def ^:private spin-hide
  (cx "[appearance:textfield]"
      "[&::-webkit-inner-spin-button]:appearance-none"
      "[&::-webkit-outer-spin-button]:appearance-none"
      "[&::-webkit-inner-spin-button]:m-0"))

;; Two diagonal neon ticks at opposite corners of a cut panel.
(def ^:private corner-accents
  (cx "before:content-[''] before:absolute before:top-0 before:left-0"
      "before:w-[17px] before:h-[2px] before:bg-[#00ff88]"
      "before:origin-top-left before:[transform:rotate(-45deg)] before:z-10"
      "after:content-[''] after:absolute after:bottom-0 after:right-0"
      "after:w-[17px] after:h-[2px] after:bg-[#00ff88]"
      "after:origin-bottom-right after:[transform:rotate(-45deg)] after:z-10"))

;; Layered grid + corner glows on the app background.
(def ^:private grid-bg
  (cx (str "[background-image:"
           "linear-gradient(rgba(0,255,136,0.03)_1px,transparent_1px),"
           "linear-gradient(90deg,rgba(0,255,136,0.03)_1px,transparent_1px),"
           "linear-gradient(rgba(0,255,136,0.05)_1px,transparent_1px),"
           "linear-gradient(90deg,rgba(0,255,136,0.05)_1px,transparent_1px),"
           "radial-gradient(ellipse_at_0%_0%,rgba(0,255,136,0.08)_0%,transparent_50%),"
           "radial-gradient(ellipse_at_100%_100%,rgba(255,0,255,0.05)_0%,transparent_50%)]")
      "[background-size:25px_25px,25px_25px,100px_100px,100px_100px,100%_100%,100%_100%]"))

(def ^:private scanlines
  (cx "before:content-[''] before:fixed before:inset-0"
      "before:pointer-events-none before:z-[9999] before:opacity-30"
      (str "before:[background:repeating-linear-gradient("
           "0deg,transparent,transparent_2px,"
           "rgba(0,0,0,0.1)_2px,rgba(0,0,0,0.1)_4px)]")))

(def theme
  (spec/validate! :cyberpunk
    (let [;; ── Palette ────────────────────────────────────────────────
          void    "#0a0a0f"   ; deep background
          surface "#12121a"   ; card / panel
          muted   "#1c1c2e"   ; elevated background
          line    "#2a2a3a"   ; borders / dividers
          fg      "#e0e0e0"   ; primary text
          fg-dim  "#9ca3af"   ; secondary text
          fg-mute "#6b7280"   ; muted text
          accent  "#00ff88"   ; neon green (primary)
          magenta "#ff00ff"
          cyan    "#00d4ff"
          danger  "#ff3366"
          warning "#ff9500"
          gold    "#ffcc00"
          indigo  "#818cf8"]

      {:view {:bg (cx "relative" (u "bg" void) grid-bg scanlines)}

       :hr {:border (u "border" line)
            :light  (u "border" muted)}

       :card {:bg     (u "bg" surface)
              :border (cx "border" (u "border" line))
              :shadow glow-sm
              :radius (cx "relative" clip-lg corner-accents)
              :ring   ""}

       :card-with-header {:bg     (u "bg" surface)
                          :border (cx "divide-y" (u "divide" line))
                          :shadow glow-sm
                          :radius (cx "relative" clip-lg corner-accents)
                          :ring   ""}

       :code {:bg   (u "bg" surface)
              :text (u "text" accent)
              :base "font-mono text-sm rounded-none p-3 overflow-x-auto whitespace-pre-wrap"}

       :stat {:bg    (u "bg" surface)
              :base  (cx "overflow-hidden px-4 py-5 border" (u "border" line) glow-sm
                         "sm:p-6 relative" clip-lg corner-accents)
              :label (cx "truncate text-sm font-medium uppercase tracking-wider font-mono"
                         (u "text" fg-mute))
              :value (cx "mt-1 text-3xl font-semibold tracking-tight font-mono" (u "text" accent))}

       :link {:base (cx (u "text" cyan)
                        (pre "hover" (u "text" accent))
                        "hover:underline transition-colors")}

       :sidebar {:bg         (cx (u "bg" void) "lg:bg-transparent")
                 :text       (u "text" fg-dim)
                 :hover      (pre "hover" (u "bg" muted) (u "text" accent))
                 :active     (cx (u "bg" muted) (u "text" accent))
                 :radius     "rounded-none"
                 :mobile-bg  (u "bg" void)
                 :group-text (u "text" fg-mute)}

       :navbar {:bg     (u "bg" void)
                :text   (u "text" fg-dim)
                :hover  (pre "hover" (u "bg" muted) (u "text" accent))
                :active (cx (u "bg" muted) (u "text" accent))
                :radius "rounded-none"}

       :button {:base  (cx "inline-flex items-center justify-center text-center gap-2"
                           "shadow-none transition-all duration-150"
                           "uppercase tracking-wider font-mono" clip-sm)
                :link  (cx "flex items-center gap-2 text-sm transition-colors"
                           (u "text" fg-mute) (pre "hover" (u "text" accent)))
                :sizes {:xs "px-2 py-1.5 text-xs"
                        :sm "px-3 py-2 text-sm"
                        :md "px-4 py-3 text-sm"
                        :lg "px-5 py-3.5 text-base"
                        :xl "px-6 py-4 text-lg"
                        :icon "p-2"}
                :variants {:primary
                           {:bg    (cx "bg-transparent border-2" (u "border" accent))
                            :hover (cx (pre "hover" (u "bg" accent) (u "text" void))
                                       "hover:shadow-[0_0_20px_rgba(0,255,136,0.5)]")
                            :focus (cx "focus:outline-none focus:ring-2"
                                       (pre "focus" (u "ring" accent)))
                            :text  (cx (u "text" accent) "font-medium")}
                           :danger
                           {:bg    (cx "bg-transparent border-2" (u "border" danger))
                            :hover (cx (pre "hover" (u "bg" danger) (u "text" void))
                                       "hover:shadow-[0_0_20px_rgba(255,51,102,0.5)]")
                            :focus (cx "focus:outline-none focus:ring-2"
                                       (pre "focus" (u "ring" danger)))
                            :text  (cx (u "text" danger) "font-medium")}
                           :secondary
                           {:bg    (cx "bg-transparent border" (u "border" line))
                            :hover (cx (pre "hover" (u "border" accent) (u "text" accent))
                                       "hover:shadow-[0_0_10px_rgba(0,255,136,0.3)]")
                            :focus (cx "focus:outline-none focus:ring-2"
                                       (pre "focus" (u "ring" accent)))
                            :text  (cx (u "text" fg) "font-medium")}
                           :ghost
                           {:bg    "bg-transparent"
                            :hover (pre "hover" (u "bg" accent 10) (u "text" accent))
                            :focus "focus:outline-none"
                            :text  (cx (u "text" fg-mute) "font-medium")}}}

       :input {:base  (cx "block w-full h-11 border bg-transparent shadow-none"
                          "focus:outline-hidden focus:ring-2 font-mono" clip-sm spin-hide)
               :sizes {:xs "px-3 py-2 text-xs"
                       :sm "px-3.5 py-2 text-sm"
                       :md "px-4 py-2.5 text-sm"
                       :lg "px-4 py-3 text-base"
                       :xl "px-5 py-3.5 text-lg"}
               :border (cx (u "border" line) (pre "focus" (u "border" accent)))
               :focus  (pre "focus" (u "ring" accent 20))
               :bg     (u "bg" surface)
               :text   (u "text" accent)
               :placeholder (pre "placeholder" (u "text" fg-mute))}

       :checkbox {:base (cx "relative [&+label]:mb-0 appearance-none"
                            "w-4 h-4 shrink-0 cursor-pointer"
                            (u "bg" surface) "border" (u "border" line) "transition-all" clip-xs
                            (pre "hover" (u "border" accent))
                            "hover:shadow-[0_0_8px_rgba(0,255,136,0.3)]"
                            (pre "checked" (u "bg" accent) (u "border" accent))
                            "checked:after:content-['✓'] after:absolute after:left-1/2"
                            "after:top-1/2"
                            "after:-translate-x-1/2 after:-translate-y-1/2"
                            "after:text-[12px] after:font-bold"
                            (str "after:" (u "text" void)) "after:leading-none"
                            "focus:outline-none focus:ring-2" (pre "focus" (u "ring" accent 20)))}

       :toggle {:track (cx "w-11 h-6" (u "bg" muted) "border" (u "border" line) "rounded-full peer"
                           "peer-focus:outline-none peer-focus:ring-2"
                           (str "peer-focus:" (u "ring" accent 30))
                           "after:content-[''] after:absolute after:top-[2px] after:left-[2px]"
                           (str "after:" (u "bg" fg-mute))
                           "after:border" (str "after:" (u "border" line))
                           "after:rounded-full after:h-5 after:w-5 after:transition-all"
                           (str "peer-checked:" (u "bg" accent 20))
                           (str "peer-checked:" (u "border" accent 50))
                           "peer-checked:after:translate-x-full"
                           (str "peer-checked:after:" (u "bg" accent))
                           (str "peer-checked:after:" (u "border" accent)))}

       :label {:base "mb-1.5 block font-medium uppercase tracking-wider font-mono"
               :sizes {:xs "text-xs" :sm "text-sm" :md "text-sm" :lg "text-base" :xl "text-lg"}
               :text (u "text" fg-mute)
               :required (u "text" danger)}

       :select {:base  (cx "block w-full h-11 border bg-transparent shadow-none"
                           "focus:outline-hidden focus:ring-2 appearance-none font-mono" clip-sm)
                :sizes {:xs "px-3 py-2 text-xs"
                        :sm "px-3.5 py-2 text-sm"
                        :md "px-4 py-2.5 text-sm"
                        :lg "px-4 py-3 text-base"
                        :xl "px-5 py-3.5 text-lg"}
                :border (cx (u "border" line) (pre "focus" (u "border" accent)))
                :focus  (pre "focus" (u "ring" accent 20))
                :bg     (u "bg" surface)
                :text   (u "text" fg)
                :icon   (cx "absolute inset-y-0 right-0 flex items-center pr-2 pointer-events-none"
                            (u "text" accent))}

       :table {:container (cx "w-full overflow-x-auto border" (u "border" line) glow-sm clip-lg)
               :base      (cx "min-w-full divide-y" (u "divide" line))
               :header {:bg      (u "bg" void)
                        :text    (cx "text-xs font-mono font-medium" (u "text" accent)
                                     "uppercase tracking-wider")
                        :padding "px-6 py-3"}
               :body {:bg      (u "bg" surface)
                      :divider (cx "divide-y" (u "divide" line))}
               :row {:hover (pre "hover" (u "bg" muted))
                     :even  (u "bg" surface)
                     :odd   (u "bg" void)}
               :cell {:text    (cx "text-sm" (u "text" fg) "font-mono")
                      :padding "px-6 py-4 whitespace-nowrap"}}

       :alert {:base (cx "p-4 border relative" clip-lg corner-accents)
               :variants {:success {:bg (u "bg" accent 10)
                                    :border (u "border" accent)
                                    :text (u "text" accent)}
                          :warning {:bg (u "bg" warning 10)
                                    :border (u "border" warning)
                                    :text (u "text" warning)}
                          :error   {:bg (u "bg" danger 10)
                                    :border (u "border" danger)
                                    :text (u "text" danger)}
                          :info    {:bg (u "bg" cyan 10)
                                    :border (u "border" cyan)
                                    :text (u "text" cyan)}}}

       :modal {:overlay   "fixed inset-0 z-50 bg-black/80 transition-opacity"
               :container "fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6"
               :dialog    (cx "relative" (u "bg" surface) "border" (u "border" line)
                              "shadow-[0_0_30px_rgba(0,255,136,0.2)] overflow-hidden" clip-lg)
               :sizes {:sm "max-w-sm w-full"
                       :md "max-w-md w-full"
                       :lg "max-w-lg w-full"
                       :xl "max-w-xl w-full"
                       :2xl "max-w-2xl w-full"
                       :full "max-w-full mx-4"}}

       :heading {:text (u "text" accent)
                 :variants {:secondary {:text (u "text" fg-dim)}
                            :caption   {:text (u "text" fg-mute)}}}

       :text {:text (u "text" fg)
              :variants {:secondary {:text (u "text" fg-dim)}
                         :caption   {:text (u "text" fg-mute)}}}

       :tab {:border (u "border" line)
             :text   (u "text" fg-mute)
             :hover  (pre "hover" (u "border" line) (u "text" fg))
             :active {:border (u "border" accent) :text (u "text" accent)}
             :icon   {:active (u "text" accent) :inactive (u "text" fg-mute)}}

       :dropdown
       {:menu {:bg      (u "bg" surface)
               :border  (cx "ring-1" (u "ring" line))
               :shadow  "shadow-[0_0_20px_rgba(0,255,136,0.15)]"
               :divider (cx "divide-y" (u "divide" line))}
        :item {:base (cx "flex items-center gap-3 px-4 py-3"
                         "text-sm font-medium font-mono transition-colors")
               :variants {:default {:text  (u "text" fg)
                                    :hover (pre "hover" (u "bg" muted) (u "text" accent))}
                          :danger  {:text  (u "text" danger)
                                    :hover (pre "hover" (u "bg" danger 10) (u "text" danger))}}}}

       :badge
       {:radius "rounded"
        :colors
        {:green  {:pill     (cx (u "bg" accent 15) (u "text" accent))
                  :outlined (cx (u "bg" accent 15) (u "text" accent) (u "ring" accent 30))}
         :red    {:pill     (cx (u "bg" danger 15) (u "text" danger))
                  :outlined (cx (u "bg" danger 15) (u "text" danger) (u "ring" danger 30))}
         :blue   {:pill     (cx (u "bg" cyan 15) (u "text" cyan))
                  :outlined (cx (u "bg" cyan 15) (u "text" cyan) (u "ring" cyan 30))}
         :yellow {:pill     (cx (u "bg" gold 15) (u "text" gold))
                  :outlined (cx (u "bg" gold 15) (u "text" gold) (u "ring" gold 30))}
         :purple {:pill     (cx (u "bg" magenta 15) (u "text" magenta))
                  :outlined (cx (u "bg" magenta 15) (u "text" magenta) (u "ring" magenta 30))}
         :gray   {:pill     (cx (u "bg" muted) (u "text" fg-dim))
                  :outlined (cx (u "bg" muted) (u "text" fg-dim) (u "ring" line))}
         :orange {:pill     (cx (u "bg" warning 15) (u "text" warning))
                  :outlined (cx (u "bg" warning 15) (u "text" warning) (u "ring" warning 30))}
         :indigo {:pill     (cx (u "bg" indigo 15) (u "text" indigo))
                  :outlined (cx (u "bg" indigo 15) (u "text" indigo) (u "ring" indigo 30))}}}})))
