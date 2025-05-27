# Bundling Resources

Weave comes bundled with several frontend resources to make building
web applications easier. These resources are served from the classpath
and are available to your application without any additional
configuration.

## Bundled Resources

### Tailwind CSS

[Tailwind CSS](https://tailwindcss.com/) v3.4.16 is included, it
provides a utility-first CSS framework. You can use Tailwind classes
directly in your Hiccup markup:

```clojure
[:div.flex.items-center.justify-between.p-4.bg-white.shadow
 [:h1.text-xl.font-bold "My Application"]
 [:button.px-4.py-2.bg-blue-500.text-white.rounded.hover:bg-blue-600
  "Click Me"]]
```

### Datastar

[Datastar](https://data-star.dev/) v1.0.0-beta.11 is included for
client-side reactivity and event handling. Weave uses Datastar
internally for its event system and server-sent events (SSE)
communication.

### Squint

[Squint](https://github.com/squint-cljs/squint) v0.8.147 is included
for JavaScript interoperability, allowing you to write Clojure code
that gets transpiled to JavaScript for client-side execution.

### Heroicons

Weave includes [Heroicons](https://heroicons.com/) as an SVG sprite,
making it easy to use these popular icons in your application. The
icons are accessible through the `::icon` component in the
`weave.components` namespace:

```clojure
(ns your-app.core
  (:require [weave.components :as c]))

(defn view []
  [:div
   [::c/icon#solid-home {:class "h-6 w-6 text-blue-500"}]
   [::c/icon#solid-user {:class "h-6 w-6 text-green-500"}]
   [::c/icon#solid-cog {:class "h-6 w-6 text-gray-500"}]])
```

The icon ID format is `#[style]-[name]` where:

- `style` is either `solid` or `outline`
- `name` is the icon name from Heroicons (e.g., `home`, `user`, `cog`)

You can customize the size and color of icons using Tailwind classes.

## Serving Custom Resources

You can serve your own static resources by placing them in the
`resources/public` directory of your project. Files in this directory
will be automatically served at the root path of your application.

For example:

```
your-project/
├── resources/
│   └── public/
│       ├── css/
│       │   └── custom.css
│       ├── js/
│       │   └── app.js
│       └── images/
│           └── logo.png
```

These files would be accessible at:

- `http://localhost:8080/css/custom.css`
- `http://localhost:8080/js/app.js`
- `http://localhost:8080/images/logo.png`

## Using Custom Resources

To use your custom resources in your application:

```clojure
(weave/run view-fn
  {:head
   [[:link {:rel "stylesheet" :href "/css/custom.css"}]
    [:script {:src "/js/app.js"}]]})
```

```clojure
(defn view []
  [:div
   [:img {:src "/images/logo.png" :alt "Logo"}]])
```
