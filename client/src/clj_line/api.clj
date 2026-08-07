(ns clj-line.api
  "Access to the running clj-line REPL's line reader and terminal."
  (:require
    [clj-line.color :as color]
    [nrepl.core :as nrepl])
  (:import
    [org.jline.keymap KeyMap]
    [org.jline.reader LineReader Widget]
    [org.jline.terminal Terminal]))

(set! *warn-on-reflection* true)

(def reader
  "Atom holding the running JLine LineReader."
  (atom nil))

(def current-ns
  "Atom holding the current namespace based on eval response."
  (atom "user"))

(def last-response
  "Atom holding the last eval's nREPL response."
  (atom nil))

(def session
  "Atom holding the running nREPL client session."
  (atom nil))

(defn eval-code
  "Evaluate `code` on the server and return the merged nREPL response
  for the caller to display."
  [code]
  (reduce (fn [acc {:keys [out err] :as res}]
            (cond-> (merge acc res)
              out (assoc :out (str (:out acc) out))
              err (assoc :err (str (:err acc) err))))
          {}
          (nrepl/message @session {:op "eval" :code code})))

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

(defn widget
  "Reify a zero-arg fn as a JLine widget to use with `bind-key`.
  Any return value from the function is ignored."
  [f]
  (reify Widget (apply [_] (f) true)))

(defn bind-key
  "Run `widget` when `keyseq` is typed. `keyseq` is the raw characters the
  terminal sends for a key. A plain string for simple keys,
  or the KeyMap helpers ctrl/alt/key for special keys.
  Example: `(KeyMap/ctrl \\E)` for Ctrl-E."
  ([reader keyseq widget] (bind-key reader keyseq widget LineReader/MAIN))
  ([^LineReader reader ^CharSequence keyseq widget keymap]
   (.bind ^KeyMap (.get (.getKeyMaps reader) keymap) widget keyseq)))
