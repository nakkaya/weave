(ns weave.view
  (:require [weave.core :as weave]
            [cognitect.transit :as transit])
  (:import [java.io ByteArrayOutputStream ByteArrayInputStream]
           [java.util Base64]))

(defn- encode-state [state]
  (if (empty? state)
    ""
    (let [out (ByteArrayOutputStream.)
          writer (transit/writer out :msgpack)]
      (transit/write writer state)
      (.encodeToString (Base64/getUrlEncoder) (.toByteArray out)))))

(defn- decode-state []
  (if (or (nil? weave/*app-path*) (<= (count weave/*app-path*) 1))
    {}
    (try
      (let [encoded (subs weave/*app-path* 1)
            bytes (.decode (Base64/getUrlDecoder) encoded)
            in (ByteArrayInputStream. bytes)
            reader (transit/reader in :msgpack)
            state (transit/read reader)]
        (if (map? state) state {}))
      (catch Exception _
        {}))))

(defn new
  [{:keys [id default]}]
  {:pre [(keyword? id)]}
  (cond-> {:id id}
    default (assoc :default default)))

(defn add
  [reg {:keys [id signals render]}]
  {:pre [(keyword? id) (or (fn? render) (var? render))]}
  (assoc reg id
         (cond-> {:render render}
           signals (assoc :signals signals))))

(defn- at-default?
  "Check if view-id with params represents the default state (can be omitted from URL)"
  [views view-id params]
  (and (= view-id (:default views))
       (or (nil? params) (empty? params))))

(defn href
  "Generate a URL for a view. Merges with current state for multi-view support."
  ([views view-id]
   (href views view-id {}))
  ([views view-id params]
   (let [views-id (:id views)
         state (decode-state)
         new-state (if (at-default? views view-id params)
                     (dissoc state views-id)
                     (assoc state views-id [view-id params]))
         encoded (encode-state new-state)]
     (if (empty? encoded)
       "#/"
       (str "#/" encoded)))))

(defn reset-signals! [views view-id]
  (when-let [sigs (get-in views [view-id :signals])]
    (weave/push-signal! {view-id sigs})))

(defn- render-view [views view-id params]
  (if-let [render-fn (get-in views [view-id :render])]
    [:div {:id (:id views)} (render-fn views)]
    (when-let [default-id (:default views)]
      (when-let [render-fn (get-in views [default-id :render])]
        [:div {:id (:id views)} (render-fn views)]))))

(defn- get-view-state [views]
  (let [views-id (:id views)
        view-state (get (decode-state) views-id)]
    (if view-state
      view-state
      [(:default views) {}])))

(defn path
  "Returns the current view path as [view-id params-map]."
  [views]
  (get-view-state views))

(defn render
  ([views]
   (let [[view-id params] (get-view-state views)]
     (render-view views view-id (or params {}))))
  ([views view-id]
   (render views view-id {}))
  ([views view-id params]
   (let [views-id (:id views)
         state (decode-state)
         new-state (if (at-default? views view-id params)
                     (dissoc state views-id)
                     (assoc state views-id [view-id params]))
         encoded (encode-state new-state)
         path (if (empty? encoded) "/" (str "/" encoded))]
     (weave/push-path! path)
     (weave/push-html! (render-view views view-id params)))))
