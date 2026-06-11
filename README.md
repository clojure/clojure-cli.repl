# clj-line

## Implemented Features

* Multiple lines which are numbered.
* New lines are indented one space past the last opening bracket.
* Unmatched brackets are highlighted red.
* Tap outputs to JLine's [printAbove](https://jline.org/docs/examples/print-above/)

## Planned Features

| org.clojure/cli.repl feature                                                                                  | Priority  |
| -------------------------------------------------------------------------------------- | --------- |
| History                                                                                | Must have |
| Emacs/Vim editing key commands                                                         | Must have |
| Custom keybindings                                                                     | High      |
| Auto require/import                                                                    | High      |
| Print var defaults (`*print-level*`, `*print-length*`, `*print-namespace-maps*`, etc)  | High      |
| Customized exception printer function                                                  | High      |
| Read, Eval, Print, Caught hooks                                                        | High      |
| Bracket pairs & structural-editing                                                     | Medium    |
| Docs                                                                                   | Medium    |
| Colored prompt text                                                                    | Medium    |
| Prompt text derived from function output (Git, Current NS, Docker, etc)                | Medium    |
| Secondary prompt (format and content of continuation line, line numbers, colors)       | Medium    |
| Pretty-print toggle                                                                    | Medium    |
| Tap: (on/off, tap out text, filter pred)                                               | Medium    |
| Auto indent, column alignment (let columns and other things people align)              | Medium    |
| Inline tab completion of symbols (function or class)                                   | Medium    |
| REPL commands like :prompt (custom?)                                                   | Medium    |
| Status line (on/off)                                                                   | Low       |
| Input value coloring by type                                                           | Low       |
| Output value coloring by type                                                          | Low       |
| Indentation rules (basic alignment after each new bracket context)                     | Low       |
| Inline eval of form under cursor output to tap above                                   | Low       |

## Run

```
clojure -M:repl
```

## Demo

Tap printing above the active input line without scrambling the text

```clojure
(do
  (future (Thread/sleep 1000) (tap> [:job-A :done]))
  (future (Thread/sleep 5500) (tap> [:job-B :done]))
  (future (Thread/sleep 7000) (tap> [:job-C :done]))
  (future (Thread/sleep 10000) (tap> [:job-D :done])))

```

## Tests

```
clojure -M:test
```
