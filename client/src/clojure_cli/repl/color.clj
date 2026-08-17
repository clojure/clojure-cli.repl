(ns clojure-cli.repl.color
  (:import
    [org.jline.utils AttributedString AttributedStringBuilder AttributedStyle]))

(set! *warn-on-reflection* true)

(def colors
  {:black          AttributedStyle/BLACK
   :red            AttributedStyle/RED
   :green          AttributedStyle/GREEN
   :yellow         AttributedStyle/YELLOW
   :blue           AttributedStyle/BLUE
   :magenta        AttributedStyle/MAGENTA
   :cyan           AttributedStyle/CYAN
   :white          AttributedStyle/WHITE
   :bright-black   (+ AttributedStyle/BLACK   AttributedStyle/BRIGHT)
   :bright-red     (+ AttributedStyle/RED     AttributedStyle/BRIGHT)
   :bright-green   (+ AttributedStyle/GREEN   AttributedStyle/BRIGHT)
   :bright-yellow  (+ AttributedStyle/YELLOW  AttributedStyle/BRIGHT)
   :bright-blue    (+ AttributedStyle/BLUE    AttributedStyle/BRIGHT)
   :bright-magenta (+ AttributedStyle/MAGENTA AttributedStyle/BRIGHT)
   :bright-cyan    (+ AttributedStyle/CYAN    AttributedStyle/BRIGHT)
   :bright-white   (+ AttributedStyle/WHITE   AttributedStyle/BRIGHT)})

(defn build-style
  "Build a JLine AttributedStyle from a style map, nil values yield the default style."
  [style-map]
  (let [{:keys [fg bg bold italic underline inverse]} style-map
        fg-code (colors fg)
        bg-code (colors bg)]
    (cond-> AttributedStyle/DEFAULT
      fg-code   (.foreground fg-code)
      bg-code   (.background bg-code)
      bold      (.bold)
      italic    (.italic)
      underline (.underline)
      inverse   (.inverse))))

(defn build-attrib-str
  "Build a styled JLine AttributedString from a vector of {:text :style} segments."
  ^AttributedString [segments]
  (let [asb (AttributedStringBuilder.)]
    (doseq [{:keys [text style]} segments]
      (.append asb ^String text ^AttributedStyle (build-style style)))
    (.toAttributedString asb)))
