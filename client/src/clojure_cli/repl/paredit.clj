(ns clojure-cli.repl.paredit
  (:require
    [clojure-cli.repl.api :as api]
    [clojure.string :as str]
    [rewrite-clj.paredit :as pe]
    [rewrite-clj.zip :as z])
  (:import
    [org.jline.reader LineReader]))

(set! *warn-on-reflection* true)

(defn rows
  "Splits on newlines and keeps any trailing empty strings. This accounts
  for a cursor past a newline on an empty row."
  [src]
  (str/split src #"\n" -1))

(defn as-rewrite-position
  "JLine sees the cursor as a zero based index into the buffer.
  rewrite-clj requires a one based [row col] position."
  [src idx]
  (let [rows-before (rows (subs src 0 idx))]
    [(count rows-before) (inc (count (last rows-before)))]))

(defn as-jline-index
  "A rewrite-clj position back to the zero based JLine index."
  [src [row col]]
  (let [rows-before (take (dec row) (rows src))]
    (+ (dec col) (reduce + (map #(inc (count %)) rows-before)))))

(def ops
  {:paredit/slurp-forward pe/slurp-forward-into
   :paredit/slurp-forward-fully pe/slurp-forward-fully-into
   :paredit/slurp-backward pe/slurp-backward-into
   :paredit/slurp-backward-fully pe/slurp-backward-fully-into
   :paredit/barf-forward pe/barf-forward
   :paredit/barf-backward pe/barf-backward
   :paredit/splice pe/splice
   :paredit/splice-killing-forward pe/splice-killing-forward
   :paredit/splice-killing-backward pe/splice-killing-backward
   :paredit/raise pe/raise
   :paredit/kill pe/kill
   :paredit/split pe/split
   :paredit/join pe/join
   :paredit/move-to-prev pe/move-to-prev
   :paredit/wrap-list #(pe/wrap-around % :list)
   :paredit/wrap-vector #(pe/wrap-around % :vector)
   :paredit/wrap-map #(pe/wrap-around % :map)
   :paredit/wrap-set #(pe/wrap-around % :set)})

(def following-form-ops
  "Config keys of ops that act on the form following the cursor rather than
  the form enclosing it"
  #{:paredit/splice :paredit/splice-killing-backward :paredit/raise
    :paredit/move-to-prev :paredit/wrap-list :paredit/wrap-vector
    :paredit/wrap-map :paredit/wrap-set})

(defn edit
  "Runs a paredit op over the buffer. The cursor is placed where the
  op left the zipper. The buffer is unchanged if it will not
  parse or the op does not apply."
  [^LineReader rdr op-key]
  (let [buf (.getBuffer rdr)
        src (.toString buf)
        locate (if (following-form-ops op-key) z/skip-whitespace identity)
        edited (try (some-> (z/of-string src {:track-position? true})
                            (z/find-last-by-pos (as-rewrite-position src (.cursor buf)))
                            locate
                            ((ops op-key)))
                    (catch Exception _ nil))]
    (when edited
      (let [^String out (z/root-string edited)]
        (.clear buf)
        (.write buf out)
        (.cursor buf (as-jline-index out (z/position edited)))))))

(defn install [config rdr]
  (doseq [op-key (keys ops)
          :let [keyseq (config op-key)]
          :when keyseq]
    (api/bind-key rdr (api/key-sequence keyseq) (api/widget #(edit rdr op-key)))))
