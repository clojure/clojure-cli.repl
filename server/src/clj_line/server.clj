(ns clj-line.server
  (:require
    [clojure.java.io :as io]
    [nrepl.server :as server]
    [clj-line.server.eval-hook :as hook]))

(set! *warn-on-reflection* true)

(defn start [{:keys [eval-hook middleware port]}]
  (when eval-hook
    (hook/install (requiring-resolve eval-hook)))
  (let [extra (mapv requiring-resolve middleware)
        handler (apply server/default-handler #'hook/middleware extra)
        srv (server/start-server :port (or port 0) :handler handler)]
    (spit ".nrepl-port" (str (:port srv)))
    srv))

(defn stop [srv]
  (server/stop-server srv)
  (io/delete-file ".nrepl-port" :silently))
