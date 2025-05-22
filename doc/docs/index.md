# Introduction

Weave is an easy-to-use, Clojure-based web application framework.

Weave enables the creation of full-stack web applications in Clojure
including the user interface. It's great for micro web apps,
dashboards, admin interfaces, smart home solutions, and similar use
cases.

## Features

- Browser-based graphical user interface with real-time updates
- Reactive UI with server-sent events (SSE) for instant updates
- Standard GUI elements like labels, buttons, forms, and inputs
- Simple component organization with Hiccup syntax
- Client-side routing with hash-based navigation
- Built-in session management and authentication
- Server-side rendering with client-side interactivity
- Push updates to specific browser tabs or broadcast to all sessions
- GraalVM compatible. See =demo/Dockerfile= for static single
  executable builds
- Automatically generate and serve the needed resources for
  Progressive Web Apps (PWA)

## Why Weave?

Weave combines the simplicity of Hiccup templates with the power of
server-side rendering and real-time updates.  It provides a cohesive
development experience where your UI logic lives alongside your
application code.

Unlike traditional SPAs that require separate frontend and backend
codebases, Weave lets you build interactive web applications using
only Clojure. The server pushes UI updates directly to the browser
using server-sent events, eliminating the need for complex client-side
JavaScript frameworks.
