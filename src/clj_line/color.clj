(ns clj-line.color
  (:import
    [org.jline.utils AttributedStyle]))

(def palette
  {:bad-bracket   (.bold (.foreground AttributedStyle/DEFAULT AttributedStyle/RED))
   :tap-label     (.foreground AttributedStyle/DEFAULT AttributedStyle/MAGENTA)
   :prompt-lambda (.bold (.foreground AttributedStyle/DEFAULT AttributedStyle/YELLOW))
   :prompt-ns     (.bold (.foreground AttributedStyle/DEFAULT AttributedStyle/BLUE))
   :prompt-suffix AttributedStyle/DEFAULT})
