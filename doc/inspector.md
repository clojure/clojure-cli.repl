# Inspector

The `:inspect` config key binds a key to open the inspector on the last result `*1`:

```clojure
;; .cljconf/org.clojure/clojure-cli.repl.edn
{:inspect "M-i"}
```

The top line shows the traversal path and the type of the current value.
The body lists the entries as `key` · `value` or as a table when the entries are uniform.
The footer is context-aware and displays the available commands.

## datafy and nav

The inspector shows the `datafy` of the current value and opens an entry with `nav`.
Extend [`datafy` and `nav`](https://clojure.github.io/clojure/clojure.datafy-api.html) for your own types, and the inspector navigates their values as data.

## Metadata

The footer shows `m meta` when the current value has metadata. Pressing `m`
displays the metadata as a value you can navigate.

## Define a value

Pressing `d` will def the current value in the current namespace.
The new var carries `:browse-path` metadata containing the keys from `*1` to the value.
