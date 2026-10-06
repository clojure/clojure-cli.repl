Changelog
===========

* 0.1.1 on Oct 6, 2026
  * Silence JLine restricted method warning on Java 24+ by passing `--enable-native-access=ALL-UNNAMED` to the client JVM (#1)
  * Accept middleware vars that hold a vector of middleware symbols, e.g., `cider.nrepl/cider-middleware` (#2)
  * Note that Clojure 1.12+ is required and print a readable error if it isn't (#3)
  * Skip `^:optional` middleware symbols that are not found (#4)
  * Prevent possible client hangs when `:auto-require` errors
* 0.1.0 on Sep 28, 2026
  * Initial release
