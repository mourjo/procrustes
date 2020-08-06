(ns procrustes.core
  (:require [ring.adapter.jetty :as jetty]
            [procrustes.utils :as utils]
            [ring.middleware.defaults :as default-middleware]
            [procrustes.handlers :as handlers]
            [procrustes.env :as env]
            [procrustes.middleware :as app-middleware]
            [compojure.core :refer [ANY defroutes]]
            [compojure.middleware :as compojure-middleware]
            [compojure.route :refer [not-found]]
            [clojure.tools.logging :as ctl])
  (:gen-class)
  (:import (java.util.concurrent ExecutorService ThreadPoolExecutor TimeUnit BlockingQueue RejectedExecutionException)
           (me.mourjo RunnableQueueBuilder)
           (java.lang.management ManagementFactory)
           (org.eclipse.jetty.jmx MBeanContainer ConnectorServer)
           (org.eclipse.jetty.server.handler StatisticsHandler)
           (org.eclipse.jetty.server ConnectorStatistics ServerConnectionStatistics)
           (javax.management.remote JMXServiceURL)
           (org.eclipse.jetty.io EofException)))

(def open-requests (agent 0))
(def completed-requests (agent 0))
(def max-allowed-delay-sec 5)
(def max-pending-requests 20)

(defonce ^BlockingQueue tp-queue (RunnableQueueBuilder/buildQueue max-pending-requests))
(defonce ^ExecutorService request-processor-pool
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


(defn process-request
  [handler-fn response-callback request-map]
  (try (send open-requests inc)
       (response-callback (handler-fn request-map))
       (finally (send completed-requests inc))))


(defn drop-request
  [error-callback request-map]
  (ctl/info "Shedding load: " (:uri request-map))
  ;;(error-callback (ex-info "Request expired, the client should not see this" {}))
  )


(defn delayed-request?
  [request-map]
  (> (- (utils/now-secs) (int (/ (:request-ts-millis request-map) 1000)))
     max-allowed-delay-sec))


(defn hand-off-request
  [handler-fn response-callback error-callback request-map]
  ;; no binding conveyance required here
  (.submit request-processor-pool
           ^Runnable (fn []
                       (try (if (delayed-request? request-map)
                              (drop-request error-callback request-map)
                              (process-request handler-fn response-callback request-map))
                            (catch EofException t
                              ;; connection has been closed by the client
                              (ctl/error t))
                            (catch IllegalStateException t
                              ;; request lifecycle changed, async timeout handler has already closed the request
                              )
                            (catch Throwable t
                              (ctl/error t))))))


(defn async-to-sync
  [handler-fn]
  (fn [request-map response-callback error-callback]
    (try (hand-off-request handler-fn response-callback error-callback request-map)
         (catch RejectedExecutionException _
           (ctl/info "Exceeded capacity, dropping request")
           (response-callback
             {:status 429 :body "<h1>Too many requests</h1>"}))
         (catch Throwable t
           (ctl/error t)))))


(defonce app
         (-> routes
             compojure-middleware/wrap-canonical-redirect
             (default-middleware/wrap-defaults default-middleware/site-defaults)
             app-middleware/wrap-request-id
             async-to-sync))

(defonce control-app
         (-> routes
             compojure-middleware/wrap-canonical-redirect
             (default-middleware/wrap-defaults default-middleware/site-defaults)
             app-middleware/wrap-request-id))


(defn -main
  [& _]
  (env/start-mbean-server)
  ;(let [jetty (jetty/run-jetty app
  ;                              {:port                  3100
  ;                               :join?                 true
  ;                               :async?                true
  ;                               :async-timeout         (* 1000 max-allowed-delay-sec)
  ;                               :async-timeout-handler (fn [_]
  ;                                                        {:status 504
  ;                                                         :body   "<h1>Request timed out, mate</h1>"})
  ;                               :max-threads           5
  ;                               :min-threads           1
  ;                               :max-queued-requests   8   ;; <--- doesn't matter
  ;                               })]
  ;  (utils/log-load open-requests completed-requests tp-queue (:pool jetty) 1000))

  ;; copied from Moby:
  (let [jetty (jetty/run-jetty control-app
                         {:port                3200
                          :join?               false
                          :max-queued-requests 500
                          :max-threads         8
                          })]
    (utils/log-load open-requests completed-requests nil (:pool jetty) 1000))
  )