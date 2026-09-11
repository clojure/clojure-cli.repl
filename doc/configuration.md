# Configuration

A config file is a flat map of keyword keys:

```clojure
;; .cljconf/org.clojure/clojure-cli.repl.edn
{:prompt dev.prompt/prompt
 :history :project
 :warn-on-reflection true}
```

Configuration is the merger of two EDN files:

* user: `~/.clojure/.cljconf/org.clojure/clojure-cli.repl.edn`
* project: `.cljconf/org.clojure/clojure-cli.repl.edn`, in the project dir

Keys in a project file override those in the user file.

## Example

An [example config](../examples/.cljconf) is provided as a reference or starting point.
Copy it into the project dir, or into `~/.clojure` for use in all projects:

```
git clone https://codeberg.org/JarrodCTaylor/clj-line.git
cp -R clj-line/examples/.cljconf .
```

Once in place run `clojure -M:repl` to try it out.

## Config keys

The scope column names the process a key configures. Either the JLine client or the nREPL server.

| Key | Scope | Value | Effect | Default |
|-----|-------|-------|--------|---------|
| `:prompt` | client | [qualified symbol](#user-code) | Replaces the [prompt](prompts.md). | `ns => ` |
| `:editing-mode` | client | `:vi` or `:emacs` | Editing keybindings. | `:emacs` |
| `:history` | client | `:project` or `:user` | [Where input history persists](#history). | `:user` |
| `:keybindings` | client | [qualified symbol](#user-code) | [Binds custom keys](keys.md). | none |
| `:bracket-pairs` | client | boolean | Auto close `[({` characters. | `false` |
| `:eval-form-at-cursor` | client | [key string](#key-strings) | Eval the form at the cursor, printing the result above the prompt. | none |
| `:doc-at-cursor` | client | [key string](#key-strings) | Doc for the symbol at the cursor, Clojure or Java. | none |
| `:inspect` | client | [key string](#key-strings) | Open the [inspector](inspector.md) on the last result. | none |
| `:paredit/<op>` | client | [key string](#key-strings) | Bind a [structural editing op](editing.md). Example: `:paredit/raise "C-]"`. | none |
| `:auto-require` | client | vector of libspecs | [Required into each namespace](#auto-require). | none |
| `:middleware` | server | vector of [qualified symbols](#user-code) | [Server data on eval responses](middleware.md). | none |
| `:eval-hook` | server | [qualified symbol](#user-code) | [Wraps eval](hooks.md). | none |
| `:print-hook` | server | [qualified symbol](#user-code) | [Wraps result printing](hooks.md). | none |
| `:caught-hook` | server | [qualified symbol](#user-code) | [Wraps error reporting](hooks.md). | none |
| `:port` | server | int | nREPL port. | a free port |
| `:print-length` | server | int | `*print-length*` | `nil` |
| `:print-level` | server | int | `*print-level*` | `nil` |
| `:print-meta` | server | boolean | `*print-meta*` | `false` |
| `:print-namespace-maps` | server | boolean | `*print-namespace-maps*` | `true` |
| `:warn-on-reflection` | server | boolean | `*warn-on-reflection*` | `false` |
| `:unchecked-math` | server | boolean or `:warn-on-boxed` | `*unchecked-math*` | `false` |
| `:assert` | server | boolean | `*assert*` | `true` |
| `:compile-path` | server | string | `*compile-path*` | `"classes"` |

### Key strings

`C-x` is ctrl-x, `M-x` is alt-x, `TAB` and `RET` name themselves, anything else is the literal char.

**NOTE** `M-` keys on macOS only work when your terminal sends Option as Meta.

## History

`:history` specifies whether a `history` file is saved in each `:project`
or in a single global file for the `:user`. Search with `C-r` and
navigate with up/down arrows.

## Auto require

`:auto-require` takes a vector of libspecs:

```clojure
;; .cljconf/org.clojure/clojure-cli.repl.edn
{:auto-require [[clojure.string :as s]
                [clojure.repl :refer [doc source dir]]]}
```

The aliases and refers defined here will always be available. The REPL requires them
in the starting namespace and again in each namespace it enters. Libspecs resolve on
the server classpath, which includes the config's [user code](#user-code) and [dependencies](#dependencies).

## User code

Some configuration keys take a qualified symbol naming a function provided by the user.
That code should live in a `src` dir next to the config file.
For example `dev.prompt/prompt` maps to `.cljconf/org.clojure/clojure-cli.repl/src/dev/prompt.clj`.

## Dependencies

You can place a `deps.edn` next to the config file to put user code
and libraries on the classpath. Entries in `:deps` load in both processes.
The aliases `:clojure-cli.repl/client` and `:clojure-cli.repl/server`
are used to target one process.

```clojure
;; .cljconf/org.clojure/clojure-cli.repl/deps.edn
{:paths ["src"]
 :deps  {org.clojure/data.json {:mvn/version "2.5.2"}}
 :aliases {:clojure-cli.repl/client {:extra-deps {...}}
           :clojure-cli.repl/server {:extra-deps {...}}}}
```
