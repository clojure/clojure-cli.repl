(ns clj-line.client
  (:require
    [clojure.java.shell :as sh]
    [clojure.string :as str]
    [clojure.tools.deps.config :as dc]
    [clj-line.nrepl :as nrepl]
    [clj-line.api :as api]
    [clj-line.paredit :as paredit]
    [clj-line.reader :as reader]
    [clj-line.highlight :as highlight]
    [clj-line.color :as color])
  (:import
    [org.jline.terminal Terminal$Signal Terminal$SignalHandler TerminalBuilder]
    [org.jline.reader Buffer LineReader LineReaderBuilder Reference Widget EndOfFileException UserInterruptException]
    [org.jline.keymap KeyMap]))

(set! *warn-on-reflection* true)

(defn cursor-at-end?
  "True when nothing other than possible whitespace follows the cursor in the buffer."
  [^Buffer buf]
  (str/blank? (subs (.toString buf) (.cursor buf))))

(defn indent-or-accept
  "Returns a JLine widget for the Enter key. A complete buffer gets submitted
  otherwise write a new indented line. Paired braces always make a complete form
  so if active submitting requires the cursor be at the end."
  [^LineReader rdr paired?]
  (reify Widget
    (apply [_]
      (let [buf (.getBuffer rdr)
            line (.toString buf)
            ready-to-submit? (and (reader/complete? line)
                                  (if paired? (cursor-at-end? buf) true))]
        (if ready-to-submit?
          (.callWidget rdr "accept-line")
          (.write buf (str "\n" (apply str (repeat (reader/indent-column line (.cursor buf)) \space))))))
      true)))

(defn eof-or-delete
  "Returns a JLine widget for ctrl-d that ends input on an empty line.
   JLine's default binding leaves no way to exit the repl."
  [^LineReader rdr]
  (reify Widget
    (apply [_]
      (if (zero? (.length (.getBuffer rdr)))
        (throw (EndOfFileException.))
        (.callWidget rdr "delete-char"))
      true)))

(defn insert-pair [^LineReader rdr open close]
  (reify Widget
    (apply [_]
      (doto (.getBuffer rdr)
        (.write (str open close))
        (.move -1))
      true)))

(defn skip-or-insert
  "Returns a JLine widget for a closing brace that advances the cursor if
  the brace typed is the same."
  [^LineReader rdr close]
  (reify Widget
    (apply [_]
      (let [buf (.getBuffer rdr)]
        (if (= (.currChar buf) (int close))
          (.move buf 1)
          (.write buf (str close))))
      true)))

(defn delete-pair
  "Returns a JLine widget for backspace that removes both halves of an
   empty pair of braces."
  [^LineReader rdr]
  (reify Widget
    (apply [_]
      (let [buf (.getBuffer rdr)
            closer (reader/closer-of (char (.prevChar buf)))]
        (when (and closer (= (int closer) (.currChar buf)))
          (.delete buf))
        (.backspace buf))
      true)))

(defn bind-pairs
  "Bind each brace to its pairing widget in keymap."
  [^LineReader rdr ^KeyMap keymap]
  (let [widgets (.getWidgets rdr)]
    (doseq [[open close] reader/closer-of
            :let [open-name (str "insert-pair-" open)
                  close-name (str "skip-or-insert-" close)]]
      (.put widgets open-name (insert-pair rdr open close))
      (.put widgets close-name (skip-or-insert rdr close))
      (.bind keymap (Reference. open-name) (str open))
      (.bind keymap (Reference. close-name) (str close)))
    (.put widgets "delete-pair" (delete-pair rdr))
    (.bind keymap (Reference. "delete-pair") (KeyMap/del))))

(defn unbind-control-self-inserts
  "Unbind control keys that insert a literal control char.
   JLine seems to miscount these and it breaks redrawing."
  [^KeyMap keymap]
  (let [self-insert (.getBound keymap "a")]
    (doseq [c (range 0x20)
            :let [k (str (char c))]
            :when (= self-insert (.getBound keymap k))]
      (.unbind keymap k))))

(defn build-reader ^LineReader [{:keys [editing-mode bracket-pairs]} history-file]
  (let [terminal (-> (TerminalBuilder/builder)
                     (.system true)
                     (.ffm false)
                     (.graphemeCluster false)
                     .build)
        rdr      (-> (LineReaderBuilder/builder)
                     (.terminal terminal)
                     (.parser (reader/clojure-parser))
                     (.highlighter (highlight/clojure-highlighter))
                     (.variable LineReader/SECONDARY_PROMPT_PATTERN "%P ")
                     (.variable LineReader/HISTORY_FILE history-file)
                     .build)
        vi? (= :vi editing-mode)
        keymaps (.getKeyMaps rdr)]
    (.put (.getWidgets rdr) "indent-or-accept" (indent-or-accept rdr bracket-pairs))
    (.put (.getWidgets rdr) "eof-or-delete" (eof-or-delete rdr))
    (doseq [keymap-name (if vi? [LineReader/VIINS LineReader/VICMD] [LineReader/MAIN])]
      (let [^KeyMap keymap (.get keymaps keymap-name)]
        (.bind keymap (Reference. "indent-or-accept") "\r")
        (.bind keymap (Reference. "eof-or-delete") (KeyMap/ctrl \D))
        (when bracket-pairs (bind-pairs rdr keymap))))
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

(defn cli-classpath
  "CP for the config's deps.edn -Srepro excludes global ~/.clojure/deps.edn"
  [location lib]
  (when (.isFile (dc/data-file location lib "deps.edn"))
    (let [dir (dc/data-dir location lib)
          {:keys [exit out err]} (sh/sh "clojure" "-Srepro" "-A:clj-line/client" "-Spath" :dir (str dir))]
      (if (zero? exit)
        (str/trim out)
        (println "clj-line: could not resolve deps.edn in" (str dir) "-" (str/trim err))))))

(defn classpath-files [^java.io.File dir ^String cp]
  (map #(.toFile (.resolve (.toPath dir) ^String %))
       (.split cp java.io.File/pathSeparator)))

(defn config-classpath [location lib]
  (when-let [cp (cli-classpath location lib)]
    (classpath-files (dc/data-dir location lib) cp)))

(defn load-config
  "Puts the config classpath on a DynamicClassLoader installed as the thread's
   context loader. A new loader is required because the app loader cannot be
   extended. Returns the config map."
  [lib]
  (let [entries (distinct (mapcat #(config-classpath % lib) [:project :user]))]
    (when (seq entries)
      (let [thread (Thread/currentThread)
            loader (clojure.lang.DynamicClassLoader. (.getContextClassLoader thread))]
        (doseq [^java.io.File f entries]
          (.addURL loader (.toURL (.toURI f))))
        (.setContextClassLoader thread loader))))
  (dc/config lib))

(defn history-file
  "REPL history file, location is user configurable [:project|:user] under
   :history key, defaults to :user. Creates the parent dir if necessary."
  [lib config]
  (let [scope (if (= :project (:history config)) :project :user)
        f (dc/data-file scope lib "history")]
    (.mkdirs (.getParentFile f))
    f))

(defn eval-form
  "Widget evaluating the form at the cursor and printing the value above the prompt.
  User configurable under :eval-form-at-cursor"
  [^LineReader rdr]
  (api/widget
    (fn []
      (let [buf (.getBuffer rdr)]
        (when-let [code (reader/form-at-cursor (.toString buf) (.cursor buf))]
          (let [{:keys [value err]} (api/eval-code code)]
            (api/print-above [{:text (str "=> " (or value err) "\n")
                               :style {:fg :bright-black}}])))))))

(defn apply-keybindings
  [config reader]
  (when-let [keybindings (:keybindings config)]
    (if-let [f (requiring-resolve keybindings)]
      (f reader)
      (throw (ex-info (str "clj-line: :keybindings var not found: " keybindings) {:keybindings keybindings})))))

(defn auto-require-code
  "Returns code that requires libspecs into the current namespace.
   nrepl rolls *1 *2 *3 forward after every eval so we reset them."
  [libspecs]
  (pr-str `(let [x# *1 y# *2 z# *3]
             (require ~@(map #(list 'quote %) libspecs))
             (set! *1 y#) (set! *2 z#)
             x#)))

(defn auto-require
  "Evals a require of libspecs in the current namespace.
   Prints the error if a libspec fails to load."
  [session libspecs]
  (when (seq libspecs)
    (let [{:keys [ex err]} (nrepl/eval-quiet session (auto-require-code libspecs))]
      (when ex
        (println "clj-line: :auto-require failed:" (str/trim (or err ex)))))))

(defn -main [& args]
  (let [config (load-config 'org.clojure/clj-line)
        prompt-fn (resolve-prompt config)
        port (Integer/parseInt (str/trim (or (first args) (slurp ".nrepl-port"))))
        {:keys [session]} (nrepl/connect port)
        libspecs (:auto-require config)
        rdr (build-reader config (history-file 'org.clojure/clj-line config))]
    (reset! api/reader rdr)
    (reset! api/session session)
    (when-let [keyseq (:eval-form-at-cursor config)]
      (api/bind-key rdr (api/key-sequence keyseq) (eval-form rdr)))
    (paredit/install config rdr)
    (apply-keybindings config rdr)
    ;; ctrl-c during an eval outside of .readLine needs to interrupt the server
    (.handle (.getTerminal rdr) Terminal$Signal/INT
             (reify Terminal$SignalHandler
               (handle [_ _sig] (nrepl/interrupt session))))
    (println "connected to nREPL on" port)
    (auto-require session libspecs)
    (loop [ns "user"]
      (reset! api/current-ns ns)
      (let [line (try (.readLine rdr (render-prompt prompt-fn))
                      (catch UserInterruptException _ "") ; ctrl-c
                      (catch EndOfFileException _ nil))] ; ctrl-d
        (when line
          (let [new-ns (if (str/blank? line)
                         ns
                         (let [resp (nrepl/eval-code session line)]
                           (reset! api/last-response resp)
                           (or (:ns resp) ns)))]
            (when (not= new-ns ns) (auto-require session libspecs))
            (recur new-ns)))))
    ;; jline and the nrepl client leave non-daemon threads behind
    (.close (.getTerminal rdr))
    (System/exit 0)))
