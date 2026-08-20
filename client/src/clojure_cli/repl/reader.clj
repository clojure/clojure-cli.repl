(ns clojure-cli.repl.reader
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
  (let [nl (.indexOf src "\n" (int start))]
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

(defn form-at-cursor
  "The innermost complete form the cursor is in, the whole buffer when it is
   itself complete, nil when neither."
  [src cursor]
  (or (some (fn [[open close]]
              (when (<= open cursor close) (subs src open close)))
            (:spans (bracket-scan src)))
      (when (complete? src) src)))

(defn token-char? [c]
  (not (#{\space \tab \newline \, \( \) \[ \] \{ \} \" \; \' \` \~ \@ \^ \\} c)))

(defn token-start [src i]
  (- i (count (take-while token-char? (reverse (subs src 0 i))))))

(defn token-end [src i]
  (+ i (count (take-while token-char? (subs src i)))))

(defn token-at-cursor
  "The symbol token at the cursor or immediately prior, nil when not valid"
  [^String src cursor]
  (let [step-back? (or (= cursor (.length src))
                       (#{\space \) \] \}} (.charAt src cursor)))
        pos (if step-back? (dec cursor) cursor)
        on-token? (and (<= 0 pos) (token-char? (.charAt src pos)))]
    (when on-token?
      (subs src (token-start src pos) (token-end src pos)))))

(defn indent-column
  "Spaces to indent a continuation line"
  [^String src cursor]
  (let [{:keys [spans unmatched]} (bracket-scan src)
        enclosing (filter (fn [[open close]] (< open cursor close)) spans)
        openers (filter #(#{\( \[ \{} (.charAt src %)) unmatched)
        open (or (ffirst enclosing)
                 (when (seq openers) (apply max openers)))]
    (if open
      (- open (.lastIndexOf src "\n" (int open)))
      0)))
