(ns clj-line.highlight
  (:require
    [clj-line.color :as color]
    [clj-line.reader :as reader])
  (:import
    [org.jline.reader Highlighter]
    [org.jline.utils AttributedStringBuilder AttributedStyle]))

(set! *warn-on-reflection* true)

(defn highlight-clj [^String buffer]
  (let [bad (reader/unmatched-brackets buffer)
        ^AttributedStyle bad-style (color/build-style {:fg :red :bold true})
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
