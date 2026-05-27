# clj-line

## Features

* Unmatched brackets are highlighted red.
* New lines are indented one space past the last opening bracket.
* Multiple lines which are numbered.
* Tap outputs to JLine's [printAbove](https://jline.org/docs/examples/print-above/)

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
  (future (Thread/sleep 7000) (tap> [:job-C :done])))

```

## Tests

```
clojure -M:test
```
