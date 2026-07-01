(ns clj-line.api
  "Access to the running clj-line REPL's line reader and terminal."
  (:require
    [clj-line.color :as color])
  (:import
    [org.jline.reader LineReader]
    [org.jline.terminal Terminal]))

(def reader
  "Atom holding the running JLine LineReader."
  (atom nil))

(def current-ns
  "Atom holding the current namespace based on eval response."
  (atom "user"))

(def last-response
  "Atom holding the last eval's nREPL response."
  (atom nil))

(defn terminal
  "The running JLine Terminal."
  ^Terminal []
  (some-> ^LineReader @reader .getTerminal))

(defn terminal-width
  "The terminal's current column count."
  []
  (some-> (terminal) .getWidth))

(defn terminal-height
  "The terminal's current row count."
  []
  (some-> (terminal) .getHeight))

(defn print-above
  "Print a vector of {:text :style} segments above the input line, without
   disturbing in-progress input."
  [segments]
  (some-> ^LineReader @reader (.printAbove (color/build-attrib-str segments))))
