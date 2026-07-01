(ns clj-line.server
  (:require
    [nrepl.server :as server]
    [clj-line.server.eval-hook :as hook]))

(defn start [{:keys [eval-hook middleware port]}]
  (when eval-hook
    (hook/install (requiring-resolve eval-hook)))
  (let [extra (mapv requiring-resolve middleware)
        handler (apply server/default-handler #'hook/middleware extra)
        srv (server/start-server :port (or port 0) :handler handler)]
    (spit ".nrepl-port" (str (:port srv)))
    srv))
