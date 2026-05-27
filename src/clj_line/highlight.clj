(ns clj-line.highlight
  (:require
    [clj-line.color :refer [palette]]
    [clj-line.reader :as reader])
  (:import
    [org.jline.reader Highlighter]
    [org.jline.utils AttributedStringBuilder AttributedStyle]))

(defn highlight-clj [^String buffer]
  (let [bad (reader/unmatched-brackets buffer)
        ^AttributedStyle bad-style (:bad-bracket palette)
        asb (AttributedStringBuilder.)]
    (dotimes [i (.length buffer)]
      (.append asb (.substring buffer i (inc i))
               (if (bad i) bad-style AttributedStyle/DEFAULT)))
    (.toAttributedString asb)))

(defn clojure-highlighter []
  (reify Highlighter
    (highlight [_ _reader buffer] (highlight-clj buffer))
    (setErrorPattern [_ _])
    (setErrorIndex   [_ _])))
