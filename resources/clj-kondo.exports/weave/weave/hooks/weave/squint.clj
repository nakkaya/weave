(ns hooks.weave.squint
  (:require [clj-kondo.hooks-api :as api]))

(defn clj->js-hook [{:keys [node]}]
  (let [dummy-node (api/token-node nil)]
    {:node dummy-node}))
