(ns procrustes.client
  (:require [cheshire.core :as cc]
            [clj-http.client :as http]
            [clj-statsd :as statsd]
            [clojure.tools.logging :as ctl]
            [com.climate.claypoole :as cp]
            [procrustes.utils :as utils])
  (:import [java.util.concurrent TimeUnit SynchronousQueue ThreadPoolExecutor$CallerRunsPolicy ThreadPoolExecutor]))

(defonce request-counter
  (atom {utils/non-load-shedding-server 0
         utils/load-shedding-server 0}))


(defn grafana-load-start-annotation
  []
  (ctl/info "Running Grafana load start annotation")
  (http/post "http://localhost/api/annotations"
             {:throw-exceptions false
              :content-type :json
              :basic-auth ["admin" "admin"]
              :body (cc/generate-string
                     {:time (System/currentTimeMillis)
                      :timeEnd (System/currentTimeMillis)
                      :tags ["load_start"]
                      :text "Load starting"})}))


(defn grafana-load-end-annotation
  []
  (ctl/info "Running Grafana load end annotation")
  (http/post "http://localhost/api/annotations"
             {:throw-exceptions false
              :content-type :json
              :basic-auth ["admin" "admin"]
              :body (cc/generate-string
                     {:time (System/currentTimeMillis)
                      :timeEnd (System/currentTimeMillis)
                      :tags ["load_end"]
                      :text "Load ending"})}))


(defmacro with-grafana-annotations
  [& forms]
  `(do
     (grafana-load-start-annotation)
     ~@forms
     (grafana-load-end-annotation)))


(defn http-get
  [server-type route]
  (let [id (get (swap! request-counter update server-type inc) server-type)]
    (try
      (statsd/with-timing (str server-type "_client_time")
        (statsd/increment (str server-type ".client_request_fired"))
        (let [response (http/get route {:throw-exceptions false})]
          (statsd/increment (str server-type ".response_code." (:status response)))
          (ctl/info (format "Request %d completed for" id)
                    server-type
                    route)
          (when (= 500 (:status response))
            (ctl/error (format "[%s] Got 500 status\n" server-type)
                       (with-out-str (clojure.pprint/pprint response))))
          response))
      (catch Exception e
        (statsd/increment (str server-type ".response_code.FAIL"))
        (ctl/error (format "Request %d completed for" id)
                   server-type
                   route
                   (.getMessage e))))))


(defn synchronous-pool
  [n]
  (ThreadPoolExecutor. ^int n
                       ^int n
                       (long 60)
                       TimeUnit/MINUTES
                       (SynchronousQueue.)
                       (ThreadPoolExecutor$CallerRunsPolicy.)))


(defn fire-away
  [server-type route n]
  (cp/with-shutdown! [pool (synchronous-pool 500)]
    (dotimes [_ n]
      (Thread/sleep 50)
      (cp/future pool (http-get server-type route)))
    (await statsd/sockagt)))


(defn burst-non-load-shedding-server
  []
  (fire-away utils/non-load-shedding-server
             "http://localhost:3200/slow"
             1000))


(defn burst-load-shedding-server
  []
  (fire-away utils/load-shedding-server
             "http://localhost:3100/slow"
             1000))


(defn slow-continuous-load
  []
  (future
    (loop []
      (http-get utils/load-shedding-server "http://localhost:3100/slow")
      (recur)))
  (future
    (loop []
      (http-get utils/non-load-shedding-server "http://localhost:3200/slow")
      (recur))))


(defn burst
  []
  (statsd/setup "localhost" 8125)
  (slow-continuous-load)
  (Thread/sleep 120000)

  (with-grafana-annotations
    (ctl/info "Starting burst")
    (let [f1 (future (burst-load-shedding-server))
          f2 (future (burst-non-load-shedding-server))]
      @f1 @f2)
    (ctl/info "Finished burst")))


(defn steady
  []
  (statsd/setup "localhost" 8125)
  (slow-continuous-load)
  (Thread/sleep 30000)
  (ctl/info "Starting steady stream")
  (grafana-load-start-annotation)
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. ^Runnable grafana-load-end-annotation))
  (future
    (cp/with-shutdown! [pool (cp/threadpool 40)]
      (loop []
        (cp/future pool
                   (http-get utils/load-shedding-server
                             "http://localhost:3100/slow"))
        (cp/future pool
                   (http-get utils/non-load-shedding-server
                             "http://localhost:3200/slow"))
        (Thread/sleep 750)
        (recur))))

  (cp/with-shutdown! [pool (cp/threadpool 40)]
    (loop []
      (cp/future pool
                 (http-get utils/load-shedding-server
                           "http://localhost:3100/fast"))
      (cp/future pool
                 (http-get utils/non-load-shedding-server
                           "http://localhost:3200/fast"))
      (Thread/sleep 750)
      (recur))))
