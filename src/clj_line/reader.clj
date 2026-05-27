(ns clj-line.reader
  (:require
    [clojure.tools.reader :as r]
    [clojure.tools.reader.reader-types :as rt])
  (:import
    [org.jline.reader EOFError]
    [org.jline.reader.impl DefaultParser]))

(defn complete?
  "True unless src needs more input to finish.
  Finished but invalid input returns true."
  [^String src]
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
  [^String src]
  (let [rdr (rt/indexing-push-back-reader src)]
    (loop [acc []]
      (let [form (r/read {:eof ::eof} rdr)]
        (if (identical? form ::eof) acc (recur (conj acc form)))))))

(defn clojure-parser
  "Throws EOFError on incomplete input so JLine will read multiple lines.
   Complete input falls through to DefaultParser."
  []
  (proxy [DefaultParser] []
    (parse [line cursor context]
      (when-not (complete? line)
        (throw (EOFError. -1 -1 "Incomplete form" "...")))
      (proxy-super parse line cursor context))))

(defn skip-string
  "Given the index of an opening quote, return the index one after
   the closing quote. Handles backslash escapes and returns len if the
   string is unterminated."
  [^String src start]
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
  [^String src start]
  (let [nl (.indexOf src (int \newline) (int start))]
    (if (neg? nl) (.length src) nl)))

(defn unmatched-brackets
   "Returns the set of positions in src where a bracket ([{}]) has no partner.
    Brackets inside strings, char literals, and comments are skipped."
  [^String src]
  (let [len (.length src)
        closer-of {\( \), \[ \], \{ \}}]
    (loop [pos 0, stack [], bad #{}]
      (if (>= pos len)
        (into bad (map first) stack)
        (let [c (.charAt src pos)]
          (cond
            (= c \;)        (recur (skip-comment src pos) stack bad)
            (= c \")        (recur (skip-string src pos) stack bad)
            (= c \\)        (recur (min len (+ pos 2)) stack bad)
            (#{\( \[ \{} c) (recur (inc pos) (conj stack [pos (closer-of c)]) bad)
            (#{\) \] \}} c) (if (= c (second (peek stack)))
                              (recur (inc pos) (pop stack) bad)
                              (recur (inc pos) stack (conj bad pos)))
            :else           (recur (inc pos) stack bad)))))))

(defn indent-column
  "Number of spaces a continuation line should be indented so the cursor
   sits one column past the last unclosed opener. Zero if the buffer
   has no unclosed openers."
  ^long [^String src]
  (let [openers (filter #(#{\( \[ \{} (.charAt src ^long %)) (unmatched-brackets src))]
    (if (empty? openers)
      0
      (let [innermost (long (apply max openers))
            last-nl (.lastIndexOf src (int \newline) innermost)]
        (- innermost last-nl)))))
