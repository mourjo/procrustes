(ns async-jetty.core
  (:require [ring.adapter.jetty :as jetty]
            [com.climate.claypoole :as cp]
            [async-jetty.utils :as utils]
            [ring.middleware.defaults :as default-middleware]
            [async-jetty.handlers :as handlers]
            [compojure.core :refer [ANY defroutes]]
            [compojure.middleware :as compojure-middleware]
            [compojure.route :refer [not-found]]
            [clojure.tools.logging :as ctl])
  (:gen-class)
  (:import (java.util.concurrent ExecutorService ThreadPoolExecutor TimeUnit ArrayBlockingQueue BlockingQueue)))

(def open-requests (agent 0))
(def completed-requests (agent 0))
(def max-wait-sec 4)
(def max-pending-allowed 20)

(defonce ^BlockingQueue tp-queue (ArrayBlockingQueue. 50 true))
(defonce request-processor-pool
         (let [tp (ThreadPoolExecutor.
                    5
                    5
                    60
                    TimeUnit/SECONDS
                    tp-queue
                    (utils/create-thread-factory "request-processor-pool-"))]
           (.addShutdownHook (Runtime/getRuntime)
                             (Thread. ^Runnable (fn [] (.shutdown ^ExecutorService tp))))
           tp))

(defroutes routes
           (ANY "/slow" params (handlers/slow-poke params))
           (ANY "/fast" params (handlers/fast-poke params))
           (ANY "*" _ (not-found "Incorrect route")))


(defn in-flight-requests
  []
  (- @open-requests @completed-requests))


(defn process-request
  [handler-fn response-callback request-map]
  (try (send open-requests inc)
       (response-callback (handler-fn request-map))
       (finally (send completed-requests inc))))


(defn drop-request
  [error-callback request-map]
  (ctl/info "Shedding load: " (:uri request-map))
  (error-callback (ex-info "Request expired, the client should not see this" {})))


(defn hand-off-request
  [handler-fn response-callback error-callback request-map]
  (let [ingestion-time (utils/now-secs)]
    (cp/future request-processor-pool
               (try (if (<= (- (utils/now-secs) ingestion-time) max-wait-sec)
                      (process-request handler-fn response-callback request-map)
                      (drop-request error-callback request-map))
                    (catch Throwable t
                      (error-callback t))))))


(defn async-to-sync
  [handler-fn]
  (fn [request-map response-callback error-callback]
    (let [pending-requests (+ (in-flight-requests)
                              (.size tp-queue))]
      (if (<= pending-requests max-pending-allowed)
        (hand-off-request handler-fn response-callback error-callback request-map)
        (error-callback (ex-info "Too many requests"
                                 {:queued  pending-requests
                                  :request request-map}))))))


(defonce app
         (-> routes
             compojure-middleware/wrap-canonical-redirect
             (default-middleware/wrap-defaults default-middleware/site-defaults)
             async-to-sync))


(defn -main
  [& args]
  (utils/log-load open-requests completed-requests tp-queue 1000)
  (jetty/run-jetty app
                   {:port                3100
                    :join?               true
                    :async?              true
                    :async-timeout       (* 1000 max-wait-sec)
                    :max-threads         5
                    :min-threads         1
                    :max-queued-requests 50}))
