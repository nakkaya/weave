# Create a Project

Requirements:

 - Java 11 or higher
 - Clojure
 
 Weave is designed with simplicity in mind, you only need two files to get started.
 
  - `deps.edn`
  - `src/app/core.clj`

Create a file called `deps.edn`,

```clojure
{:paths   ["src"]

 :deps    {org.clojure/clojure {:mvn/version "1.12.0"}
		   weave/core {:git/url "https://github.com/nakkaya/weave/"
					   :git/sha "d9338ac9576a8bb098aa51938c5d3926c487376e"}}

 :aliases {:dev {:exec-fn app.core/run}}}
```

Then in `src/app/core.clj`

```clojure
(ns app.core
  (:require [weave.core :as weave]))

(defn view []
  [:div.p-6
   [:h1#label.text-2xl.font-bold "Hello Weave!"]
   [:button.bg-blue-500.text-white.px-4.py-2.rounded
	{:data-on-click
	 (weave/handler []
	  (weave/push-html!
	   [:h1#label.text-2xl.font-bold "Button was clicked!"]))}
	"CLICK ME"]])

(defn run [_opts]
  (weave/run #'view {}))
```

 Run `clj -X:dev` to start the app on `http://localhost:8080`

## Understanding the Basics

### Markup

Weave uses Chassis DSL for defining HTML elements in Clojure. 

- Elements are represented as Clojure vectors: `[:div "content"]`
- CSS classes are added with dot notation: `[:div.my-class "content"]`
- IDs are added with hash notation: `[:h1#title "Hello"]`
- Attributes are specified in a map: `[:button {:disabled true} "Click"]`
- Tailwind CSS classes work seamlessly: `[:div.p-4.bg-blue-500 "Styled content"]`

### Event Handlers

Weave uses [Datastar](https://data-star.dev/) on the client side for
its reactive event handling system:

- Events are attached with data attributes: `:data-on-click /
  :data-on-load /` etc.
- The `weave/handler` macro creates server-side event handlers
- Handlers can update the DOM, merge one or more fragments using
  `push-html!` or `broadcast-html!`. By default,
  [Datastar](https://data-star.dev/) merges fragments using Idiomorph,
  which matches top level elements based on their ID

### Server-Side Rendering

- The `view` function defines your initial UI state
- All rendering happens on the server
- Updates are pushed to the client via Server-Sent Events (SSE)
