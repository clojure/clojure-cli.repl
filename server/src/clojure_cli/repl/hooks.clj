(ns clojure-cli.repl.hooks
  (:require
    [clojure.main :as main]
    [clojure-cli.repl.inspect :as inspect]
    [nrepl.middleware :refer [set-descriptor!]]
    [nrepl.middleware.caught :as caught]
    [nrepl.middleware.print :as print]))

(set! *warn-on-reflection* true)

(def default-eval eval)
(def default-print print/*print-fn*)
(def default-caught main/repl-caught)

(def evaluator (atom nil))
(def printer (atom nil))
(def catcher (atom nil))

(defn hooked-eval [form] (@evaluator form))
(defn hooked-print [value writer options] (@printer value writer options))
(defn hooked-caught [throwable] (@catcher throwable))

(defn resolve-hook [key sym]
  (or (requiring-resolve sym)
      (throw (ex-info (str "clojure-cli.repl: " key " var not found: " sym) {key sym}))))

(defn install [{:keys [eval-hook print-hook caught-hook]}]
  (when eval-hook (reset! evaluator ((resolve-hook :eval-hook eval-hook) default-eval)))
  (when print-hook (reset! printer ((resolve-hook :print-hook print-hook) default-print)))
  (when caught-hook (reset! catcher ((resolve-hook :caught-hook caught-hook) default-caught))))

(defn middleware [handler]
  (fn [{:keys [op] :as msg}]
    (let [eval? (= op "eval")
          user-eval? (and eval? (not (:tool-eval msg)))]
      (when eval?
        (inspect/remember-session (:session msg)))
      (handler (if user-eval?
                 (cond-> msg
                   @evaluator (assoc :eval (str `hooked-eval))
                   @printer (assoc ::print/print (str `hooked-print))
                   @catcher (assoc ::caught/caught (str `hooked-caught)))
                 msg)))))

;; Order the middleware after session resolution and before the nrepl eval handler
(set-descriptor! #'middleware
  {:requires #{"clone"} :expects #{"eval"} :handles {}})
