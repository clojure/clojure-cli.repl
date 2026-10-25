(ns clojure-cli.repl.watch
  (:require
    [clojure.java.io :as io]
    [nrepl.middleware :refer [set-descriptor!]]
    [nrepl.misc :refer [response-for]]
    [nrepl.transport :as transport]))

(set! *warn-on-reflection* true)

(def sessions
  "Session id to session atom of eval bindings."
  (atom {}))

(def latest
  "Id of the session that most recently started an eval."
  (atom nil))

(def watchers
  "Session id to the watch request from each watching client."
  (atom {}))

(defn session-id [session]
  (:id (meta session)))

(defn latest-binding [dynamic-var]
  (when-let [session (get @sessions @latest)] ;; nil before the first eval
    (get @session dynamic-var)))

(defn project-relative-path [path]
  (let [dir (.toPath (io/file (System/getProperty "user.dir")))
        file (.toPath (io/file path))]
    (str (if (.startsWith file dir) (.relativize dir file) file))))

(defn reply-to-watchers [eval-msg reply]
  (doseq [[watcher-id watch-request] @watchers]
    (try (transport/send (:transport watch-request) (response-for watch-request (assoc reply :eval-id (:id eval-msg))))
         (catch java.io.IOException _ (swap! watchers dissoc watcher-id)))))

(defn echoed-request [{:keys [op code column file-path] ns-name :ns}]
  (cond-> {:code (if (= op "load-file")
                   (str "(load-file " (pr-str (some-> file-path project-relative-path)) ")")
                   code)}
    ns-name (assoc :ns ns-name)
    column (assoc :column column)))

(defn watched-transport [{:keys [transport] :as eval-msg}]
  (reify transport/Transport
    (recv [_] (transport/recv transport))
    (recv [_ timeout] (transport/recv transport timeout))
    (send [this response]
      (transport/send transport response)
      (reply-to-watchers eval-msg (cond-> (dissoc response :id :session :status)
                                    (some #{"done" :done} (:status response)) (assoc :status ["eval-done"])))
      this)))

(defn track-eval [handler {:keys [session] :as msg}]
  (swap! sessions assoc (session-id session) session)
  (when (or (:file msg) (contains? @watchers (session-id session)))
    (reset! latest (session-id session)))
  (if (:file msg) ;; Unlike the client, editors send :file with evals
    (do (reply-to-watchers msg (echoed-request msg))
        (handler (assoc msg :transport (watched-transport msg))))
    (handler msg)))

(defn middleware [handler]
  (fn [{:keys [op session] :as msg}]
    (case op
      "watch" (swap! watchers assoc (session-id session) msg)
      ("eval" "load-file") (track-eval handler msg)
      (handler msg))))

(set-descriptor! #'middleware
  {:requires #{"clone"}
   :expects  #{"eval" "load-file"}
   :handles  {"watch" {}}})
