(ns clj-line.server.eval-hook
  (:require
    [nrepl.middleware :refer [set-descriptor!]]))

(def evaluator (atom eval))
(defn hooked-eval [form] (@evaluator form))

(defn install [hook]
  (reset! evaluator (hook eval)))

(defn middleware [handler]
  (fn [{:keys [op] :as msg}]
    (handler (cond-> msg
               (and (= op "eval") (not= @evaluator eval) (not (:eval msg)))
               (assoc :eval (str `hooked-eval))))))

(set-descriptor! #'middleware
  {:requires #{} :expects #{"eval"} :handles {}})
