(ns async-jetty.utils
  (:require [clojure.tools.logging :as ctl])
  (:import (java.util.concurrent ThreadFactory)))


(defn now-secs []
  (int (/ (System/currentTimeMillis) 1000)))


(defn log-load
  [open-requests completed-requests tp-queue every-secs]
  (Class/forName "org.apache.log4j.Logger")
  (doto
    (Thread. ^Runnable
             (fn []
               (ctl/info (format "Open: %d, Completed: %d, Thread-pool queue: %d"
                                 @open-requests
                                 @completed-requests
                                 (.size tp-queue)))
               (Thread/sleep every-secs)
               (recur)))
    (.setDaemon true)
    (.start)))


(let [counter (atom 0)]
  (defn ^ThreadFactory create-thread-factory
    [thread-name]
    (proxy [ThreadFactory] []
      (newThread [^Runnable runnable]
        (let [t (Thread. runnable)]
          (.setName t (str thread-name (swap! counter inc)))
          t)))))
