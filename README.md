# clj-line

clj-line runs as two processes. An nREPL server that evaluates code,
and a JLine client that provides a terminal prompt. Keeping them
separate keeps the client dependencies off the project classpath.

## Run

The root `deps.edn` aliases compose the classpath for each process:

* `clojure -M:serve` server
* `clojure -M:attach [port]` client reads .nrepl-port if no port is provided
* `clojure -M:repl` a server that spawns a client

## Features

- [x] Multiple lines that are properly indented
- [x] Unmatched brackets are highlighted red
- [x] Tap outputs to JLine's [printAbove](https://jline.org/docs/examples/print-above/)
- [x] Custom prompts
- [ ] History
- [ ] Emacs/Vim editing key commands
- [ ] Custom keybindings
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
