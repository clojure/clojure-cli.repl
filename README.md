# clojure-cli.repl

A REPL for the Clojure CLI featuring multi-line editing with proper indentation,
bracket highlighting, structural editing, inline eval, doc lookup for Clojure
and Java, a data inspector, configurable prompts, keybindings, and much more.

It runs as two processes. An nREPL server that evaluates code, and a JLine client that
provides a terminal prompt. Separate processes keep the client dependencies
off the project classpath.

## Install

Add these aliases to your user `deps.edn`:

```clojure
{:aliases
 {:repl   {:extra-deps {io.github.clojure/clojure-cli.repl-server {:mvn/version "0.1.0"}}
           :main-opts ["-m" "clojure-cli.repl.server" "repl"]}
  :serve  {:extra-deps {io.github.clojure/clojure-cli.repl-server {:mvn/version "0.1.0"}}
           :main-opts ["-m" "clojure-cli.repl.server"]}
  :attach {:replace-paths []
           :replace-deps {io.github.clojure/clojure-cli.repl-client {:mvn/version "0.1.0"}}
           :jvm-opts ["--enable-native-access=ALL-UNNAMED"]
           :main-opts ["-m" "clojure-cli.repl.client"]}}}
```

The server runs on the project classpath. The client runs in its own JVM with only its own deps.

**Note: Java 17+ and Clojure 1.12+ are required**

## Run

* `clojure -M:repl` starts a server that spawns a client
* `clojure -M:serve` starts a server, writing its port to `.nrepl-port`
* `clojure -M:attach [port]` attaches a client, reading `.nrepl-port` when no port is given

## Example Config

The REPL offers many customization options. An [example config](examples/.cljconf) is provided
as a reference or starting point. Copy it into the project dir, or into
`~/.clojure` for use in all projects:

```
git clone https://github.com/clojure/clojure-cli.repl.git
cp -R clojure-cli.repl/examples/.cljconf .
```

## Demos

The example prompt displays the current ns, the outcome (✓|✗) and duration of the last eval, the server heap, and the wall clock:

![prompt gif](doc/images/prompt.gif)

Eval the form at the cursor without submitting the line:

![inline eval gif](doc/images/eval-at-cursor.gif)

Display Clojure or Java docs for the symbol at the cursor:

![doc gif](doc/images/doc.gif)

Structural editing:

![paredit gif](doc/images/paredit.gif)

Browse the last result, drill into nested data:

![inspector gif](doc/images/inspect.gif)

## Configuration

The REPL is highly customizable. Behavior can be [configured](doc/configuration.md) at the user level, project level, or both.

| Keys | Configure | Docs |
|------|-----------|------|
| `:prompt` | The prompt | [Prompts](doc/prompts.md) |
| `:middleware` | Server data on eval responses | [Middleware](doc/middleware.md) |
| `:keybindings` `:editing-mode` | Keys and widgets | [Keys](doc/keys.md) |
| `:paredit/<op>` `:bracket-pairs` `:eval-form-at-cursor` `:doc-at-cursor` | Editing | [Structural editing](doc/editing.md) |
| `:inspect` | Browse the last result | [Inspector](doc/inspector.md) |
| `:eval-hook` `:print-hook` `:caught-hook` | Server hooks | [Hooks](doc/hooks.md) |
| `:history` `:auto-require` `:port` and dynamic vars | Session behavior | [Configuration](doc/configuration.md) |


## Tests

```
cd client && clojure -M:test
```

## Copyright and License

Copyright © 2026

All rights reserved. The use and
distribution terms for this software are covered by the
[Eclipse Public License 1.0] which can be found in the file
LICENSE at the root of this distribution. By using this software
in any fashion, you are agreeing to be bound by the terms of this
license. You must not remove this notice, or any other, from this
software.

[Eclipse Public License 1.0]: https://opensource.org/license/epl-1-0
