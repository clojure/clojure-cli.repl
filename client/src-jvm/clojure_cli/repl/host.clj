(ns clojure-cli.repl.host
  (:require
    [clojure.java.shell :as sh]
    [clojure.string :as str]
    [clojure.tools.deps.config :as dc]))

(set! *warn-on-reflection* true)

(defn cli-classpath
  "CP for the config's deps.edn -Srepro excludes global ~/.clojure/deps.edn"
  [location lib]
  (when (.isFile (dc/data-file location lib "deps.edn"))
    (let [dir (dc/data-dir location lib)
          {:keys [exit out err]} (sh/sh "clojure" "-Srepro" "-A:clojure-cli.repl/client" "-Spath" :dir (str dir))]
      (if (zero? exit)
        (str/trim out)
        (println "clojure-cli.repl: could not resolve deps.edn in" (str dir) "-" (str/trim err))))))

(defn classpath-files [^java.io.File dir ^String cp]
  (map #(.toFile (.resolve (.toPath dir) ^String %))
       (.split cp java.io.File/pathSeparator)))

(defn config-classpath [location lib]
  (when-let [cp (cli-classpath location lib)]
    (classpath-files (dc/data-dir location lib) cp)))

(defn load-config
  "Puts the config classpath on a DynamicClassLoader installed as the thread's
   context loader. A new loader is required because the app loader cannot be
   extended. Returns the config map."
  [lib]
  (let [entries (distinct (mapcat #(config-classpath % lib) [:project :user]))]
    (when (seq entries)
      (let [thread (Thread/currentThread)
            loader (clojure.lang.DynamicClassLoader. (.getContextClassLoader thread))]
        (doseq [^java.io.File f entries]
          (.addURL loader (.toURL (.toURI f))))
        (.setContextClassLoader thread loader))))
  (dc/config lib))

(defn resolve-prompt
  "Resolves the configured :prompt var, or nil when none is configured."
  [config]
  (when-let [prompt (:prompt config)]
    (or (requiring-resolve prompt)
        (throw (ex-info (str "clojure-cli.repl: :prompt var not found: " prompt) {:prompt prompt})))))

(defn apply-keybindings
  [config reader]
  (when-let [keybindings (:keybindings config)]
    (if-let [f (requiring-resolve keybindings)]
      (f reader)
      (throw (ex-info (str "clojure-cli.repl: :keybindings var not found: " keybindings) {:keybindings keybindings})))))
