(ns clj-line.server
  (:require
    [clojure.java.io :as io]
    [nrepl.config :as nrepl-config]
    [nrepl.server :as server]
    [clj-line.server.hooks :as hooks]))

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

(defn start [{:keys [middleware port] :as config}]
  (when-let [vars (not-empty (configured-dynamic-vars config))]
    (alter-var-root #'nrepl-config/config assoc :dynamic-vars vars))
  (hooks/install config)
  (let [extra (mapv requiring-resolve middleware)
        handler (apply server/default-handler #'hooks/middleware extra)
        srv (server/start-server :port (or port 0) :handler handler)]
    (spit ".nrepl-port" (str (:port srv)))
    srv))

(defn stop [srv]
  (server/stop-server srv)
  (io/delete-file ".nrepl-port" :silently))
