# Components

Weave provides a collection of pre-styled UI components through the
`weave.components` namespace. These components help you build
consistent, attractive interfaces without writing extensive CSS or
HTML markup.

## Overview

The components namespace offers themed, pre-styled UI elements that
follow modern design principles. These components are built on top of
Tailwind CSS and are designed to work seamlessly with Weave's
server-side rendering approach.

## Usage

To use Weave components, require the namespace in your application:

```clojure
(ns your-app.core
  (:require [weave.components :as c]))
```

Then use the components in your views with the namespace keyword syntax:

```clojure
(defn my-view []
  [::c/view#app
   [::c/card
    [:h1.text-2xl "Hello World"]
    [::c/button {:variant :primary} "Click Me"]]])
```

## Component Customization

Most components accept options as a map of attributes:

```clojure
[::c/button 
 {:variant :primary    ; Visual style
  :size :lg            ; Size variant
  :disabled true       ; State
  :class "mt-4"}       ; Additional classes
 "Submit"]
```

## Work in Progress

The components namespace is a work in progress. New components are
being added regularly, and existing components may evolve based on
user feedback and best practices.

Future plans include:
- More specialized components for data visualization
- Enhanced theming capabilities
- Responsive design improvements
- Accessibility enhancements

For the most up-to-date information on available components, refer to
the source code or examples in the demo application.
