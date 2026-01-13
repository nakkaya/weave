(ns weave.view-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [etaoin.api :as e]
   [weave.test.browser :refer [*browser* with-browser weave-options visible? click el-text] :as browser]
   [weave.core :as weave]
   [weave.view :as view]))

(defn event-handler-fixture
  [test-fn]
  (binding [weave/*event-handlers* (atom {})]
    (test-fn)))

(use-fixtures :each event-handler-fixture)

;; Test views
(defn welcome-view [_]
  [:div#welcome "Select a page"])

(defn page-one-view [_]
  [:div#page-one "Page One Content"])

(defn page-two-view [_]
  [:div#page-two "Page Two Content"])

(def test-views
  (-> (view/new {:id :content
                 :default :welcome})
      (view/add {:id :welcome
                 :render #'welcome-view})
      (view/add {:id :page-one
                 :render #'page-one-view})
      (view/add {:id :page-two
                 :render #'page-two-view})))

(defn nav-test-view []
  [:div#view
   [:button#go-one
    {:data-on-click (weave/handler [test-views]
                      (view/render test-views :page-one))}
    "Go One"]
   [:button#go-two
    {:data-on-click (weave/handler [test-views]
                      (view/render test-views :page-two))}
    "Go Two"]
   [:button#go-welcome
    {:data-on-click (weave/handler [test-views]
                      (view/render test-views :welcome))}
    "Go Welcome"]
   (view/render test-views)])

;; Tests

(deftest default-view-on-empty-path-test
  (with-browser nav-test-view weave-options
    (testing "Default view renders when URL has no encoded path"
      (visible? :welcome)
      (is (= "Select a page" (el-text :welcome))))))

(deftest navigation-updates-view-test
  (with-browser nav-test-view weave-options
    (testing "Clicking navigation updates the view"
      (visible? :go-one)

      (click :go-one)
      (e/wait-predicate #(visible? :page-one))
      (is (= "Page One Content" (el-text :page-one)))

      (click :go-two)
      (e/wait-predicate #(visible? :page-two))
      (is (= "Page Two Content" (el-text :page-two))))))

(deftest page-reload-preserves-view-test
  (with-browser nav-test-view weave-options
    (testing "Page reload preserves the current view from URL"
      (visible? :go-one)

      (click :go-one)
      (e/wait-predicate #(visible? :page-one))
      (is (= "Page One Content" (el-text :page-one)))

      (e/refresh *browser*)

      (visible? :page-one)
      (is (= "Page One Content" (el-text :page-one))))))

(deftest corrupt-url-falls-back-to-default-test
  (with-browser nav-test-view weave-options
    (testing "Corrupt encoded URL falls back to default view"
      (visible? :welcome)

      ;; Navigate to a corrupt hash
      (e/js-execute
       *browser* "window.location.hash = '#/corrupt-garbage-data'")
      (e/refresh *browser*)

      ;; Should show default view, not crash
      (visible? :welcome)
      (is (= "Select a page" (el-text :welcome))))))

(deftest unknown-view-id-renders-nil-test
  (with-browser nav-test-view weave-options
    (testing "Unknown view ID in URL falls back to default"
      (visible? :welcome)

      ;; Navigate to page-one first
      (click :go-one)
      (e/wait-predicate #(visible? :page-one))

      ;; Manually set hash to encoded unknown view
      (e/js-execute *browser*
                    (str "window.location.hash = '" (view/href test-views :nonexistent) "'"))
      (e/refresh *browser*)

      ;; Should show default since :nonexistent has no render fn
      (visible? :welcome))))

(deftest href-generates-valid-url-test
  (testing "href generates URL that can be decoded"
    (binding [weave/*app-path* ""]
      (let [href-url (view/href test-views :page-one {:id 123})]
        (is (string? href-url))
        (is (.startsWith href-url "#/"))))))

(deftest href-default-view-produces-clean-url-test
  (testing "href for default view produces clean URL"
    (binding [weave/*app-path* ""]
      (let [href-url (view/href test-views :welcome)]
        (is (= "#/" href-url))))))

(deftest default-view-removes-from-url-test
  (with-browser nav-test-view weave-options
    (testing "Navigating to default view clears URL"
      (visible? :go-one)

      ;; Navigate to page-one
      (click :go-one)
      (e/wait-predicate #(visible? :page-one))

      ;; Navigate back to default
      (click :go-welcome)
      (e/wait-predicate #(visible? :welcome))

      ;; URL should be clean
      (let [url (e/get-url *browser*)]
        (is (or (.endsWith url "#/")
                (.endsWith url "#")
                (not (.contains url "#"))))))))

;; Test view/path and render with params

(defn param-view [views]
  (let [[_view-id {:keys [id]}] (view/path views)]
    [:div#param-view
     [:span#param-id (str id)]]))

(def param-views
  (-> (view/new {:id :param-content
                 :default :param-view})
      (view/add {:id :param-view
                 :render #'param-view})))

(defn param-test-view []
  [:div#view
   [:button#go-with-params
    {:data-on-click (weave/handler [param-views]
                      (view/render param-views :param-view {:id 42}))}
    "Go With Params"]
   (view/render param-views)])

(deftest render-with-params-test
  (with-browser param-test-view weave-options
    (testing "render with params makes them accessible via view/path"
      (visible? :go-with-params)

      (click :go-with-params)

      (e/wait-predicate #(= "42" (el-text :param-id)))
      (is (= "42" (el-text :param-id))))))

(deftest params-preserved-on-reload-test
  (with-browser param-test-view weave-options
    (testing "params are preserved in URL and accessible after reload"
      (visible? :go-with-params)

      (click :go-with-params)
      (e/wait-predicate #(= "42" (el-text :param-id)))

      (e/refresh *browser*)

      (visible? :param-id)
      (is (= "42" (el-text :param-id))))))

;; Test accessing nested signals via weave/*signals*

(defn signal-access-view [_]
  (let [{:keys [value]} (:nested weave/*signals*)]
    [:div#signal-access
     [:span#signal-value (str value)]]))

(def signal-access-views
  (-> (view/new {:id :signal-access-content
                 :default :signal-access-view})
      (view/add {:id :signal-access-view
                 :signals {:value 0}
                 :render #'signal-access-view})))

(defn signal-access-test-view []
  [:div#view
   {:data-signals-nested.value "123"}
   [:button#read-signal
    {:data-on-click (weave/handler [signal-access-views]
                      (weave/push-html! (view/render signal-access-views)))}
    "Read Signal"]
   (view/render signal-access-views)])

(deftest access-nested-signals-test
  (with-browser signal-access-test-view weave-options
    (testing "nested signals accessible via weave/*signals* in handlers"
      (visible? :read-signal)

      (click :read-signal)

      (e/wait-predicate #(= "123" (el-text :signal-value)))
      (is (= "123" (el-text :signal-value))))))

;; Unit test for no default - returns nil
(deftest no-default-returns-nil-test
  (testing "No default configured returns nil"
    (let [app (-> (view/new {:id :content})
                  (view/add {:id :page-one
                             :render #'page-one-view}))]
      (binding [weave/*app-path* ""]
        (is (nil? (view/render app)))))))

(def signal-views
  (-> (view/new {:id :signal-content
                 :default :search-view})
      (view/add {:id :search-view
                 :signals {:count 99}
                 :render (fn [_] [:div "Search View"])})))

(defn signal-test-view []
  [:div#view
   {:data-signals-search-view.count "5"}
   [:button#reset-signals
    {:data-on-click (weave/handler [signal-views]
                      (view/reset-signals! signal-views :search-view))}
    "Reset Signals"]
   [:input#count-input {:data-bind-search-view.count true}]])

(deftest reset-signals-test
  (with-browser signal-test-view weave-options
    (testing "reset-signals! resets view signals to namespaced defaults"
      (visible? :reset-signals)

      ;; Initial value from data-signals
      (is (= "5" (e/get-element-value *browser* {:id :count-input})))

      ;; Reset signals
      (click :reset-signals)

      ;; Should now show default value from registry
      (e/wait-predicate #(= "99" (e/get-element-value *browser* {:id :count-input})))
      (is (= "99" (e/get-element-value *browser* {:id :count-input}))))))

;; Multi-app tests

(defn outer-one-view [_]
  [:div#outer-one "Outer One"])

(defn outer-two-view [_]
  [:div#outer-two "Outer Two"])

(defn inner-one-view [_]
  [:div#inner-one "Inner One"])

(defn inner-two-view [_]
  [:div#inner-two "Inner Two"])

(def outer-views
  (-> (view/new {:id :outer
                 :default :outer-one})
      (view/add {:id :outer-one
                 :render #'outer-one-view})
      (view/add {:id :outer-two
                 :render #'outer-two-view})))

(def inner-views
  (-> (view/new {:id :inner
                 :default :inner-one})
      (view/add {:id :inner-one
                 :render #'inner-one-view})
      (view/add {:id :inner-two
                 :render #'inner-two-view})))

(defn multi-app-test-view []
  [:div#view
   [:button#outer-two-btn
    {:data-on-click (weave/handler [outer-views]
                      (view/render outer-views :outer-two))}
    "Outer Two"]
   [:button#inner-two-btn
    {:data-on-click (weave/handler [inner-views]
                      (view/render inner-views :inner-two))}
    "Inner Two"]
   [:button#outer-default-btn
    {:data-on-click (weave/handler [outer-views]
                      (view/render outer-views :outer-one))}
    "Outer Default"]
   (view/render outer-views)
   (view/render inner-views)])

(deftest multi-app-independent-navigation-test
  (with-browser multi-app-test-view weave-options
    (testing "Multiple apps navigate independently"
      ;; Both start at defaults
      (visible? :outer-one)
      (visible? :inner-one)

      ;; Change inner app
      (click :inner-two-btn)
      (e/wait-predicate #(visible? :inner-two))
      (is (= "Inner Two" (el-text :inner-two)))
      ;; Outer unchanged
      (is (= "Outer One" (el-text :outer-one)))

      ;; Change outer app
      (click :outer-two-btn)
      (e/wait-predicate #(visible? :outer-two))
      (is (= "Outer Two" (el-text :outer-two)))
      ;; Inner unchanged
      (is (= "Inner Two" (el-text :inner-two))))))

(deftest multi-app-preserves-state-on-reload-test
  (with-browser multi-app-test-view weave-options
    (testing "Multi-app state preserved on reload"
      (visible? :outer-one)

      ;; Navigate both apps
      (click :outer-two-btn)
      (e/wait-predicate #(visible? :outer-two))
      (click :inner-two-btn)
      (e/wait-predicate #(visible? :inner-two))

      ;; Reload
      (e/refresh *browser*)

      ;; Both should be preserved
      (visible? :outer-two)
      (visible? :inner-two)
      (is (= "Outer Two" (el-text :outer-two)))
      (is (= "Inner Two" (el-text :inner-two))))))

(deftest multi-app-default-clears-from-url-test
  (with-browser multi-app-test-view weave-options
    (testing "Navigating to default clears only that app from URL"
      (visible? :outer-one)

      ;; Navigate both away from defaults
      (click :outer-two-btn)
      (e/wait-predicate #(visible? :outer-two))
      (click :inner-two-btn)
      (e/wait-predicate #(visible? :inner-two))

      ;; Navigate outer back to default
      (click :outer-default-btn)
      (e/wait-predicate #(visible? :outer-one))

      ;; Reload - outer should be at default, inner preserved
      (e/refresh *browser*)
      (visible? :outer-one)
      (visible? :inner-two)
      (is (= "Outer One" (el-text :outer-one)))
      (is (= "Inner Two" (el-text :inner-two))))))
