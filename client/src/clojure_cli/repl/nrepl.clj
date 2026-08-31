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

(defn eval-quiet
  "Evals without printing. Nrepl can split :out and :err across messages."
  [session code ns-name]
  (let [messages (nrepl/message session {:op "eval" :code code :ns ns-name})]
    (assoc (reduce merge {} messages)
           :out (not-empty (apply str (keep :out messages)))
           :err (not-empty (apply str (keep :err messages))))))

(defn interrupt [session]
  (nrepl/message session {:op "interrupt"}))

(defn connect [port]
  (let [conn (nrepl/connect :port port)
        client (nrepl/client conn Long/MAX_VALUE)]
    {:conn conn
     :session (nrepl/client-session client)
     :tool-session (nrepl/client-session client)}))
