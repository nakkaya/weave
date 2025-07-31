(ns weave.browser
  (:require
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
