(ns clj-line.main
  (:require
    [clojure.main :as m]
    [clj-line.color :refer [palette]]
    [clj-line.highlight :as highlight]
    [clj-line.reader :as reader])
  (:import
    [org.jline.terminal TerminalBuilder]
    [org.jline.reader LineReader LineReaderBuilder EndOfFileException UserInterruptException Reference Widget]
    [org.jline.keymap KeyMap]
    [org.jline.utils AttributedString AttributedStringBuilder AttributedStyle]))

(defn build-attrib-str
  "Build an AttributedString from a seq of [text style] pairs."
  ^AttributedString [pairs]
  (let [asb (AttributedStringBuilder.)]
    (doseq [[^String text ^AttributedStyle style] pairs]
      (.append asb text style))
    (.toAttributedString asb)))

(defn prompt-str ^String []
  (.toAnsi (build-attrib-str [["λ " (:prompt-lambda palette)]
                              [(str (ns-name *ns*)) (:prompt-ns palette)]
                              ["=> " (:prompt-suffix palette)]])))

(defn indent-or-accept
  "Returns a JLine widget for the Enter key. A complete buffer gets submitted.
   Otherwise it inserts a newline with enough spaces to align
   the cursor one column past the last unclosed opener."
  ^Widget [^LineReader rdr]
  (reify Widget
    (apply [_]
      (let [buf (.getBuffer rdr)
            line (.toString buf)]
        (if (reader/complete? line)
          (.callWidget rdr "accept-line")
          (.write buf (str "\n" (apply str (repeat (reader/indent-column line) \space))))))
      true)))

(defn build-line-reader []
  (let [terminal (-> (TerminalBuilder/builder)
                     (.system true)
                     (.ffm false) ;; Disable the FFM provider it requires JDK 22+ and setting a flag
                     (.graphemeCluster false) ;; Without this startup logs some garbage
                     .build)
        rdr      (-> (LineReaderBuilder/builder)
                     (.terminal terminal)
                     (.parser (reader/clojure-parser))
                     (.highlighter (highlight/clojure-highlighter))
                     (.variable LineReader/SECONDARY_PROMPT_PATTERN "%N%P > ")
                     .build)
        widget-name "indent-or-accept"]
    (.put (.getWidgets rdr) widget-name (indent-or-accept rdr))
    (.bind ^KeyMap (.get (.getKeyMaps rdr) LineReader/MAIN) (Reference. widget-name) "\r")
    rdr))

(defn jline-read [^LineReader rdr]
  (fn [request-prompt request-exit]
    (try
      (let [forms (reader/read-all (.readLine rdr (prompt-str)))]
        (case (count forms)
          0 request-prompt
          1 (first forms)
          (cons 'do forms))) ;; Multiple complete forms on one line
      (catch UserInterruptException _ request-prompt)
      (catch EndOfFileException    _ request-exit))))

(defn tap-printer
  "Returns a tap fn that renders each value above the input line using JLine printAbove.
  Enables printing async values without scrambling typing."
  [^LineReader rdr]
  (fn [value]
    (.printAbove rdr (build-attrib-str [["tap> " (:tap-label palette)]
                                        [(pr-str value) (:prompt-suffix palette)]]))))

(defn -main [& _]
  (let [rdr (build-line-reader)]
    (add-tap (tap-printer rdr))
    (m/repl
      :prompt      (fn [])
      :need-prompt (constantly true)
      :read        (jline-read rdr))))
