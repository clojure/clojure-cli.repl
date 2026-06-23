(ns clj-line.main
  (:require
    [clojure.main :as m]
    [clojure.repl.deps :as deps]
    [clojure.tools.deps.config :as dc]
    [clj-line.api :as api]
    [clj-line.color :as color]
    [clj-line.highlight :as highlight]
    [clj-line.reader :as reader])
  (:import
    [org.jline.terminal TerminalBuilder]
    [org.jline.reader LineReader LineReaderBuilder EndOfFileException UserInterruptException Reference Widget]
    [org.jline.keymap KeyMap]))

(def lib 'org.clojure/clj-line)

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

(defn default-prompt []
  [{:text (str (ns-name *ns*)) :style {:fg :blue :bold true}}
   {:text "=> "}])

(defn jline-read [^LineReader rdr prompt-fn]
  (fn [request-prompt request-exit]
    (try
      (let [prompt (.toAnsi (color/build-attrib-str (prompt-fn)))
            forms  (reader/read-all (.readLine rdr prompt))]
        (case (count forms)
          0 request-prompt
          1 (first forms)
          (cons 'do forms))) ;; Multiple complete forms on one line
      (catch UserInterruptException _ request-prompt)
      (catch EndOfFileException    _ request-exit))))

(defn tap-printer
  "Returns a tap fn that renders each value above the input line using JLine printAbove.
  Enables printing async values without scrambling typing."
  []
  (fn [value]
    (api/print-above [{:text "tap> " :style {:fg :magenta}}
                      {:text (pr-str value)}])))

;; NOTE this might go away entirely if the project splits into two processes.
(defn data-dir-deps
  "A :local/root coord for each config location whose data dir contains a deps.edn,
   so a prompt project placed there needs no explicit :deps entry."
  []
  (into {}
        (for [location [:user :project]
              :let [^java.io.File dir (dc/data-dir location lib)]
              :when (.exists (java.io.File. dir "deps.edn"))]
          [(symbol "clj-line" (str (name location) "-data"))
           {:local/root (.getPath dir)}])))

(defn add-config-deps [config]
  (when-let [deps (not-empty (into (or (:deps config) {}) (data-dir-deps)))]
    ;; add-libs needs a DynamicClassLoader context and *repl* bound
    (.setContextClassLoader
      (Thread/currentThread)
      (clojure.lang.DynamicClassLoader. (.getContextClassLoader (Thread/currentThread))))
    (binding [*repl* true] (deps/add-libs deps))))

(defn -main [& _]
  (let [config (dc/config lib)
        _ (add-config-deps config)
        prompt-fn (if-let [user-prompt (:prompt config)]
                    (requiring-resolve user-prompt)
                    default-prompt)
        eval-hook (when-let [eh (:eval-hook config)] (requiring-resolve eh))
        rdr (build-line-reader)]
    (reset! api/reader rdr)
    (add-tap (tap-printer))
    (m/repl
      :prompt      (fn [])
      :need-prompt (constantly true)
      :eval        (if eval-hook (eval-hook eval) eval)
      :read        (jline-read rdr prompt-fn))))
