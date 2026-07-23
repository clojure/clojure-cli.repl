(ns clj-line.client.main
  (:require
    [clojure.string :as str]
    [clojure.tools.deps.config :as dc]
    [clj-line.client.nrepl :as nrepl]
    [clj-line.api :as api]
    [clj-line.reader :as reader]
    [clj-line.highlight :as highlight]
    [clj-line.color :as color])
  (:import
    [org.jline.terminal Terminal$Signal Terminal$SignalHandler TerminalBuilder]
    [org.jline.reader LineReader LineReaderBuilder Reference Widget EndOfFileException UserInterruptException]
    [org.jline.keymap KeyMap]))

(set! *warn-on-reflection* true)

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

(defn eof-or-delete
  "Returns a JLine widget for ctrl-d that ends input on an empty line.
   JLine's default binding leaves no way to exit the repl."
  ^Widget [^LineReader rdr]
  (reify Widget
    (apply [_]
      (if (zero? (.length (.getBuffer rdr)))
        (throw (EndOfFileException.))
        (.callWidget rdr "delete-char"))
      true)))

(defn unbind-control-self-inserts
  "Unbind control keys that insert a literal control char.
   JLine seems to miscount these and it breaks redrawing."
  [^KeyMap keymap]
  (let [self-insert (.getBound keymap "a")]
    (doseq [c (range 0x20)
            :let [k (str (char c))]
            :when (= self-insert (.getBound keymap k))]
      (.unbind keymap k))))

(defn build-reader ^LineReader [^java.io.File history-file editing-mode]
  (let [terminal (-> (TerminalBuilder/builder)
                     (.system true)
                     (.ffm false)
                     (.graphemeCluster false)
                     .build)
        rdr      (-> (LineReaderBuilder/builder)
                     (.terminal terminal)
                     (.parser (reader/clojure-parser))
                     (.highlighter (highlight/clojure-highlighter))
                     (.variable LineReader/SECONDARY_PROMPT_PATTERN "%N%P > ")
                     (.variable LineReader/HISTORY_FILE history-file)
                     .build)
        vi? (= :vi editing-mode)
        keymaps (.getKeyMaps rdr)]
    (.put (.getWidgets rdr) "indent-or-accept" (indent-or-accept rdr))
    (.put (.getWidgets rdr) "eof-or-delete" (eof-or-delete rdr))
    (doseq [keymap-name (if vi? [LineReader/VIINS LineReader/VICMD] [LineReader/MAIN])]
      (let [^KeyMap keymap (.get keymaps keymap-name)]
        (.bind keymap (Reference. "indent-or-accept") "\r")
        (.bind keymap (Reference. "eof-or-delete") (KeyMap/ctrl \D))))
    (when vi?
      (.put keymaps LineReader/MAIN (.get keymaps LineReader/VIINS)) ;; `main` is each line's starting keymap defaulted to emacs
      (unbind-control-self-inserts (.get keymaps LineReader/VIINS)))
    rdr))

(defn default-prompt []
  [{:text @api/current-ns :style {:fg :blue :bold true}}
   {:text " => "}])

(defn resolve-prompt [config]
  (if-let [prompt (:prompt config)]
    (or (requiring-resolve prompt)
        (throw (ex-info (str "clj-line: :prompt var not found: " prompt) {:prompt prompt})))
    default-prompt))

(defn render-prompt ^String [prompt-fn]
  (.toAnsi (color/build-attrib-str (prompt-fn))))

(defn load-config
  "Read the config, first putting its src dirs on the classpath so their code can
   be required. Set a DynamicClassLoader as the thread's context loader since the
   app loader for a `-M -m` process is not extensible."
  [lib]
  (let [dirs (->> [:project :user]
                  (map #(dc/data-file % lib "src"))
                  (filter (fn [^java.io.File d] (.isDirectory d))))]
    (when (seq dirs)
      (let [thread (Thread/currentThread)
            loader (clojure.lang.DynamicClassLoader. (.getContextClassLoader thread))]
        (doseq [^java.io.File d dirs]
          (.addURL loader (.toURL (.toURI d))))
        (.setContextClassLoader thread loader))))
  (dc/config lib))

(defn history-file
  "REPL history file, location is user configurable [:project|:user] under
   :history key, defaults to :user. Creates the parent dir if necessary."
  [lib config]
  (let [scope (if (= :project (:history config)) :project :user)
        ^java.io.File f (dc/data-file scope lib "history")]
    (.mkdirs (.getParentFile f))
    f))

(defn apply-keybindings
  [config reader]
  (when-let [keybindings (:keybindings config)]
    (if-let [f (requiring-resolve keybindings)]
      (f reader)
      (throw (ex-info (str "clj-line: :keybindings var not found: " keybindings) {:keybindings keybindings})))))

(defn -main [& args]
  (let [config (load-config 'org.clojure/clj-line)
        prompt-fn (resolve-prompt config)
        port (Integer/parseInt (str/trim (or (first args) (slurp ".nrepl-port"))))
        {:keys [session]} (nrepl/connect port)
        rdr (build-reader (history-file 'org.clojure/clj-line config) (:editing-mode config))]
    (reset! api/reader rdr)
    (reset! api/session session)
    (apply-keybindings config rdr)
    ;; ctrl-c during an eval outside of .readLine needs to interrupt the server
    (.handle (.getTerminal rdr) Terminal$Signal/INT
             (reify Terminal$SignalHandler
               (handle [_ _sig] (nrepl/interrupt session))))
    (println "connected to nREPL on" port)
    (loop [ns "user"]
      (reset! api/current-ns ns)
      (let [line (try (.readLine rdr (render-prompt prompt-fn))
                      (catch UserInterruptException _ "") ; ctrl-c
                      (catch EndOfFileException _ nil))] ; ctrl-d
        (when line
          (recur (if (str/blank? line)
                   ns
                   (let [resp (nrepl/eval-code session line)]
                     (reset! api/last-response resp)
                     (or (:ns resp) ns)))))))
    ;; jline and the nrepl client leave non-daemon threads behind
    (.close (.getTerminal rdr))
    (System/exit 0)))
