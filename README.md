# clj-line

clj-line runs as two processes. An nREPL server that evaluates code,
and a JLine client that provides a terminal prompt. Keeping them
separate keeps the client dependencies off the project classpath.

## Local Install Usage

Until it's released, add these aliases to your user `deps.edn` to run locally:

```clojure
{:aliases
 {:repl   {:replace-paths [] :main-opts ["-m" "clj-line.server.main" "repl"]
           :replace-deps {clj-line/server {:git/url "https://codeberg.org/JarrodCTaylor/clj-line.git"
                                            :git/sha "030b2c41aaa4574e9bb9970bfe4273fe667f9484" :deps/root "server"}}}
  :attach {:replace-paths [] :main-opts ["-m" "clj-line.client.main"]
           :replace-deps {clj-line/client {:git/url "https://codeberg.org/JarrodCTaylor/clj-line.git"
                                            :git/sha "030b2c41aaa4574e9bb9970bfe4273fe667f9484" :deps/root "client"}}}
  :serve  {:replace-paths [] :main-opts ["-m" "clj-line.server.main"]
           :replace-deps {clj-line/server {:git/url "https://codeberg.org/JarrodCTaylor/clj-line.git"
                                            :git/sha "030b2c41aaa4574e9bb9970bfe4273fe667f9484" :deps/root "server"}}}}}
```

## Run

The root `deps.edn` aliases compose the classpath for each process:

* `clojure -M:serve` server
* `clojure -M:attach [port]` client reads .nrepl-port if no port is provided
* `clojure -M:repl` a server that spawns a client

## Configuration

A flat EDN map, merged from a user file and overridable with a project file:

* user: `~/.clojure/.cli-config/org.clojure/clj-line.edn`
* project: `.cli-config/org.clojure/clj-line.edn`, in the project dir

Options whose value is a symbol point to user provided code, loaded
from a `src` dir next to the config. For example,
`my-prompt/prompt` is loaded from `.cli-config/org.clojure/clj-line/src/my_prompt.clj`.

| Key | Scope | Value | Effect (default) |
|-----|-------|-------|------------------|
| `:prompt` | client | qualified symbol | Fn returning prompt segments.            |
| `:editing-mode` | client | `:vi` | Vi keybindings (emacs). |
| `:history` | client | `:project` or `:user` | History scope (`:user`).         |
| `:keybindings` | client | qualified symbol | Fn `(f reader)` binding keys. |
| `:middleware` | server | vector of qualified symbols | nREPL middleware.                      |
| `:eval-hook` | server | qualified symbol | Wraps eval: `(f eval)` returns the eval fn.     |
| `:port` | server | int | nREPL port (OS-assigned). |

## Features

- [x] Multiple lines that are properly indented
- [x] Unmatched brackets are highlighted red
- [x] Tap outputs to JLine's [printAbove](https://jline.org/docs/examples/print-above/)
- [x] Custom prompts
- [x] History
- [x] Emacs/Vim editing key commands
- [x] Custom keybindings
- [ ] Auto require/import
- [ ] Print var defaults (`*print-level*`, `*print-length*`, `*print-namespace-maps*`, etc)
- [ ] Customized exception printer function
- [ ] Read, Eval, Print, Caught hooks
- [ ] Bracket pairs & structural-editing
- [ ] Docs
- [ ] Secondary prompt (format and content of continuation line, line numbers, colors)
- [ ] Pretty-print toggle
- [ ] Tap: (on/off, tap out text, filter pred)
- [ ] Auto indent, column alignment (let columns and other things people align)
- [ ] Inline tab completion of symbols (function or class)
- [ ] REPL commands like :prompt (custom?)
- [ ] Status line (on/off)
- [ ] Input value coloring by type
- [ ] Output value coloring by type
- [ ] Indentation rules (basic alignment after each new bracket context)
- [ ] Inline eval of form under cursor output to tap above

## Tests

```
cd client && clojure -M:test
```
