# Hooks

Hooks can change how the server evals code, prints results, and reports errors.
Each hook function is given the default function and returns a new one:

* `:eval-hook`: Given `clojure.core/eval`, return a function of `form`.
* `:print-hook`: Given nREPL's print function, return a function of `value writer options`.
* `:caught-hook`: Given `clojure.main/repl-caught`, return a function of `throwable`.

## Example

Pretty print all results:

```clojure
;; .cljconf/org.clojure/clojure-cli.repl.edn
{:print-hook dev.hooks/pprint-values}
```

```clojure
(ns dev.hooks
  (:require [clojure.pprint :as pprint]))

(defn pprint-values [_default-print]
  (fn [value writer _options]
    (pprint/pprint value writer)))
```
