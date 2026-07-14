(ns weave.theme.util
  "Tiny helpers for authoring themes so class strings stay short and readable.

   The whole point: a theme value is just a Tailwind class string. Instead of
   writing one 400-character literal per value, compose it from named palette
   colours and reusable fragments with `cx`, and interpolate arbitrary-value
   utilities with `u` / `pre`."
  (:require [clojure.string :as str]))

(defn cx
  "Join Tailwind class tokens into one space-separated string.
   Nil-safe, flattens nested seqs, trims each token and drops blanks.
   Same join semantics as weave.components/tw."
  [& tokens]
  (->> tokens
       flatten
       (remove nil?)
       (map (fn [t] (str/trim (str t))))
       (remove str/blank?)
       (str/join " ")))

(defn u
  "Arbitrary-value utility.
     (u \"bg\" \"#12121a\")      => \"bg-[#12121a]\"
     (u \"bg\" \"#00ff88\" 10)   => \"bg-[#00ff88]/10\"   ; opacity"
  ([util color] (str util "-[" color "]"))
  ([util color opacity] (str util "-[" color "]/" opacity)))

(defn pre
  "Prefix a variant onto each token.
     (pre \"hover\" (u \"bg\" \"#00ff88\") (u \"text\" \"#0a0a0f\"))
     => \"hover:bg-[#00ff88] hover:text-[#0a0a0f]\""
  [variant & tokens]
  (->> (flatten tokens)
       (remove nil?)
       (map #(str variant ":" %))
       (str/join " ")))

(defn duo
  "A light + dark arbitrary-value pair.
     (duo \"bg\" \"#f7f7f7\" \"#1a1a1a\") => \"bg-[#f7f7f7] dark:bg-[#1a1a1a]\""
  [util light dark]
  (cx (u util light) (pre "dark" (u util dark))))
