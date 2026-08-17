# clojure-cli.repl

clojure-cli.repl runs as two processes. An nREPL server that evaluates code,
and a JLine client that provides a terminal prompt. Keeping them
separate keeps the client dependencies off the project classpath.

## Local Install Usage

Until it's released, add these aliases to your user `deps.edn` to run locally:

```clojure
{:aliases
 {:repl   {:replace-paths [] :main-opts ["-m" "clojure-cli.repl.server" "repl"]
           :replace-deps {clojure-cli.repl/server {:git/url "https://codeberg.org/JarrodCTaylor/clj-line.git"
                                            :git/sha "edfcf8995f43be7ee41114935673bcd87ae217db" :deps/root "server"}}}
  :attach {:replace-paths [] :main-opts ["-m" "clojure-cli.repl.client"]
           :replace-deps {clojure-cli.repl/client {:git/url "https://codeberg.org/JarrodCTaylor/clj-line.git"
                                            :git/sha "edfcf8995f43be7ee41114935673bcd87ae217db" :deps/root "client"}}}
  :serve  {:replace-paths [] :main-opts ["-m" "clojure-cli.repl.server"]
           :replace-deps {clojure-cli.repl/server {:git/url "https://codeberg.org/JarrodCTaylor/clj-line.git"
                                            :git/sha "edfcf8995f43be7ee41114935673bcd87ae217db" :deps/root "server"}}}}}
```

## Run

The root `deps.edn` aliases compose the classpath for each process:

* `clojure -M:serve` server
* `clojure -M:attach [port]` client reads .nrepl-port if no port is provided
* `clojure -M:repl` a server that spawns a client

## Configuration

A flat EDN map, merged from a user file and overridable with a project file:

* user: `~/.clojure/.cljconf/org.clojure/clojure-cli.repl.edn`
* project: `.cljconf/org.clojure/clojure-cli.repl.edn`, in the project dir

Options whose value is a symbol point to user provided code, loaded
from a `src` dir next to the config. For example,
`my-prompt/prompt` is loaded from `.cljconf/org.clojure/clojure-cli.repl/src/my_prompt.clj`.

A `deps.edn` in the config dir puts that code and its libraries on the
classpath. Items in `:deps` are included in both processes.
Use aliases `:clojure-cli.repl/client` or `:clojure-cli.repl/server` for more precise targeting.

```clojure
{:paths ["src"]
 :deps  {org.clojure/data.json {:mvn/version "2.5.2"}}          ; included in both client and server
 :aliases {:clojure-cli.repl/client {:extra-deps {...}}                 ; prompts, keybindings
           :clojure-cli.repl/server {:extra-deps {criterium/criterium {:mvn/version "0.4.6"}}}}}
```


| Key | Scope | Value | Effect (default) |
|-----|-------|-------|------------------|
| `:prompt` | client | qualified symbol | Fn returning prompt segments.            |
| `:editing-mode` | client | `:vi` | Vi keybindings (emacs). |
| `:history` | client | `:project` or `:user` | History scope (`:user`).         |
| `:keybindings` | client | qualified symbol | Fn `(f reader)` binding keys. |
| `:bracket-pairs` | client | boolean | Auto close `[({` characters (`false`). |
| `:eval-form-at-cursor` | client | key string | Eval the form at the cursor printing result above the prompt. |
| `:paredit/<op>` | client | key string | Bind a structural editing op. Example: `:paredit/raise "M-r"`. |
| `:auto-require` | client | vector of libspecs | Required into each namespace. |
| `:middleware` | server | vector of qualified symbols | nREPL middleware.                      |
| `:eval-hook` | server | qualified symbol | Wraps eval: `(f eval)` returns the eval fn.     |
| `:print-hook` | server | qualified symbol | Wraps result printing: `(f print)` returns a fn with signature `[value writer options]`. |
| `:caught-hook` | server | qualified symbol | Wraps error reporting: `(f caught)` returns a fn with signature `[throwable]`. |
| `:port` | server | int | nREPL port (OS-assigned). |
| `:print-length` | server | int | `*print-length*` (`nil`). |
| `:print-level` | server | int | `*print-level*` (`nil`). |
| `:print-meta` | server | boolean | `*print-meta*` (`false`). |
| `:print-namespace-maps` | server | boolean | `*print-namespace-maps*` (`true`). |
| `:warn-on-reflection` | server | boolean | `*warn-on-reflection*` (`false`). |
| `:unchecked-math` | server | boolean or `:warn-on-boxed` | `*unchecked-math*` (`false`). |
| `:assert` | server | boolean | `*assert*` (`true`). |
| `:compile-path` | server | string | `*compile-path*` (`"classes"`). |

Key strings: `M-x` is alt-x, `C-x` is ctrl-x, `TAB` and `RET` name themselves,
anything else is the literal char.

Paredit ops: `:paredit/slurp-forward` `:paredit/slurp-forward-fully`
`:paredit/slurp-backward` `:paredit/slurp-backward-fully`
`:paredit/barf-forward` `:paredit/barf-backward` `:paredit/splice`
`:paredit/splice-killing-forward` `:paredit/splice-killing-backward`
`:paredit/raise` `:paredit/kill` `:paredit/split` `:paredit/join`
`:paredit/move-to-prev` `:paredit/wrap-list` `:paredit/wrap-vector`
`:paredit/wrap-map` `:paredit/wrap-set`

## Features

- [x] Multiple lines that are properly indented
- [x] Unmatched brackets are highlighted red
- [x] Tap outputs to JLine's [printAbove](https://jline.org/docs/examples/print-above/)
- [x] Custom prompts
- [x] History
- [x] Emacs/Vim editing key commands
- [x] Custom keybindings
- [x] Auto require
- [x] Dynamic var config (`*print-level*`, `*print-length*`, `*warn-on-reflection*`, etc)
- [x] Print and Caught hooks (Read is the client's parser, reachable via `:keybindings`)
- [x] Bracket pairs & structural-editing
- [x] Inline eval of form at cursor output above the prompt
- [ ] Docs
- [ ] Inline tab completion of symbols (function or class)
- [ ] Tap: (on/off, tap out text, filter pred)
- [ ] REPL commands like :prompt (custom?)
- [ ] Status line (on/off)
- [ ] -Secondary prompt (format and content of continuation line, line numbers, colors)-
- [ ] -Input value coloring by type-
- [ ] -Output value coloring by type-

## Tests

```
cd client && clojure -M:test
```
