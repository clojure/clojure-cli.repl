(ns clj-line.reader
  (:require
    [clojure.tools.reader :as r]
    [clojure.tools.reader.reader-types :as rt])
  (:import
    [org.jline.reader EOFError Parser$ParseContext]
    [org.jline.reader.impl DefaultParser]))

(set! *warn-on-reflection* true)

(defn complete?
  "True unless src needs more input to finish.
  Finished but invalid input returns true."
  [src]
  (try
    (let [rdr (rt/indexing-push-back-reader src)]
      (loop []
        (let [form (r/read {:eof ::eof} rdr)]
          (if (identical? form ::eof) true (recur)))))
    (catch clojure.lang.ExceptionInfo e
      (not= :eof (:ex-kind (ex-data e))))
    (catch Exception _ true)))

(defn read-all
  "Reads all top level forms from src into a vector. Read errors propagate
   to surface invalid input to the repl loop."
  [src]
  (let [rdr (rt/indexing-push-back-reader src)]
    (loop [acc []]
      (let [form (r/read {:eof ::eof} rdr)]
        (if (identical? form ::eof) acc (recur (conj acc form)))))))

(defn clojure-parser
  "On Enter, ACCEPT_LINE, throws EOFError for incomplete input so JLine will
   read multiple lines. Every other case, complete input, or any other context
   such as tab completion, falls through to DefaultParser."
  []
  (let [default (DefaultParser.)]
    (proxy [DefaultParser] []
      (parse [line cursor context]
        (when (and (= context Parser$ParseContext/ACCEPT_LINE) (not (complete? line)))
          (throw (EOFError. -1 -1 "Incomplete form" "...")))
        (.parse default line cursor context)))))

(defn skip-string
  "Given the index of an opening quote, return the index one after
   the closing quote. Handles backslash escapes and returns len if the
   string is unterminated."
  ^long [^String src start]
  (let [len (.length src)]
    (loop [pos (inc start)]
      (cond
        (>= pos len) pos ; unterminated
        (= (.charAt src pos) \\) (recur (min len (+ pos 2))) ; escape skip next char
        (= (.charAt src pos) \") (inc pos)
        :else (recur (inc pos))))))

(defn skip-comment
  "Given the index of the ; in src, returns the index of
   the newline, or src length if no newline."
  ^long [^String src start]
  (let [nl (.indexOf src (int \newline) (int start))]
    (if (neg? nl) (.length src) nl)))

(def closer-of {\( \), \[ \], \{ \}})

(defn bracket-scan
  "Returns the open/close index of each matched bracket in :spans, innermost
   first, and the index of each bracket with no partner under :unmatched.
   Brackets inside strings and comments are not accounted for."
  [^String src]
  (let [len (.length src)]
    (loop [pos 0, stack [], spans [], bad #{}]
      (if (>= pos len)
        {:spans spans :unmatched (into bad (map first) stack)}
        (let [c (.charAt src pos)]
          (cond
            (= c \;)        (recur (skip-comment src pos) stack spans bad)
            (= c \")        (recur (skip-string src pos) stack spans bad)
            (= c \\)        (recur (min len (+ pos 2)) stack spans bad)
            (#{\( \[ \{} c) (recur (inc pos) (conj stack [pos (closer-of c)]) spans bad)
            (#{\) \] \}} c) (let [[open closer] (peek stack)]
                              (if (and closer (= c ^Character closer))
                                (recur (inc pos) (pop stack) (conj spans [open (inc pos)]) bad)
                                (recur (inc pos) stack spans (conj bad pos))))
            :else           (recur (inc pos) stack spans bad)))))))

(defn indent-column
  "Spaces to indent a continuation line"
  [^String src cursor]
  (let [{:keys [spans unmatched]} (bracket-scan src)
        enclosing (filter (fn [[open close]] (< open cursor close)) spans)
        openers (filter #(#{\( \[ \{} (.charAt src %)) unmatched)
        open (or (ffirst enclosing)
                 (when (seq openers) (apply max openers)))]
    (if open
      (- open (.lastIndexOf src (int \newline) (int open)))
      0)))
