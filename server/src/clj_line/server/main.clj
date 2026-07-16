(ns clj-line.server.main
  (:require
    [clojure.tools.deps.config :as dc]
    [clj-line.server :as server]))

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
