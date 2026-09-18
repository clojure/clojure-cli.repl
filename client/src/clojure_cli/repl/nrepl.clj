(ns clojure-cli.repl.nrepl
  (:require
    [nrepl.core :as nrepl]))

(set! *warn-on-reflection* true)

(defn render [{:keys [out err value]}]
  (when out (print out) (flush))
  (when err (binding [*out* *err*] (print err) (flush)))
  (when value (println value)))

(defn eval-code [session code]
  (reduce (fn [acc res]
            (render res)
            (merge acc res))
          {}
          (nrepl/message session {:op "eval" :code code})))

(defn request [session message]
  (reduce merge {} (nrepl/message session message)))

(defn interrupt [session]
  (nrepl/message session {:op "interrupt"}))

(defn connect [port]
  (let [conn (nrepl/connect :port port)
        client (nrepl/client conn Long/MAX_VALUE)
        session-id (nrepl/new-session client)]
    {:conn conn
     :session (nrepl/client-session client :session session-id)
     :session-id session-id}))
