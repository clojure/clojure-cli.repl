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

(defn build-reader ^LineReader []
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
                     .build)
        widget-name "indent-or-accept"]
    (.put (.getWidgets rdr) widget-name (indent-or-accept rdr))
    (.bind ^KeyMap (.get (.getKeyMaps rdr) LineReader/MAIN) (Reference. widget-name) "\r")
    rdr))

(defn default-prompt []
  [{:text @api/current-ns :style {:fg :blue :bold true}}
   {:text " => "}])

(defn resolve-prompt [config]
  (if-let [spec (:prompt config)]
    (or (requiring-resolve spec)
        (throw (ex-info (str "clj-line: :prompt var not found: " spec) {:prompt spec})))
    default-prompt))

(defn render-prompt ^String [prompt-fn]
  (.toAnsi (color/build-attrib-str (prompt-fn))))

(defn -main [& args]
  (let [prompt-fn (resolve-prompt (dc/config 'org.clojure/clj-line))
        port (Integer/parseInt (str/trim (or (first args) (slurp ".nrepl-port"))))
        {:keys [session]} (nrepl/connect port)
        rdr (build-reader)]
    (reset! api/reader rdr)
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
                     (or (:ns resp) ns)))))))))
