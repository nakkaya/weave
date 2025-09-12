(ns weave.test.browser
  (:require
   [clojure.test :refer [is]]
   [etaoin.api :as e]
   [integrant.core :as ig]
   [weave.core :as core]))

(def ^:dynamic *browser* nil)

(def port 3333)

(def url (str "http://localhost:" port))

(def weave-options {:http-kit {:port port}
                    :sse {:enabled true
                          :keep-alive true}})

(defn driver-options []
  {:path-driver "chromedriver"
   :args [(str "--user-data-dir=/tmp/chrome-data-" (random-uuid))
          "--no-sandbox"]})

(defmacro with-browser
  "Helper macro for weave tests that need server and driver setup.
   Automatically navigates to the test URL.
   Usage: (with-browser view-fn weave-options
            (testing \"description\"
              ;; test body with access to server and *browser* bindings))"
  [view weave-options & body]
  `(let [~'server (core/run ~view ~weave-options)
         ~'driver (e/chrome-headless (driver-options))]
     (try
       (binding [*browser* ~'driver]
         (e/go *browser* url)
         ~@body)
       (finally
         (e/quit ~'driver)
         (ig/halt! ~'server)))))

(defn visible? [id]
  (e/wait-visible *browser* {:id id})
  (is (e/visible? *browser* {:id id})))

(defn clear [id]
  (e/clear *browser* {:id id}))

(defn fill [id value]
  (e/fill *browser* {:id id} value))

(defn click [id]
  (e/click *browser* {:id id}))

(defn el-text [id]
  (e/get-element-text *browser* {:id id}))

(defn new-tab []
  (e/js-execute *browser* "window.open(arguments[0], '_blank');" url))

(defn tabs []
  (e/get-window-handles *browser*))

(defn switch-tab [tab-handle]
  (e/switch-window *browser* tab-handle))

(defn has-alert?
  "Check if there's currently an alert or confirm dialog present."
  []
  (e/has-alert? *browser*))

(defn accept-alert
  "Accept an alert or confirm dialog that appears."
  []
  (e/accept-alert *browser*))
