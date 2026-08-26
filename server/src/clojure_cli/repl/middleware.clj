(ns clojure-cli.repl.middleware
  (:require
    [nrepl.transport :as transport]))

(set! *warn-on-reflection* true)

(defn assoc-response
  "Returns nREPL middleware that merges `f` onto the final response of each eval.
   Bencode limits the values to strings or integers."
  [f]
  (fn [handler]
    (fn [{:keys [op transport] :as msg}]
      (if (= op "eval")
        (handler (assoc msg :transport
                        (reify transport/Transport
                          (recv [_] (transport/recv transport))
                          (recv [_ timeout] (transport/recv transport timeout))
                          (send [_ resp]
                            (transport/send transport
                                            (cond-> resp
                                              (contains? (:status resp) :done) (merge (f))))))))
        (handler msg)))))
