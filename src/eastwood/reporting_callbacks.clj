(ns eastwood.reporting-callbacks
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [eastwood.error-messages :as msgs]
            [eastwood.util :as util]))

(def last-cwd-shown (atom nil))

(defn print-warning [{:keys [warn-data] :as info} cwd]
  (when (not= cwd @last-cwd-shown)
    (reset! last-cwd-shown cwd)
    (println (format "Entering directory `%s'" cwd)))
  (println (format "%s:%s:%s: %s: %s"
                   (-> warn-data :uri-or-file-name str)
                   ;; Emacs compilation-mode default regex's
                   ;; do not recognize warning lines with
                   ;; nil instead of decimal numbers for
                   ;; line/col number.  Make up values if we
                   ;; don't know them.
                   (or (-> warn-data :line) "1")
                   (or (-> warn-data :column) "1")
                   (name (-> warn-data :linter))
                   (-> warn-data :msg))))

(defrecord PrintingReporter [opts warn-writer])

(defrecord SilentReporter [opts])

(defn printing-reporter [opts]
  (let [wrtr (java.io.PrintWriter. *out* true)
        warn-wrtr (if-let [out (:out opts)]
                    (io/writer out)
                    wrtr)]
    (->PrintingReporter opts warn-wrtr)))

(defn silent-reporter [opts]
  (->SilentReporter opts))

(defn lint-error [reporter error]
  (swap! (:errors reporter) conj error))

(defn debug [reporter thing msg]
  (when (util/debug? thing (:opts reporter))
    (println msg)))

(defn error [reporter e]
  (println "Linting failed:")
  (util/pst e nil)
  (flush)
  e)


(defn dispatch-fn [record & _]
  (type record))

(defmulti lint-warning dispatch-fn)
(defmulti analyzer-exception dispatch-fn)
(defmulti note dispatch-fn)

(defmethod lint-warning PrintingReporter [reporter warning]
  (binding [*out* (:warn-writer reporter)]
    (print-warning warning (-> reporter :opts :cwd))
    (flush)))

(defmethod analyzer-exception PrintingReporter [reporter exception]
  (println (str/join "\n" (:msgs exception)))
  (flush))

(defmethod note PrintingReporter [reporter msg]
  (println msg)
  (flush))

(defmethod note SilentReporter [reporter msg] )

(defmethod lint-warning SilentReporter [reporter warning])

(defmethod analyzer-exception SilentReporter [reporter exception])

(defn lint-warnings [reporter warnings]
  (doseq [warning warnings]
    (lint-warning reporter warning)))

(defn lint-errors [reporter namespace errors]
  (doseq [{:keys [exception warn-data] :as error} errors]
    (when exception
      (let [{:keys [msgs]} (msgs/format-exception namespace exception)]
        (doseq [msg msgs] (println msg))))
    (lint-error reporter (:warn-data error))))

(defn lint-exceptions [reporter exceptions]
  (doseq [exception exceptions]
    (lint-error reporter exception)))

(defn show-error [reporter linting-error]
  (let [message (msgs/error-msg linting-error)
        exception (-> linting-error :err-data :exception)]
    (note reporter message)
    (error reporter exception)
    (lint-error reporter exception)))
