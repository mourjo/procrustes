(ns procrustes.utils
  (:require [clojure.tools.logging :as ctl]
            [procrustes.middleware :as app-middleware])
  (:import (java.util.concurrent ThreadFactory BlockingQueue)
           (org.eclipse.jetty.util.thread QueuedThreadPool)))


(defn now-secs []
  (int (/ (System/currentTimeMillis) 1000)))


(defn log-load
  ([^QueuedThreadPool jetty-pool every-secs]
   (log-load nil jetty-pool every-secs))
  ([^BlockingQueue tp-queue ^QueuedThreadPool jetty-pool every-secs]
  (Class/forName "org.apache.log4j.Logger")
  (loop []
    (if tp-queue
      (ctl/info (format "Open: %d, Completed: %d, Handler queue: %d, Jetty queue: %d (approx)"
                        @app-middleware/open-requests
                        @app-middleware/completed-requests
                        (.size tp-queue)
                        (.getQueueSize jetty-pool)))
      (ctl/info (format "Open: %d, Completed: %d, Jetty queue: %d (approx)"
                        @app-middleware/open-requests
                        @app-middleware/completed-requests
                        (.getQueueSize jetty-pool))))
    (Thread/sleep every-secs)
    (recur))))


(let [counter (atom 0)]
  (defn ^ThreadFactory create-thread-factory
    [thread-name]
    (proxy [ThreadFactory] []
      (newThread [^Runnable runnable]
        (let [t (Thread. runnable)]
          (.setName t (str thread-name (swap! counter inc)))
          t)))))
