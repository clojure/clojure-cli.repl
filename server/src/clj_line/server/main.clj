(ns clj-line.server.main
  (:require
    [clojure.java.shell :as sh]
    [clojure.string :as str]
    [clojure.tools.deps.config :as dc]
    [clj-line.server :as server]))

(set! *warn-on-reflection* true)

(defn cli-classpath
  "CP for the configs deps.edn -Srepro excludes global ~/.clojure/deps.edn"
  [location lib]
  (when (.isFile (dc/data-file location lib "deps.edn"))
    (let [dir (dc/data-dir location lib)
          {:keys [exit out err]} (sh/sh "clojure" "-Srepro" "-A:clj-line/server" "-Spath" :dir (str dir))]
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
  (let [srv (server/start (load-config 'org.clojure/clj-line))]
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. ^Runnable (fn [] (server/stop srv)))) ;; on shutdown remove .nrepl-port
    (println "nREPL server listening on port" (:port srv))
    (if (= "repl" (first args))
      (do
        (ignore-interrupt)
        (.waitFor (spawn-client (:port srv)))
        (System/exit 0))
      @(promise))))
