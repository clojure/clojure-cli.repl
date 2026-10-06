(ns clojure-cli.repl.inspect
  (:require
    [clojure.datafy :refer [datafy nav]]
    [clojure.pprint :as pprint]
    [clojure.string :as str]
    [nrepl.middleware :refer [set-descriptor!]]
    [nrepl.misc :refer [response-for]]
    [nrepl.transport :as transport]))

(set! *warn-on-reflection* true)

(def view (atom nil))
(def max-entries 1000)
(def min-col-width 12)
(def separator " · ")

(def sessions
  "Session id to session atom of eval bindings."
  (atom {}))

(defn remember-session [session]
  (swap! sessions assoc (:id (meta session)) session))

(defn session-binding [session-id dynamic-var]
  (when-let [session (get @sessions session-id)] ;; prevents NPE if you inspect in a fresh repl
    (get @session dynamic-var)))

(defn entries [value]
  (let [capped-entries (take max-entries value)
        map-value? (map? value)
        set-value? (instance? java.util.Set value)
        list-value? (or (sequential? value) (instance? java.util.List value))]
    (cond
      map-value? (mapv (fn [[key child]]
                         {:key key :label (pr-str key) :child child})
                       capped-entries)
      set-value? (vec (map-indexed (fn [i child]
                                     {:key child :label (str i) :child child})
                                   capped-entries))
      list-value? (vec (map-indexed (fn [i child]
                                      {:key i :label (str i) :child child})
                                    capped-entries)))))

(defn preview [value width]
  (let [s (binding [*print-length* 8 *print-level* 4] (pr-str value))]
    (if (> (count s) width)
      (str (subs s 0 (max 0 (dec width))) "…") ;; a label can be wider than the terminal
      s)))

(defn type-name [value]
  (if (nil? value) "nil" (.getSimpleName (class value))))

(defn table-keys [children]
  (when (seq children)
    (let [uniform-maps? (and (every? map? children)
                             (apply = (map #(set (keys %)) children)))
          uniform-vectors? (and (every? vector? children)
                                (apply = (map count children)))]
      (cond
        uniform-maps? (keys (first children))
        uniform-vectors? (range (count (first children)))))))

(defn format-cells [label label-width cells col-width]
  (let [left-align (fn [s width] (format (str "%-" width "s") s))]
    (apply str (left-align label label-width)
           (map (fn [cell] (str " " (left-align (preview cell (- col-width 2)) (dec col-width))))
                cells))))

(defn format-table [visible-entries cols]
  (when-let [column-keys (table-keys (map :child visible-entries))]
    (let [label-width (apply max (map #(count (:label %)) visible-entries))
          remaining-width (- cols label-width)
          max-columns (quot remaining-width min-col-width)
          shown-columns (take max-columns column-keys)
          columns-cut? (< (count shown-columns) (count column-keys))]
      (when (seq shown-columns)
        (let [col-width (quot remaining-width (count shown-columns))]
          {:header (format-cells "" label-width shown-columns col-width)
           :rows (mapv (fn [{:keys [label child]}]
                         (format-cells label label-width (map #(get child %) shown-columns) col-width))
                       visible-entries)
           :cols-note (when columns-cut? (str (count shown-columns) " of " (count column-keys) " cols"))})))))

(defn list-row [cols {:keys [label child]}]
  (str label separator (preview child (- cols (count label) (count separator)))))

(defn meta-frame-index [stack]
  (first (keep-indexed (fn [i frame] (when (= 'meta (:key frame)) i)) stack)))

(defn render []
  (let [{:keys [stack page rows cols table]} @view
        value (:value (peek stack))
        all-entries (entries value)
        path (str/join " / " (map :label stack))
        meta-action (cond
                      (meta-frame-index stack) "hide"
                      (meta value) "meta")]
    (if all-entries
      (let [total (count all-entries)
            start (min (* page rows) total)
            visible-entries (subvec all-entries start (min (+ start rows) total))
            formatted-table (format-table visible-entries cols)
            table-view? (and table (some? formatted-table))
            cols-note (when table-view? (:cols-note formatted-table))]
        {:path path
         :alt-view (when formatted-table (if table-view? "list" "table"))
         :meta-action meta-action
         :info (str (type-name value) separator
                    (if (= total max-entries) (str max-entries "+ items") (str total " items"))
                    (when cols-note (str separator cols-note)))
         :start start
         :total total
         :header (if table-view? (:header formatted-table) "")
         :entries (if table-view?
                    (:rows formatted-table)
                    (mapv #(list-row cols %) visible-entries))})
      {:path path
       :info (type-name value)
       :meta-action meta-action
       :header ""
       :leaf (pprint/write value :stream nil :length 100 :level 10)})))

(defn respond
  ([] (render))
  ([extras] (merge (render) extras)))

(defn start [session-id rows cols]
  (reset! view {:stack [{:label "*1" :value (datafy (session-binding session-id #'clojure.core/*1))}]
                :page 0 :rows rows :cols cols :table true})
  (respond))

(defn toggle-table []
  (if (:alt-view (render))
    (do (swap! view update :table not) (respond))
    (respond {:note "not tabular"})))

(defn in [i]
  (let [{:keys [stack page rows]} @view
        current-frame (peek stack)
        value (:value current-frame)
        {:keys [key child label]} (get (entries value) i)
        new-stack (conj (pop stack)
                        (assoc current-frame :cursor (- i (* page rows)) :page page)
                        {:label label :key key :value (datafy (nav value key child))})]
    (swap! view #(assoc % :stack new-stack :page 0))
    (respond)))

(defn out []
  (let [{:keys [stack]} @view]
    (if (= (count stack) 1)
      (respond)
      (let [remaining (pop stack)
            frame (peek remaining)]
        (swap! view #(assoc % :stack remaining :page (:page frame)))
        (respond {:cursor (:cursor frame)})))))

(defn page [delta]
  (swap! view (fn [{:keys [stack page rows] :as state}]
                (let [total (count (entries (:value (peek stack))))
                      max-page (quot (dec total) rows)
                      next-page (-> (+ page delta) (max 0) (min max-page))]
                  (assoc state :page next-page))))
  (respond))

(defn toggle-meta [cursor]
  (let [{:keys [stack page]} @view
        meta-index (meta-frame-index stack)
        current-frame (peek stack)
        meta-map (datafy (meta (:value current-frame)))]
    (cond
      meta-index (let [remaining (subvec stack 0 meta-index)
                       origin-frame (peek remaining)]
                   (swap! view #(assoc % :stack remaining :page (:page origin-frame)))
                   (respond {:cursor (:cursor origin-frame)}))
      (nil? meta-map) (respond {:cursor cursor :note "no metadata"})
      :else (let [new-stack (conj (pop stack)
                                  (assoc current-frame :cursor cursor :page page)
                                  {:label "^meta" :key 'meta :value meta-map})]
              (swap! view #(assoc % :stack new-stack :page 0))
              (respond)))))

(defn def-as [session-id var-name]
  (let [{:keys [stack]} @view
        target-ns (session-binding session-id #'clojure.core/*ns*)
        var-symbol (with-meta (symbol var-name) {:browse-path (mapv :key (rest stack))})
        result (try {:defd (str (intern target-ns var-symbol (:value (peek stack))))}
                    (catch Throwable e {:note (ex-message e)}))]
    (respond result)))

(def op-fns
  {"inspect-start"        (fn [msg] (start (:session msg) (:rows msg) (:cols msg)))
   "inspect-in"           (fn [msg] (in (:idx msg)))
   "inspect-out"          (fn [_]   (out))
   "inspect-page"         (fn [msg] (page (:delta msg)))
   "inspect-toggle-meta"  (fn [msg] (toggle-meta (:cursor msg)))
   "inspect-toggle-table" (fn [_]   (toggle-table))
   "inspect-def"          (fn [msg] (def-as (:session msg) (:name msg)))})

(defn middleware [handler]
  (fn [{:keys [op transport] :as msg}]
    (if-let [f (op-fns op)]
      (let [view (try (f msg)
                      (catch Throwable e (assoc (render) :note (ex-message e))))]
        (transport/send transport (response-for msg :view (pr-str view) :status ["done"])))
      (handler msg))))

(set-descriptor! #'middleware
  {:requires #{}
   :expects  #{}
   :handles  {"inspect-start" {} "inspect-in" {} "inspect-out" {}
              "inspect-page" {} "inspect-toggle-meta" {}
              "inspect-toggle-table" {} "inspect-def" {}}})
