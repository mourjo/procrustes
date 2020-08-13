(ns procrustes.client
  (:require [cheshire.core :as cc]
            [procrustes.env :as env]
            [clj-http.client :as http]
            [clj-statsd :as statsd]
            [clojure.tools.logging :as ctl]
            [com.climate.claypoole :as cp]
            [procrustes.utils :as utils]
            [clojure.java.jmx :as jmx])
  (:import [java.util.concurrent
            ExecutorService
            SynchronousQueue
            ThreadPoolExecutor
            ThreadPoolExecutor$CallerRunsPolicy
            TimeUnit]))

(defonce request-counter
  (atom {utils/non-load-shedding-server 0
         utils/load-shedding-server 0}))


(defn update-slow-poke-time
  [n-sec]
  (jmx/with-connection env/jmx-ls-conn
    (jmx/write! "me.mourjo:type=EnvBean" :SlowPokeTime (int n-sec)))
  (jmx/with-connection env/jmx-nls-conn
    (jmx/write! "me.mourjo:type=EnvBean" :SlowPokeTime (int n-sec)))
  (ctl/info "Updated slow poke time on servers to" n-sec "seconds"))


(defn grafana-load-start-annotation
  []
  (ctl/info "Running Grafana load start annotation")
  (http/post env/grafana-annotations-endpoint
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
  (http/post env/grafana-annotations-endpoint
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


(defn ^ExecutorService synchronous-pool
  [n]
  (ThreadPoolExecutor. ^int n
                       ^int n
                       (long 60)
                       TimeUnit/MINUTES
                       (SynchronousQueue.)
                       (ThreadPoolExecutor$CallerRunsPolicy.)))


(defn fire-away
  [server-type route n]
  (let [pool (cp/threadpool 500)]
    (dotimes [_ n]
      (Thread/sleep 50)
      (cp/future pool (http-get server-type route)))
    (await statsd/sockagt)
    pool))


(defn burst-non-load-shedding-server
  []
  (fire-away utils/non-load-shedding-server
             env/slow-nls-route
             600))


(defn burst-load-shedding-server
  []
  (fire-away utils/load-shedding-server
             env/slow-ls-route
             600))


(defn slow-continuous-load
  []
  (future
    (loop []
      (http-get utils/load-shedding-server env/slow-ls-route)
      (recur)))
  (future
    (loop []
      (http-get utils/non-load-shedding-server env/slow-nls-route)
      (recur))))


(defn burst
  "Send a burst of requests to both load shedding and non load shedding servers that lasts
  for a short time."
  []
  (env/setup-statsd)
  (slow-continuous-load)
  (Thread/sleep 30000)

  (with-grafana-annotations
    (ctl/info "Starting burst")
    (let [f1 (future (burst-load-shedding-server))
          f2 (future (burst-non-load-shedding-server))]
      (.shutdown ^ExecutorService @f1)
      (.shutdown ^ExecutorService @f2))
    (ctl/info "Finished burst")))


(defn steady
  "Fire requests to both the load shedding and non load shedding servers at a steady
  rate. After a while, it increases the time taken to process the slow route on both
  servers while keeping the same rate of requests."
  []
  (env/setup-statsd)
  (slow-continuous-load)
  (Thread/sleep 30000)
  (ctl/info "Starting steady stream")
  (update-slow-poke-time 4)
  (grafana-load-start-annotation)
  (let [p (promise)
        n 500
        _ (.addShutdownHook (Runtime/getRuntime)
                            (Thread. ^Runnable (fn []
                                                 (when-not (realized? p)
                                                   (grafana-load-end-annotation)
                                                   (update-slow-poke-time 3)))))
        f1 (future
             (cp/with-shutdown! [pool (cp/threadpool 40)]
               (loop [i n]
                 (cp/future pool
                            (http-get utils/load-shedding-server
                                      env/slow-ls-route))
                 (cp/future pool
                            (http-get utils/non-load-shedding-server
                                      env/slow-nls-route))
                 (Thread/sleep 750)
                 (if (pos? i)
                   (recur (dec i))
                   (ctl/info "Finished slow routes")))))
        f2 (future
             (cp/with-shutdown! [pool (cp/threadpool 40)]
               (loop [i n]
                 (cp/future pool
                            (http-get utils/load-shedding-server
                                      env/fast-ls-route))
                 (cp/future pool
                            (http-get utils/non-load-shedding-server
                                      env/fast-nls-route))
                 (Thread/sleep 750)
                 (if (pos? i)
                   (recur (dec i))
                   (ctl/info "Finished fast routes")))))]
    @f1 @f2
    (deliver p ::done)
    (grafana-load-end-annotation)
    (update-slow-poke-time 3)))
