(ns procrustes.utils
  (:require [procrustes.middleware :as app-middleware]
            [clojure.tools.logging :as ctl])
  (:import (java.util.concurrent ThreadFactory BlockingQueue)
           (org.eclipse.jetty.util.thread QueuedThreadPool)))

(def load-shedding-server "LS")
(def non-load-shedding-server "NON-LS")

(defn now-secs []
  (int (/ (System/currentTimeMillis) 1000)))


(defn mean
  [stats]
  (reduce-kv (fn [acc k v]
               (if (= k "/")
                 acc
                 (str (format "%s %s=%2.2f" acc k (/ (reduce + v) (count v))) "s")))
             ""
             stats))

(defn log-load
  ([server-type ^QueuedThreadPool jetty-pool every-secs]
   (log-load server-type nil jetty-pool every-secs))
  ([server-type ^BlockingQueue tp-queue ^QueuedThreadPool jetty-pool every-secs]
   (ctl/info "\n\n\n*** Server-type:" server-type "***")
   (loop []
     (let [stats (mean @app-middleware/timers)]
       (if tp-queue
         (ctl/info
           (format "[%s] Open: %d, Completed: %d, Handler queue: %d, Jetty queue: %d, %s, "
                   server-type
                   @app-middleware/open-requests
                   @app-middleware/completed-requests
                   (.size tp-queue)
                   (.getQueueSize jetty-pool)
                   stats))
         (ctl/info
           (format "[%s] Open: %d, Completed: %d, Jetty queue: %d, %s"
                   server-type
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
