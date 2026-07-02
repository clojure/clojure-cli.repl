(ns clj-line.client.nrepl
  (:require
    [nrepl.core :as nrepl]))

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

(defn interrupt [session]
  (nrepl/message session {:op "interrupt"}))

(defn connect [port]
  (let [conn (nrepl/connect :port port)
        client (nrepl/client conn Long/MAX_VALUE)
        session (nrepl/client-session client)]
    {:conn conn :session session}))
