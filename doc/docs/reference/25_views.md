# Views

The `weave.view` namespace provides a DSL for defining and switching
between views with URL-based routing. Views are encoded in the URL
hash using Transit-MessagePack, allowing for bookmarkable and
shareable URLs.

## Creating a View Registry

Use `view/new` to create a view registry with an id and optional
default view:

```clojure
(require '[weave.view :as view])

(def views
  (-> (view/new {:id :content
                 :default :welcome})
      (view/add {:id :welcome
                 :render #'welcome-view})
      (view/add {:id :users
                 :render #'users-view})
      (view/add {:id :user-edit
                 :signals {:loading false}
                 :render #'user-edit-view})))
```

### Options

- `:id` - keyword, required
    - Identifies the view registry in the URL
    - Used as the `id` attribute of the container `div` where views
     render
- `:default` - keyword, optional - view to render when URL has no
  encoded path for the view

The rendered output wraps your view in a div with the views id:

```clojure
(def views (view/new {:id :content :default :home}))

;; (view/render views) produces:
[:div {:id :content} (view-renderer)]
```


### View Definition

Each view added with `view/add` takes a map with:

- `:id` - keyword, required - unique identifier for the view
- `:render` - function or var, required - receives the view registry
  as its argument and returns hiccup for the view
- `:signals` - map, optional - default signal values namespaced under
  the view id

## Rendering Views

Call `view/render` with just the view to render based on the current URL:

```clojure
(defn main-view []
  [:div#app
   [:nav
    [:button {:href (view/href views :users)
              :data-on-click (weave/handler [views]
                               (view/render views :users))}
     "Users"]]
   (view/render views)])
```

### Navigating to Views

Call `view/render` with a view id to navigate:

```clojure
;; Navigate to :users view
(view/render views :users)

;; Navigate with params
(view/render views :user-edit {:id 123})
```

This pushes the new URL hash and updates the container element.

## Generating URLs

Use `view/href` to generate URLs for links. The views must be passed
as the first argument:

```clojure
(view/href views :users)
;; => "/#/kpY..."

(view/href views :user-edit {:id 123})
;; => "/#/k5YB..."
```

## Multi-View Support

Multiple view registries can share the same URL, each maintaining
independent state. The URL encodes a map of registry id to
`[view-id params]`:

```clojure
(def sidebar-views
  (-> (view/new {:id :sidebar
                 :default :nav})
      (view/add {:id :nav :render #'nav-view})
      (view/add {:id :settings :render #'settings-view})))

(def content-views
  (-> (view/new {:id :content
                 :default :dashboard})
      (view/add {:id :dashboard :render #'dashboard-view})
      (view/add {:id :users :render #'users-view})))

(defn main-view []
  [:div#app
   (view/render sidebar-views)
   (view/render content-views)])
```

When rendering a non-default view, each registry's state is preserved
in the URL independently. Default views are omitted from the URL to
keep URLs minimal.

### URL Optimization

When a view registry is at its default view with no params, it is
excluded from the URL entirely:

```clojure
;; If sidebar is at :nav (default) and content is at :users
;; Only content state is encoded in URL
(view/href content-views :users)
;; => "/#/kpY..." (encodes {:content [:users {}]})

;; If both are at defaults
;; URL is clean with no encoded state
;; => "/#/"
```

## Signals

Views can define default signals that are namespaced under the view id.
This prevents signal collisions between views.

```clojure
(def views
  (-> (view/new {:id :content})
      (view/add {:id :search
                 :signals {:query "" :page 0}
                 :render #'search-view})))
```

The signals are accessible as `search.query` and `search.page` in
datastar attributes:

```clojure
(defn search-view [_views]
  [:div
   [:input {:data-bind-search.query true
            :placeholder "Search..."}]
   [:span {:data-text "$search.page"}]])
```

### Accessing Signals in Clojure

Since signals are namespaced under the view id, access them via
`weave/*signals*` using the nested structure:

```clojure
(defn search-view [_views]
  (let [{:keys [query page]} (:search weave/*signals*)]
    [:div
     [:p (str "Searching for: " query)]
     [:p (str "Page: " page)]]))
```

### Setting Signals in Clojure

Use `weave/push-signal!` with the nested structure:

```clojure
;; Set a single signal
(weave/push-signal! {:search {:query "hello"}})

;; Set multiple signals
(weave/push-signal! {:search {:query "hello" :page 1}})
```

### Resetting Signals

Use `view/reset-signals!` to reset a view's signals to their defaults:

```clojure
(view/reset-signals! views :search)
;; Pushes {:search {:query "" :page 0}} to client
```

## URL Format

Views are encoded in the URL hash as base64 Transit-MessagePack:

```
/#/<base64-encoded-data>
```

The encoded data is a map of `{views-id [view-id params-map]}`. This
format:

- Supports multiple independent view registries in one URL
- Is compact and URL-safe
- Supports complex params (keywords, nested maps, etc.)
- Is bookmarkable and shareable
- Omits views at their default state for clean URLs

## Dynamic Var

- `view/*path*` - bound during render to `[view-id params-map]`

Use this to access the current view id and params in your render
function:

```clojure
(defn user-edit-view [_views]
  (let [[_view-id {:keys [id]}] view/*path*]
    [:div
     [:h1 (str "Editing User " id)]]))
```

## Complete Example

```clojure
(ns myapp.core
  (:require [weave.core :as weave]
            [weave.view :as view]))

(defn welcome-view [_views]
  [:div.text-center
   [:h1 "Welcome"]
   [:p "Select a page from the navigation"]])

(defn users-view [views]
  [:div
   [:h1 "Users"]
   [:ul
    (for [user @users-atom]
      [:li {:key (:id user)}
       [:a {:href (view/href views :user-edit {:id (:id user)})}
        (:name user)]])]])

(defn user-edit-view [views]
  (let [[_view-id {:keys [id]}] view/*path*]
    [:div
     [:h1 "Edit User"]
     [:p (str "Editing user: " id)]
     [:button {:data-on-click (weave/handler [views]
                                (view/render views :users))}
      "Back to Users"]]))

(def views
  (-> (view/new {:id :content
                 :default :welcome})
      (view/add {:id :welcome
                 :render #'welcome-view})
      (view/add {:id :users
                 :render #'users-view})
      (view/add {:id :user-edit
                 :render #'user-edit-view})))

(defn main-view []
  [:div#app
   [:nav.flex.gap-4.p-4
    [:button
     {:href (view/href views :users)
      :data-on-click (weave/handler [views]
                       (view/render views :users))}
     "Users"]
    [:button
     {:href (view/href views :user-edit {:id 1})
      :data-on-click (weave/handler [views]
                       (view/render views :user-edit {:id 1}))}
     "Edit User 1"]]

   (view/render views)])
```
