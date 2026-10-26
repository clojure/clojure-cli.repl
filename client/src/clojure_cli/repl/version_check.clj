(ns clojure-cli.repl.version-check)

(when (< (:minor *clojure-version*) 12)
  (println "clojure-cli.repl client requires Clojure 1.12 or later, started with" (clojure-version))
  (System/exit 1))
