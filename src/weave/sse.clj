(ns weave.sse
  (:require
   [clojure.string :as str]
   [dev.onionpancakes.chassis.core :as c]
   [org.httpkit.server :as hk])
  (:import
   [java.io
    ByteArrayOutputStream Closeable
    OutputStream OutputStreamWriter]
   [java.nio.charset StandardCharsets]
   [java.util.concurrent.locks ReentrantLock]
   [java.util.zip GZIPOutputStream]))

(def ^:private ^:const buffer-size 4096)

(def ^:private patch-elements-event "event: datastar-patch-elements\n")
(def ^:private patch-signals-event  "event: datastar-patch-signals\n")
(def ^:private data-elements-prefix "data: elements ")
(def ^:private data-signals-prefix  "data: signals ")
(def ^:private elements-newline-tail "data: elements ")

(def ^:private mode->literal
  {:outer   nil
   :inner   "inner"
   :replace "replace"
   :prepend "prepend"
   :append  "append"
   :before  "before"
   :after   "after"
   :remove  "remove"})

(deftype SSEConnection
    [ch
     ^ReentrantLock lock
     ^StringBuilder buff
     ^chars scratch
     ^ByteArrayOutputStream baos
     ^OutputStream wrapped-os
     ^OutputStreamWriter writer
     gzip?
     ^:volatile-mutable closed?]

  Closeable
  (close [this]
    (.lock lock)
    (try
      (when-not closed?
        (set! closed? true)
        (try (.close writer) (catch Exception _))
        (try (hk/close ch) (catch Exception _)))
      (finally
        (.unlock lock)))))

(defn- add-data-lines!
  "In-place: insert `data: elements ` right after every '\\n' in `buff`."
  [^StringBuilder buff ^long from]
  (let [tail-len (.length ^String elements-newline-tail)]
    (loop [i (.indexOf buff "\n" from)]
      (when-not (neg? i)
        (.insert buff (inc i) ^String elements-newline-tail)
        (recur (.indexOf buff "\n" (+ i 1 tail-len)))))))

(defn- flush!
  "Drain the connection's StringBuilder through the writer/gzip/BAOS
   chain and hand the resulting bytes to http-kit."
  [^SSEConnection conn]
  (let [^StringBuilder buff (.buff conn)
        ^chars scratch (.scratch conn)
        ^OutputStreamWriter w (.writer conn)
        ^OutputStream wos (.wrapped-os conn)
        ^ByteArrayOutputStream baos (.baos conn)
        total (.length buff)
        cap (alength scratch)]
    (loop [off 0]
      (when (< off total)
        (let [end (min total (+ off cap))
              n (- end off)]
          (.getChars buff off end scratch 0)
          (.write w scratch 0 n)
          (recur end))))
    (.setLength buff 0)
    (.flush w)

    (when (.gzip? conn)
      (.flush wos))

    (let [bytes (.toByteArray baos)]
      (.reset baos)
      (hk/send! (.ch conn) bytes false))))

(defn- with-frame [^SSEConnection conn build-fn]
  (when conn
    (let [^ReentrantLock lock (.lock conn)
          ^StringBuilder buff (.buff conn)]
      (.lock lock)
      (try
        (build-fn buff)
        (.append buff "\n\n")
        (flush! conn)
        (catch java.io.IOException _
          (.close ^Closeable conn))
        (finally
          (.unlock lock))))))

(defn html!
  ([conn hiccup] (html! conn hiccup nil))
  ([^SSEConnection conn hiccup opts]
   (with-frame conn
     (fn [^StringBuilder buff]
       (.append buff ^String patch-elements-event)
       (when-let [sel (:selector opts)]
         (.append buff "data: selector ")
         (.append buff ^String sel)
         (.append buff "\n"))
       (when-let [literal (mode->literal (:mode opts))]
         (.append buff "data: mode ")
         (.append buff ^String literal)
         (.append buff "\n"))
       (when (:use-view-transition opts)
         (.append buff "data: useViewTransition true\n"))
       (when-let [id (:id opts)]
         (.append buff "id: ")
         (.append buff (str id))
         (.append buff "\n"))
       (when-let [retry (:retry-duration opts)]
         (.append buff "retry: ")
         (.append buff (str retry))
         (.append buff "\n"))
       (.append buff ^String data-elements-prefix)
       (let [body-start (.length buff)]
         (c/write-html buff hiccup)
         (add-data-lines! buff body-start))))))

(defn signals!
  [^SSEConnection conn ^String signals-json]
  (with-frame conn
    (fn [^StringBuilder buff]
      (.append buff ^String patch-signals-event)
      (.append buff ^String data-signals-prefix)
      (.append buff ^String signals-json))))

(defn script!
  [^SSEConnection conn ^String script]
  (with-frame conn
    (fn [^StringBuilder buff]
      (.append buff ^String patch-elements-event)
      (.append buff "data: selector body\n")
      (.append buff "data: mode append\n")
      (.append buff ^String data-elements-prefix)
      (let [body-start (.length buff)]
        (c/write-html buff [:script {:data-effect "el.remove()"} script])
        (add-data-lines! buff body-start)))))

(defn close! [^SSEConnection conn]
  (when conn
    (.close ^Closeable conn)))

(defn- open [ch gzip?]
  (let [buff (StringBuilder. buffer-size)
        scratch (char-array buffer-size)
        baos (ByteArrayOutputStream. buffer-size)
        ^OutputStream wrapped (if gzip?
                                (GZIPOutputStream. baos true)
                                baos)
        osw (OutputStreamWriter. wrapped StandardCharsets/UTF_8)]
    (SSEConnection. ch (ReentrantLock.) buff scratch baos wrapped osw gzip? false)))

(defn- headers [ring-request gzip?]
  (cond-> {"Cache-Control" "no-cache"
           "Content-Type"  "text/event-stream"}
    (or (nil? (:protocol ring-request))
        (neg? (compare (:protocol ring-request) "HTTP/1.1")))
    (assoc "Connection" "keep-alive")

    gzip?
    (assoc "Content-Encoding" "gzip")))

(defn- accept-gzip? [ring-request]
  (-> ring-request
      (get-in [:headers "accept-encoding"] "")
      (str/includes? "gzip")))

(defn response [ring-request {:keys [on-open on-close]}]
  (let [gzip? (accept-gzip? ring-request)
        conn-p (promise)]
    (hk/as-channel
     ring-request
     {:on-open
      (fn [ch]
        (hk/send! ch {:status 200
                      :headers (headers ring-request gzip?)}
                  false)
        (let [conn (open ch gzip?)]
          (deliver conn-p conn)
          (on-open conn)))
      :on-close
      (fn [_ status]
        (when (realized? conn-p)
          (try (.close ^Closeable @conn-p) (catch Exception _)))
        (when on-close (on-close status)))})))
