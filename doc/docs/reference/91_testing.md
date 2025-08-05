# Testing Applications

Weave provides a dedicated browser testing component that simplifies
end-to-end testing of Weave applications. The component integrates
with [Etaoin](https://github.com/clj-commons/etaoin) for browser
automation and provides convenience functions for common testing
operations.

## Setup

### Adding the Test Component

Add the browser testing component to your project's `deps.edn`:

```clojure
{:aliases
 {:test {:extra-deps {weave/test {:git/url "https://github.com/nakkaya/weave/"
                                  :deps/root "components/test"}
                      }}}}
```

### Prerequisites

Install ChromeDriver for browser automation:

```bash
# On macOS with Homebrew
brew install chromedriver

# On Ubuntu/Debian
sudo apt-get install chromium-chromedriver

# On other systems, download from:
# https://chromedriver.chromium.org/
```

## Basic Usage

### Import the Testing Functions

```clojure
(ns my-app.test
  (:require [clojure.test :refer [deftest testing is]]
            [weave.test.browser :refer [with-browser visible? click]]))
```

### Writing Your First Test

```clojure
(defn my-test-view []
  [:div
   [:h1 {:id "title"} "Hello, Weave!"]
   [:button {:id "click-me"
             :data-on-click (weave/handler []
                              (weave/push-html!
                                [:h1 {:id "title"} "Clicked!"]))}
    "Click Me"]])

(deftest basic-interaction-test
  (with-browser my-test-view {:http-kit {:port 3333}}
    (testing "button click updates title"
      (visible? :title)
      (is (= "Hello, Weave!" (el-text :title)))
      (click :click-me)
      (is (= "Clicked!" (el-text :title))))))
```

## Testing Functions

### Core Functions

#### `with-browser`
Macro that sets up a test server and browser driver for testing.

```clojure
(with-browser view-fn server-options
  ;; test body with access to *browser* binding
  )
```

**Parameters:**

- `view-fn` - Function that returns your Hiccup view
- `server-options` - Map of Weave server options (same as `weave.core/run`)

**Features:**

- Automatically starts Weave server
- Launches headless Chrome browser
- Navigates to test URL
- Provides `*browser*` binding for test functions
- Cleans up server and browser after test

#### `visible?` 
Waits for an element to be visible and asserts its presence.

```clojure
(visible? :my-element-id)
```

#### `click`
Clicks an element by ID.

```clojure
(click :button-id)
```

#### `fill`
Fills a form field with a value.

```clojure
(fill :input-field "some text")
```

#### `el-text`
Gets the text content of an element.

```clojure
(is (= "Expected Text" (el-text :element-id)))
```

### Multi-Tab Testing Functions

#### `new-tab`
Opens a new browser tab with the same test URL.

```clojure
(new-tab)
```

#### `tabs`
Returns all browser tab handles.

```clojure
(let [tab-handles (tabs)]
  ;; tab-handles is a vector of tab identifiers
  )
```

#### `switch-tab`
Switches to a specific tab.

```clojure
(let [[tab1 tab2] (tabs)]
  (switch-tab tab2)  ; Switch to second tab
  ;; perform actions in tab2
  (switch-tab tab1)  ; Switch back to first tab
  )
```

## Examples

### Testing Form Interactions

```clojure
(defn contact-form-view []
  [:form
   [:input {:id "name" :type "text" :placeholder "Name"}]
   [:input {:id "email" :type "email" :placeholder "Email"}]
   [:button {:id "submit"
             :data-on-click (weave/handler []
                              (let [{:keys [name email]} (:params weave/*request*)]
                                (weave/push-html! "#result"
                                  [:div {:id "result"}
                                   (str "Hello " name " (" email ")")])))}
    "Submit"]])

(deftest form-submission-test
  (with-browser contact-form-view {:http-kit {:port 3333}}
    (testing "form submission displays result"
      (visible? :name)
      (fill :name "John Doe")
      (fill :email "john@example.com")
      (click :submit)
      (visible? :result)
      (is (= "Hello John Doe (john@example.com)" (el-text :result))))))
```
