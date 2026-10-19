(ns clojure-cli.repl.api
  "Access to the running clojure-cli.repl REPL's line reader and terminal."
  (:require
    [clojure.string :as str]
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

(defn eval-code
  "Evaluate `code` on the server and return the merged nREPL response
  for the caller to display."
  [code]
  (reduce (fn [acc {:keys [out err] :as res}]
            (cond-> (merge acc res)
              out (assoc :out (str (:out acc) out))
              err (assoc :err (str (:err acc) err))))
          {}
          (nrepl/message @session {:op "eval" :code code :ns @current-ns})))

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

(def ^:private named-keys
  {"TAB" "\t" "RET" "\r" "SPC" " " "ESC" "\u001b" "DEL" "\u007f"})

(def ^:private special-keys
  {"<up>"    InfoCmp$Capability/key_up
   "<down>"  InfoCmp$Capability/key_down
   "<left>"  InfoCmp$Capability/key_left
   "<right>" InfoCmp$Capability/key_right
   "<home>"  InfoCmp$Capability/key_home
   "<end>"   InfoCmp$Capability/key_end})

(def ^:private ctrl-form-chars
  (set "@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_?abcdefghijklmnopqrstuvwxyz "))

(defn ^:private invalid-key-string [key-string reason]
  (throw (ex-info (str "clojure-cli.repl: invalid key string " (pr-str key-string) ": " reason)
                  {:key-string key-string})))

(defn ^:private unmodified-chars [key-string modifiers base-key]
  (let [modified? (seq modifiers)
        special-key? (re-matches #"<.+>" base-key)
        special-chars (when-let [capability (special-keys base-key)]
                        (when-not modified? (KeyMap/key (terminal) capability)))
        single-char? (= 1 (count base-key))]
    (cond
      (empty? base-key) (invalid-key-string key-string (str modifiers " has no key"))
      special-chars special-chars
      (and special-key? modified?) (invalid-key-string key-string (str "C- and M- cannot be used with " base-key))
      special-key? (invalid-key-string key-string (str "unknown special key " base-key))
      (named-keys base-key) (named-keys base-key)
      (or (not modified?) single-char?) base-key
      :else (invalid-key-string key-string (str modifiers base-key " modifies more than one key")))))

(defn ^:private keystroke-chars [key-string keystroke]
  (let [[_ modifiers base-key] (re-matches #"((?:[CM]-)*)(.*)" keystroke)
        ctrl? (str/includes? modifiers "C-")
        meta? (str/includes? modifiers "M-")
        base-chars (unmodified-chars key-string modifiers base-key)
        ctrl-form? (ctrl-form-chars (first base-chars))
        ctrl-chars (if ctrl? (KeyMap/ctrl (first base-chars)) base-chars)
        modified-chars (if meta? (KeyMap/alt ^String ctrl-chars) ctrl-chars)]
    (cond
      (and ctrl? (not ctrl-form?)) (invalid-key-string key-string (str "C- cannot be used with " base-key))
      (str/includes? modified-chars (KeyMap/ctrl \c)) (invalid-key-string key-string "C-c interrupts and cannot be bound")
      :else modified-chars)))

(defn key-sequence
  "Return the characters the terminal sends when `key-string` is typed.
  `key-string` uses Emacs key notation: `C-e`, `C-M-f`, `C-x d`, `TAB`, `<up>`."
  [key-string]
  (apply str (map #(keystroke-chars key-string %) (str/split (str/trim key-string) #"\s+"))))

(defn bind-key
  "Run `widget` when `keyseq` is typed. `keyseq` is the raw characters the
  terminal sends for a key. A plain string for simple keys,
  or the KeyMap helpers ctrl/alt/key for special keys.
  Example: `(KeyMap/ctrl \\E)` for Ctrl-E."
  ([reader keyseq widget] (bind-key reader keyseq widget LineReader/MAIN))
  ([^LineReader reader ^CharSequence keyseq widget keymap]
   (.bind ^KeyMap (.get (.getKeyMaps reader) keymap) widget keyseq)))
