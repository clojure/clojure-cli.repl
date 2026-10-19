Changelog
===========

* 0.2.1 on Oct 19, 2026
  * Add `:echo-editor-code` to show only output from editor evaluations
* 0.2.0 on Oct 19, 2026
  * Show evaluations sent from an editor in the terminal REPL
  * TAB completion using the nREPL completions op
  * Add `:init` to run a function once at server startup (#5)
  * Key strings accept sequences (`C-x d`), stacked modifiers (`C-M-f`), and special keys (`<up>`) (#7)
  * Use faint instead of bright black for secondary text
  * Replace `M-` keys with `C-x` chords in the example config, and add common paredit movements
* 0.1.1 on Oct 6, 2026
  * Silence JLine restricted method warning on Java 24+ by passing `--enable-native-access=ALL-UNNAMED` to the client JVM (#1)
  * Accept middleware vars that hold a vector of middleware symbols, e.g., `cider.nrepl/cider-middleware` (#2)
  * Note that Clojure 1.12+ is required and print a readable error if it isn't (#3)
  * Skip `^:optional` middleware symbols that are not found (#4)
  * Prevent possible client hangs when `:auto-require` errors
* 0.1.0 on Sep 28, 2026
  * Initial release
