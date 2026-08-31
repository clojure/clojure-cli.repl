(ns clojure-cli.repl.api
  "Access to the running clojure-cli.repl REPL's line reader and terminal."
  (:require
    [clojure-cli.repl.color :as color]
    [nrepl.core :as nrepl])
  (:import
    [org.jline.builtins Less Source Source$InputStreamSource]
    [org.jline.keymap KeyMap]
    [org.jline.reader LineReader Widget]
    [org.jline.terminal Terminal]
    [org.jline.utils InfoCmp$Capability]))

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

(def tool-session
  "Atom holding the nREPL session for internal evals."
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
  (.getTerminal ^LineReader @reader))

(defn terminal-width [] (.getWidth (terminal)))

(defn terminal-height [] (.getHeight (terminal)))

(defn print-above
  "Print a vector of {:text :style} segments above the input line, without
   disturbing in-progress input."
  [segments]
  (.printAbove ^LineReader @reader (color/build-attrib-str segments)))

(defn show-below
  "Show segments in a pane directly below the input line.
  Cleared by passing nil"
  [segments]
  (let [field (.getDeclaredField org.jline.reader.impl.LineReaderImpl "post")]
    (.setAccessible field true)
    (.set field @reader
          (when segments
            (reify java.util.function.Supplier
              (get [_] (color/build-attrib-str segments)))))))

(defn page
  "Show text in full screen less proivded by JLine. q returns to the repl."
  [title ^String text]
  (let [t (terminal)
        less (Less. t (.toPath (java.io.File. ".")))
        source (Source$InputStreamSource.
                 (java.io.ByteArrayInputStream. (.getBytes text "UTF-8"))
                 false title)]
    (^[Source/1] Less/.run less (into-array Source [source]))
    (.puts t InfoCmp$Capability/keypad_xmit (into-array Object []))
    (.flush (.writer t))))

(defn widget
  "Reify a zero-arg fn as a JLine widget to use with `bind-key`.
  Any return value from the function is ignored."
  [f]
  (reify Widget (apply [_] (f) true)))

(defn key-sequence
  "M-r is alt-r, C-e is ctrl-e, TAB and RET name themselves, anything else is literal."
  [s]
  (if-let [[_ modifier ^String key-name] (re-matches #"([MC])-(.+)" s)]
    (case modifier
      "M" (KeyMap/alt key-name)
      "C" (KeyMap/ctrl (.charAt key-name 0)))
    (case s "TAB" "\t" "RET" "\r" s)))

(defn bind-key
  "Run `widget` when `keyseq` is typed. `keyseq` is the raw characters the
  terminal sends for a key. A plain string for simple keys,
  or the KeyMap helpers ctrl/alt/key for special keys.
  Example: `(KeyMap/ctrl \\E)` for Ctrl-E."
  ([reader keyseq widget] (bind-key reader keyseq widget LineReader/MAIN))
  ([^LineReader reader ^CharSequence keyseq widget keymap]
   (.bind ^KeyMap (.get (.getKeyMaps reader) keymap) widget keyseq)))
