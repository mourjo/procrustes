(ns procrustes.utils
  (:require [clojure.tools.logging :as ctl]
            [procrustes.middleware :as app-middleware])
  (:import (java.util.concurrent ThreadFactory BlockingQueue)
           (org.eclipse.jetty.util.thread QueuedThreadPool)))


(defn now-secs []
  (int (/ (System/currentTimeMillis) 1000)))

(defn mean
  [stats]
  (reduce-kv (fn [acc k v]
               (format "%s -- %s: %3.2f sec" acc k (/ (reduce + v) (count v))))
             ""
             stats))


(defn log-load
  ([server-type ^QueuedThreadPool jetty-pool every-secs]
   (log-load server-type nil jetty-pool every-secs))
  ([server-type ^BlockingQueue tp-queue ^QueuedThreadPool jetty-pool every-secs]
   (Class/forName "org.apache.log4j.Logger")
   (println "\n\n\n***Server-type: " server-type " ***")
   (loop []
     (let [stats (mean @app-middleware/timers)]
       (if tp-queue
         (.print System/out (format "\rOpen: %3d, Completed: %3d, Handler queue: %3d, Jetty queue: %3d (approx), %s"
                                    @app-middleware/open-requests
                                    @app-middleware/completed-requests
                                    (.size tp-queue)
                                    (.getQueueSize jetty-pool)
                                    stats))
         (.print System/out (format "\rOpen: %3d, Completed: %3d, Jetty queue: %3d (approx), %s"
                                    @app-middleware/open-requests
                                    @app-middleware/completed-requests
                                    (.getQueueSize jetty-pool)
                                    stats))))
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
