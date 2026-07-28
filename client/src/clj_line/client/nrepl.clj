(ns clj-line.client.nrepl
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
  "Evals without printing. Nrepl can split :err across messages."
  [session code]
  (let [messages (nrepl/message session {:op "eval" :code code})
        merged-response (reduce merge {} messages)
        whole-err (not-empty (apply str (keep :err messages)))]
    (assoc merged-response :err whole-err)))

(defn interrupt [session]
  (nrepl/message session {:op "interrupt"}))

(defn connect [port]
  (let [conn (nrepl/connect :port port)
        client (nrepl/client conn Long/MAX_VALUE)
        session (nrepl/client-session client)]
    {:conn conn :session session}))
