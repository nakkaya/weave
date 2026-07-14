(ns weave.theme.spec
  "The theme contract: every class slot a theme must fill, and a validator.

   `contract` is a shape map — the same nested structure as a theme, with the
   sentinel `:req` at every leaf a conforming theme must define. It doubles as
   documentation: reading it tells you exactly what a new theme has to provide.

   The contract is the set of leaf paths common to all shipped themes (default,
   gov-uk, cyberpunk). Themes may define *more* (e.g. gov-uk's :card-with-header
   :header-bg, extra button variants) — the contract is the guaranteed minimum
   the components rely on.")

(def contract
  {:view {:bg :req}
   :hr {:border :req :light :req}
   :card {:bg :req :border :req :radius :req}
   :card-with-header {:bg :req :border :req :shadow :req :radius :req :ring :req}
   :code {:base :req :bg :req :text :req}
   :stat {:base :req :bg :req :label :req :value :req}
   :link {:base :req}
   :sidebar {:bg :req :text :req :hover :req :active :req :radius :req}
   :navbar {:bg :req :text :req :hover :req :radius :req}
   :button {:base :req
            :sizes {:xs :req :sm :req :md :req :lg :req :xl :req :icon :req}
            :variants {:primary   {:bg :req :hover :req :focus :req :text :req}
                       :secondary {:bg :req :hover :req :focus :req :text :req}
                       :danger    {:bg :req :hover :req :focus :req :text :req}
                       :ghost     {:bg :req :hover :req :focus :req :text :req}}}
   :input {:base :req :border :req :focus :req :bg :req :text :req :placeholder :req
           :sizes {:xs :req :sm :req :md :req :lg :req :xl :req}}
   :select {:base :req :border :req :focus :req :bg :req :text :req :icon :req
            :sizes {:xs :req :md :req :lg :req :xl :req}}
   :checkbox {:base :req}
   :toggle {:track :req}
   :label {:base :req :text :req :required :req
           :sizes {:xs :req :sm :req :md :req :lg :req :xl :req}}
   :alert {:base :req
           :variants {:success {:bg :req :border :req :text :req}
                      :warning {:bg :req :border :req :text :req}
                      :error   {:bg :req :border :req :text :req}
                      :info    {:bg :req :border :req :text :req}}}
   :modal {:overlay :req :container :req :dialog :req
           :sizes {:sm :req :md :req :lg :req :xl :req :2xl :req :full :req}}
   :table {:container :req :base :req
           :header {:bg :req :text :req :padding :req}
           :body {:bg :req :divider :req}
           :row {:hover :req :even :req :odd :req}
           :cell {:text :req :padding :req}}
   :heading {:text :req
             :variants {:secondary {:text :req} :caption {:text :req}}}
   :text {:text :req
          :variants {:secondary {:text :req} :caption {:text :req}}}
   :tab {:border :req :text :req :hover :req
         :active {:border :req :text :req}
         :icon {:active :req :inactive :req}}
   :dropdown {:menu {:bg :req :border :req :shadow :req :divider :req}
              :item {:base :req
                     :variants {:default {:text :req :hover :req}
                                :danger  {:text :req :hover :req}}}}})

(defn- leaf-paths
  "All paths to :req leaves in a shape map."
  [shape]
  (mapcat (fn [[k v]]
            (if (map? v)
              (map #(cons k %) (leaf-paths v))
              [[k]]))
          shape))

(def required-paths
  "Flat vector of every path a conforming theme must define."
  (mapv vec (leaf-paths contract)))

(defn missing-paths
  "Return the required paths `theme` does not define. A path is missing only when
   absent (nil); an explicit \"\" is a valid value meaning \"no classes here\"."
  [theme]
  (filterv (fn [path] (nil? (get-in theme path)))
           required-paths))

(defn validate!
  "Throw if `theme` (named `id`) is missing any required class slot.
   Returns the theme unchanged on success, so it can wrap a def value."
  [id theme]
  (let [missing (missing-paths theme)]
    (when (seq missing)
      (throw (ex-info (str "Theme " id " is missing " (count missing)
                           " required class slot(s): " (pr-str missing))
                      {:theme id :missing missing})))
    theme))
