# Structural editing

Structural editing operates on whole forms. You can pull a form into another,
push a form out, wrap, split, raise, and the brackets will be kept balanced for you.
Each available op has a config key that binds it to a [key string](configuration.md#key-strings):

```clojure
;; .cljconf/org.clojure/clojure-cli.repl.edn
{:paredit/slurp-forward "C-t"
 :paredit/raise "C-]"}
```

| Op | Effect |
|----|--------|
| `:paredit/slurp-forward` | Pull the next form into the enclosing form. |
| `:paredit/slurp-forward-fully` | Pull all following forms into the enclosing form. |
| `:paredit/slurp-backward` | Pull the previous form into the enclosing form. |
| `:paredit/slurp-backward-fully` | Pull all preceding forms into the enclosing form. |
| `:paredit/barf-forward` | Push the last form out of the enclosing form. |
| `:paredit/barf-backward` | Push the first form out of the enclosing form. |
| `:paredit/splice` | Remove the brackets of the form at the cursor. |
| `:paredit/splice-killing-forward` | Splice the enclosing form, deleting the form at the cursor and everything after it. |
| `:paredit/splice-killing-backward` | Splice the enclosing form, deleting everything before the cursor. |
| `:paredit/raise` | Replace the enclosing form with the form at the cursor. |
| `:paredit/kill` | Delete the form at the cursor and everything after it in the enclosing form. |
| `:paredit/split` | Split the enclosing form at the cursor. |
| `:paredit/join` | Join the forms left and right of the cursor. |
| `:paredit/move-to-prev` | Move the form at the cursor to its previous depth-first position. |
| `:paredit/wrap-list` | Wrap the form at the cursor in `()`. |
| `:paredit/wrap-vector` | Wrap the form at the cursor in `[]`. |
| `:paredit/wrap-map` | Wrap the form at the cursor in `{}`. |
| `:paredit/wrap-set` | Wrap the form at the cursor in `#{}`. |
