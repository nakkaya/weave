# Handlers

Handlers are server-side functions that process client-side
events. They are a core part of Weave's reactivity model.

## How Handlers Work

- When you define a handler using the `weave/handler` macro, Weave:
    - Generates a unique route path using a UUID
    - Registers the handler function with that route
    - Returns client-side [Datastar](https://data-star.dev/)
      expression that will invoke this route when triggered

- When a client-side event occurs (like a button click):
    - The browser sends a request to the unique route
    - Weave executes your handler function on the server
    - Your handler can update the DOM, execute scripts, etc.

## Handler Options

- `:auth-required?` - Whether authentication is required (defaults to
  the value of `*secure-handlers*`)
- `:type` - Request content type (use `:form` for form submissions)
- `:selector` - CSS selector for the form to submit (e.g. `"#myform"`)

## Example

```clojure
{:data-on-click
 (weave/handler
  ;; This code runs on the server when the button is clicked
  (weave/push-html! [:div#message "Button clicked!"]))}
```

When this handler is registered, Weave:

 - Creates a unique route like `/a1b2c3d4-e5f6-7890-abcd-ef1234567890`
 - Sets up a POST endpoint for that route
 - Returns client-side code that will POST to that route when the
   click event occurs
