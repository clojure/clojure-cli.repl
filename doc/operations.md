# Operations

The client is designed to be fully decoupled from the server.
Any runtime that implements a compliant nREPL server can be
interacted with through the client repl.

The operations documented here are specific to clojure-cli.repl. A server needs
to implement them in addition to the standard nREPL operations `eval`, `clone`,
and `interrupt`, which the [nREPL operations reference](https://nrepl.org/nrepl/ops.html) documents.

## doc

Documentation for a symbol.

Request:

| Key | Value |
|-----|-------|
| `:op` | `"doc"` |
| `:sym` | the symbol name |
| `:ns` | the namespace to resolve `:sym` in |

Reply:

| Key | Value |
|-----|-------|
| `:doc` | the documentation text |

```clojure
;; request
{:op "doc" :sym "map" :ns "user"}
;; reply
{:doc "clojure.core/map\n..." :status ["done"]}
```

## auto-require

Require libspecs in a namespace. The client sends it on startup and on each namespace change.

Request:

| Key | Value |
|-----|-------|
| `:op` | `"auto-require"` |
| `:libspecs` | the libspecs to require, as an EDN string |
| `:ns` | the namespace to require them in |

Reply:

| Key | Value |
|-----|-------|
| `:error` | the failure message, present only when a libspec fails to load |

```clojure
;; request
{:op "auto-require" :libspecs "[[clojure.string :as s]]" :ns "user"}
;; reply
{:status ["done"]}
```

## inspect

The server is responsible for holding and modifying the state of the inspector.
Each operation from the client is a request for the server to change the state.
The server replies with a view for the client to render.

### inspect-start

Start the inspector on `*1` of the requesting session.

Request:

| Key | Value |
|-----|-------|
| `:op` | `"inspect-start"` |
| `:rows` | the number of entries in a page |
| `:cols` | the terminal width |

### inspect-in

Enter the entry at `:idx`.

Request:

| Key | Value |
|-----|-------|
| `:op` | `"inspect-in"` |
| `:idx` | the index of an entry |

### inspect-out

Return to the enclosing value.

Request:

| Key | Value |
|-----|-------|
| `:op` | `"inspect-out"` |

### inspect-page

Move the page by `:delta`.

Request:

| Key | Value |
|-----|-------|
| `:op` | `"inspect-page"` |
| `:delta` | a number of pages, forward or backward |

### inspect-toggle-meta

Show or hide the metadata.

Request:

| Key | Value |
|-----|-------|
| `:op` | `"inspect-toggle-meta"` |
| `:cursor` | the selected row |

### inspect-toggle-table

Switch between a list and a table.

Request:

| Key | Value |
|-----|-------|
| `:op` | `"inspect-toggle-table"` |

### inspect-def

Define the current value as a var named `:name` in the namespace of the requesting session.

Request:

| Key | Value |
|-----|-------|
| `:op` | `"inspect-def"` |
| `:name` | a var name |

### Reply

The client expects the response of every operation to provide `:view` as an EDN string.
The server formats the text that the client renders. A view is a collection or a leaf.

Every view has these keys:

| Key | Value |
|-----|-------|
| `:path` | the path to the current value |
| `:info` | the type, with the item count for a collection |
| `:header` | the table column header, or `""` |
| `:meta-action` | `"meta"`, `"hide"`, or `nil`, the metadata toggle offered in the footer |

A collection adds its entries:

| Key | Value |
|-----|-------|
| `:start` | the index of the first entry shown |
| `:total` | the number of entries |
| `:entries` | the entries shown, each a string |
| `:alt-view` | `"list"`, `"table"`, or `nil`, the layout toggle offered in the footer |

A leaf adds the value:

| Key | Value |
|-----|-------|
| `:leaf` | the value as a string |

Some operations add a key:

| Key | Added by | Value |
|-----|----------|-------|
| `:cursor` | `inspect-out`, `inspect-toggle-meta` | the row to select |
| `:defd` | `inspect-def` | the printed var it created |
| `:note` | any operation | a message, usually an error |

```clojure
;; request
{:op "inspect-start" :rows 40 :cols 80}
;; reply :view after parsing the edn
{:path "*1"
 :info "PersistentArrayMap · 3 items"
 :header ""
 :meta-action nil
 :start 0
 :total 3
 :entries [":a · 1"
           ":items · [{:id 1, :qty 4} {:id 2, :qty 8}]"
           ":note · \"hello inspector\""]
 :alt-view nil}
```
