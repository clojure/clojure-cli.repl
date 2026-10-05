# Middleware

`:middleware` takes a vector of qualified symbols, each resolving to nREPL middleware:

```clojure
;; .cljconf/org.clojure/clojure-cli.repl.edn
{:middleware [dev.timing/middleware dev.heap/middleware]}
```

A symbol can also name a var that holds a vector of middleware symbols, such as
`cider.nrepl/cider-middleware`.

Custom middleware you write lives in the `src` dir next to the config file. Any 3rd
party middleware (e.g., cider-nrepl) needs the dep added to the
`:clojure-cli.repl/server` alias, see [Dependencies](configuration.md#dependencies).

```clojure
;; .cljconf/org.clojure/clojure-cli.repl/deps.edn
{:aliases {:clojure-cli.repl/server {:extra-deps {cider/cider-nrepl {:mvn/version "0.62.2"}}}}}
```

A symbol marked `^:optional` is used when its namespace and var are found, and
skipped when they are not. Any unmarked symbol that is not found is an error,
and the REPL will not start.

```clojure
;; .cljconf/org.clojure/clojure-cli.repl.edn
{:middleware [^:optional org.corfield.rephrase.nrepl/wrap-rephrase]}
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

Your map keys share the response with nREPL's own keys, so namespacing them is recommended.
The values must survive nREPL's bencode wire encoding, so use strings or integers.

## Long form nREPL middleware

`assoc-response` covers the common case of adding values after the eval
completes. If that does not meet your needs, `:middleware` accepts any nREPL
middleware. For example, [timing.clj](../examples/.cljconf/org.clojure/clojure-cli.repl/src/dev/timing.clj) must capture the start time before the eval runs.
