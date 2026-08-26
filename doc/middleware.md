# Middleware

The `:middleware` config key names nREPL middleware vars with qualified
symbols:

```clojure
{:middleware [dev.timing/middleware dev.heap/middleware]}
```

## Adding data to responses

`clojure-cli.repl.middleware/assoc-response` is a convenience function to
assoc values onto each eval's response map. The client reads response values
at `clojure-cli.repl.api/last-response`.

```clojure
(ns dev.heap
  (:require [clojure-cli.repl.middleware :as mw]))

(def middleware
  (mw/assoc-response
    (fn []
      (let [rt (Runtime/getRuntime)]
        {:clojure-cli.repl/heap-used (- (.totalMemory rt) (.freeMemory rt))
         :clojure-cli.repl/heap-max (.maxMemory rt)}))))
```

The map's keys share the response with nREPL's own keys, so namespacing them is recommended.
The values must survive nREPL's bencode wire encoding, so use strings or integers.

## Long form nREPL middleware

`assoc-response` covers the common case of adding values after the eval
completes. If that does not meet your needs, `:middleware` accepts any nREPL
middleware. For example, [timing.clj](../examples/.cljconf/org.clojure/clojure-cli.repl/src/dev/timing.clj) must capture the start time before the eval runs.
