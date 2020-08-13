(ns procrustes.core
  (:require [ring.adapter.jetty :as jetty]
            [procrustes.utils :as utils]
            [ring.middleware.defaults :as default-middleware]
            [procrustes.handlers :as handlers]
            [procrustes.env :as env]
            [procrustes.middleware :as app-middleware]
            [compojure.core :refer [ANY defroutes]]
            [compojure.route :refer [not-found]]
            [clojure.string :as cs]
            [clojure.tools.logging :as ctl])
  (:gen-class)
  (:import (java.util.concurrent ExecutorService
                                 ThreadPoolExecutor
                                 TimeUnit
                                 BlockingQueue
                                 RejectedExecutionException)
           (me.mourjo RunnableQueueBuilder)
           (org.eclipse.jetty.io EofException)))

(def max-allowed-delay-sec 6)
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
  (ANY "/" params (handlers/default-response params))
  (ANY "/slow" params (handlers/slow-poke params))
  (ANY "/fast" params (handlers/fast-poke params))
  (ANY "*" _ (not-found "Incorrect route")))


(defn drop-request
  [request-map]
  (ctl/info "Shedding load: " (:uri request-map)))


(defn delayed-request?
  [request-map]
  (> (- (utils/now-secs) (int (/ (:request-ts-millis request-map) 1000)))
     max-allowed-delay-sec))


(defn hand-off-request
  "Submit the handler to a threadpool for request processing."
  [handler-fn response-callback request-map]
  ;; no binding conveyance required here
  (.submit request-processor-pool
           ^Runnable (fn []
                       (try (if (delayed-request? request-map)
                              (drop-request request-map)
                              (response-callback (handler-fn request-map)))
                            (catch EofException t
                              ;; connection has been closed by the client
                              )
                            (catch IllegalStateException t
                              ;; request lifecycle changed, async timeout handler has already closed the request
                              )
                            (catch Throwable t
                              (ctl/error t))))))


(defn wrap-load-monitor
  "Convert a synchronous handler into an asynchronous handler for the purposes for
  shedding load."
  [handler-fn]
  (fn [request-map response-callback error-callback]
    (try (hand-off-request handler-fn response-callback request-map)
         (catch RejectedExecutionException _
           (drop-request request-map)
           (response-callback
            {:status 429 :body "<h1>Try again later</h1>"}))
         (catch Throwable t
           (ctl/error t)))))


(defonce default-app
  ;; The app that would be there without load shedding
  (-> routes
      (default-middleware/wrap-defaults default-middleware/site-defaults)
      app-middleware/wrap-request-id
      app-middleware/wrap-request-counter
      (app-middleware/wrap-server-type utils/non-load-shedding-server)
      app-middleware/wrap-exceptions))


(defonce load-shedding-app
  ;; Wrap the actual app with the load monitor
  (-> default-app
      (app-middleware/wrap-server-type utils/load-shedding-server)
      wrap-load-monitor))


(defn start-load-shedding-server
  "Starts the load shedding server"
  []
  (ctl/info "Staring load-shedding server")
  (let [jetty (jetty/run-jetty load-shedding-app
                               {:port                  3100
                                :join?                 false
                                :async?                true
                                :async-timeout         (* 1000 max-allowed-delay-sec)
                                :async-timeout-handler (fn [request-map respond-callback error-callback]
                                                         (respond-callback
                                                          {:status 504
                                                           :body   "<h1>Try again later</h1>"}))
                                :max-threads           8
                                :min-threads           1
                                :max-queued-requests   500  ;; <--- doesn't matter
                                })]
    (utils/log-load utils/load-shedding-server tp-queue (:pool jetty) 1000)))


(defn start-basic-server
  "Starts the non-load shedding server"
  []
  (ctl/info "Staring non-load-shedding server")
  (let [jetty (jetty/run-jetty default-app
                               {:port                3200
                                :join?               false
                                :max-queued-requests 500
                                ;; only difference from Moby:
                                :max-threads         8})]
    (utils/log-load utils/non-load-shedding-server (:pool jetty) 1000)))


(defn -main
  "Starting point to start one of the servers, if the environment variabel SHED_LOAD is
  set to false, start the non-load shedding server, otherwise start the load shedding
  server."
  [& _]
  (env/start)
  (if (= "TRUE" (cs/upper-case (or (System/getenv "SHED_LOAD") "false")))
    (start-load-shedding-server)
    (start-basic-server)))
