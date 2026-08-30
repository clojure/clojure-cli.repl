# Keys

`:keybindings` names one function that binds any number of
[key strings](configuration.md#key-strings). Each bound key runs a zero-arg function.

Example: Insert a UUID at the cursor with `C-t`

```clojure
;; .cljconf/org.clojure/clojure-cli.repl.edn
{:keybindings dev.keybindings/install}
```

```clojure
(ns dev.keybindings
  (:require [clojure-cli.repl.api :as api]))

(defn install [reader]
  (api/bind-key reader (api/key-sequence "C-t")
                (api/widget
                  (fn [] (.write (.getBuffer reader) (str (random-uuid)))))))
```

## The api

`api` is the example's alias for `clojure-cli.repl.api`.

### Bind keys

* `(api/key-sequence s)`: Given a [key string](configuration.md#key-strings) `s`, return the characters the terminal sends for the corresponding keys.
* `(api/widget f)`: Given a zero-arg function `f`, return a JLine widget that
  runs it. Any return value of `f` is ignored.
* `(api/bind-key reader keyseq widget)`: Bind the widget to the keyseq.

### Show output

* `(api/print-above segments)`: Given display [`segments`](prompts.md#segments), print them above the
  input line without disturbing in-progress input.
* `(api/show-below segments)`: Given display [`segments`](prompts.md#segments), show them in a pane
  pinned under the input line. Nil segments clear the pane.
* `(api/page title text)`: Given a `title` and `text`, display them full screen.

### Reach the server

* `(api/eval-code code)`: Eval the `code` string on the server in the
  current namespace and return the merged response map (`:value`, `:out`,
  `:err`, `:ex`). This enables a binding in the client to run project code on
  the server.
* `api/session`: An atom holding the nREPL session.

### Read REPL state

* `api/current-ns`, `api/last-response`: Atoms holding the current namespace and last
   eval response for use in [prompts](prompts.md#repl-state).
* `(api/terminal-width)`, `(api/terminal-height)`: Returns the current terminal size.

### JLine

* `api/reader`, `(api/terminal)`: The LineReader (an atom) and the Terminal.
  For anything JLine offers that no helper provides access to, e.g.,
  the cursor shape, built in widgets, buffer styling.
