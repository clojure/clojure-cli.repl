(ns clojure-cli.repl.server
  (:require
    [clojure.data.json :as json]
    [clojure.java.doc.api :as jdoc]
    [clojure.java.io :as io]
    [clojure.java.shell :as sh]
    [clojure.string :as str]
    [clojure.tools.deps.config :as dc]
    [nrepl.config :as nrepl-config]
    [nrepl.middleware :as middleware]
    [nrepl.server :as server]
    [clojure-cli.repl.hooks :as hooks]
    [clojure-cli.repl.inspect]))

(set! *warn-on-reflection* true)

(def dynamic-vars
  '{:warn-on-reflection   clojure.core/*warn-on-reflection*
    :print-meta           clojure.core/*print-meta*
    :print-length         clojure.core/*print-length*
    :print-level          clojure.core/*print-level*
    :print-namespace-maps clojure.core/*print-namespace-maps*
    :unchecked-math       clojure.core/*unchecked-math*
    :assert               clojure.core/*assert*
    :compile-path         clojure.core/*compile-path*})

(defn configured-dynamic-vars [config]
  (reduce-kv (fn [m k sym]
               (if (contains? config k)
                 (assoc m sym (config k))
                 m))
             {}
             dynamic-vars))

(def clojuredocs-index
  "Fully qualified var name to its clojuredocs entry saved to the user data dir on first use."
  (delay
    (try
      (let [f (dc/data-file :user 'org.clojure/clojure-cli.repl "clojuredocs-export.json")]
        (when-not (.isFile f)
          (.mkdirs (.getParentFile f))
          (spit f (slurp "https://clojuredocs.org/clojuredocs-export.json")))
        (into {}
              (map (fn [v] [(str (:ns v) "/" (:name v))
                            (update v :arglists (fn [arglists] (mapv #(str "[" % "]") arglists)))]))
              (:vars (json/read-str (slurp f) :key-fn keyword))))
      (catch Exception _ nil))))

(defn clojuredocs-examples [fq]
  (when-let [examples (seq (:examples (get @clojuredocs-index fq)))]
    (str "== " fq "\n\n"
         (str/join "\n\n" (map-indexed (fn [i e] (str "= example " (inc i) "\n" (:body e))) examples)))))

(defn doc-block [fq-name arglists doc]
  (str/join "\n" (remove str/blank? [fq-name (str/join " " arglists) doc])))

(defn resolved [sym]
  (try (ns-resolve *ns* sym) (catch Exception _ nil)))

(defn clojuredocs-doc [sym]
  (let [fq (if-let [{:keys [ns name]} (some-> (resolved sym) meta)]
             (str ns "/" name)
             (str "clojure.core/" sym))]
    (when-let [{:keys [arglists doc]} (get @clojuredocs-index fq)]
      (str (doc-block fq arglists doc) "\n\n" (clojuredocs-examples fq)))))

(defn var-doc [sym]
  (when-let [{:keys [ns name arglists doc]} (some-> (resolved sym) meta)]
    (doc-block (str ns "/" name) arglists doc)))

(defn java-doc [sym]
  (try (with-out-str (jdoc/javadoc-fn (str sym) nil))
       (catch Exception _ nil)))

(defn print-doc-for [token]
  (let [sym (symbol token)]
    (print (or (clojuredocs-doc sym)
               (var-doc sym)
               (java-doc sym)
               (str "No doc for " sym)))))

(defn resolve-middleware
  "Resolves a middleware symbol to its var.
  Adds a default nREPL descriptor when the var has none."
  [sym]
  (let [v (or (requiring-resolve sym)
              (throw (ex-info (str "clojure-cli.repl: :middleware var not found: " sym) {:middleware sym})))]
    (when-not (:nrepl.middleware/descriptor (meta v))
      (middleware/set-descriptor! v {:requires #{} :expects #{"eval"} :handles {}}))
    v))

(defn start [{:keys [middleware port] :as config}]
  (when-let [vars (not-empty (configured-dynamic-vars config))]
    (alter-var-root #'nrepl-config/config assoc :dynamic-vars vars))
  (hooks/install config)
  (let [extra (mapv resolve-middleware middleware)
        handler (apply server/default-handler #'hooks/middleware extra)
        srv (server/start-server :port (or port 0) :handler handler)]
    (spit ".nrepl-port" (str (:port srv)))
    srv))

(defn stop [srv]
  (server/stop-server srv)
  (io/delete-file ".nrepl-port" :silently))

(defn cli-classpath
  "CP for the configs deps.edn -Srepro excludes global ~/.clojure/deps.edn"
  [location lib]
  (when (.isFile (dc/data-file location lib "deps.edn"))
    (let [dir (dc/data-dir location lib)
          {:keys [exit out err]} (sh/sh "clojure" "-Srepro" "-A:clojure-cli.repl/server" "-Spath" :dir (str dir))]
      (if (zero? exit)
        (str/trim out)
        (println "clojure-cli.repl: could not resolve deps.edn in" (str dir) "-" (str/trim err))))))

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

(defn ignore-interrupt
  "Make the server ignore ctrl-c. The spawned client shares the server's process
   group, so a terminal ctrl-c reaches both. The server must not exit while the
   client is still using the server."
  []
  (sun.misc.Signal/handle
    (sun.misc.Signal. "INT")
    (reify sun.misc.SignalHandler
      (handle [_ _sig] nil))))

(defn spawn-client
  "Run the client in its own JVM so its deps stay off the server's classpath."
  ^Process [port]
  ;; TODO invoking client with local alias until we have a published coord
  (let [^java.util.List cmd ["clojure" "-M:attach" (str port)]]
    (-> (ProcessBuilder. cmd)
        .inheritIO
        .start)))

(defn -main [& args]
  (let [srv (start (load-config 'org.clojure/clojure-cli.repl))]
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. ^Runnable (fn [] (stop srv)))) ;; on shutdown remove .nrepl-port
    (println "nREPL server listening on port" (:port srv))
    (if (= "repl" (first args))
      (do
        (ignore-interrupt)
        (.waitFor (spawn-client (:port srv)))
        (System/exit 0))
      @(promise))))
