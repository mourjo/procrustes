(ns procrustes.client
  (:require [clj-http.client :as http]
            [procrustes.utils :as utils]
            [clojure.tools.logging :as ctl]
            [com.climate.claypoole :as cp]
            [clj-statsd :as statsd]))

(defonce request-counter
  (atom {utils/non-load-shedding-server 0
         utils/load-shedding-server 0}))


(defn http-get
  [server-type route]
  (let [id (get (swap! request-counter update server-type inc) server-type)]
    (try
      (statsd/with-timing (str server-type "_client_time")
        (let [response (http/get route {:throw-exceptions false})]
          (statsd/increment (str server-type ".response_code." (:status response)))
          (ctl/info (format "Request %d completed for" id)
                    server-type
                    route)
          response))
      (catch Exception e
        (statsd/increment (str server-type ".response_code.FAIL"))
        (ctl/error (format "Request %d completed for" id)
                   server-type
                   route
                   (.getMessage e))))))


(defn fire-away
  [server-type route n]
  (cp/with-shutdown! [pool (cp/threadpool 500)]
    (let [futures (cp/upmap pool
                            (fn [_]
                              (Thread/sleep (+ 500 (rand-int 1000)))
                              (http-get server-type route))
                            (range n))]
      (mapv identity futures)
      (await statsd/sockagt))))


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
  (ctl/info "Starting burst")
  (let [f1 (future (burst-load-shedding-server))
        f2 (future (burst-non-load-shedding-server))]
    @f1 @f2)
  (ctl/info "Finished burst"))


(defn steady
  []
  (statsd/setup "localhost" 8125)
  (ctl/info "Starting steady stream")
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
