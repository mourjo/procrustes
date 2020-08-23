(ns procrustes.core
  (:gen-class)
  (:require [clojure.string :as cs]
            [clojure.tools.logging :as ctl]
            [compojure.core :refer [ANY defroutes]]
            [compojure.route :refer [not-found]]
            [procrustes.env :as env]
            [procrustes.handlers :as handlers]
            [procrustes.middleware :as app-middleware]
            [procrustes.utils :as utils]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.defaults :as default-middleware]
            [ring.util.response :as ring-response])
  (:import java.time.Instant
           java.time.temporal.ChronoUnit
           [java.util.concurrent
            BlockingQueue
            ExecutorService
            RejectedExecutionException
            ThreadPoolExecutor
            TimeUnit]
           me.mourjo.RunnableQueueBuilder
           org.eclipse.jetty.io.EofException))

(def max-allowed-delay-sec 6)
(def max-pending-requests 20)

;; this blocking queue limits the has a limited capacity, which translates to limiting the
;; number of concurrent requests in the system -- if this limit is reached, the thread
;; pool will reject new tasks which is essentially load shedding
(defonce ^BlockingQueue tp-queue (RunnableQueueBuilder/buildQueue max-pending-requests))

;; the above queue is used to build the request processor pool
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
  "Decide whether a request has waited in the queue for too long. Uses a new option in the
  Ring response map `:request-ts-millis` added in our forked version of Ring (in
  consideration for Ring version 2, see https://github.com/ring-clojure/ring/pull/410)"
  [request-map]
  (let [request-ingestion-ts (:request-ts-millis request-map)
        diff-secs (.between ChronoUnit/SECONDS
                            (Instant/ofEpochMilli ^long request-ingestion-ts)
                            (Instant/now))]
    (> diff-secs max-allowed-delay-sec)))


(defn hand-off-request
  "Submit the handler to a threadpool for request processing."
  [handler-fn response-callback request-map]
  ;; no binding conveyance required here, hence use a barebones submit method on the
  ;; threadpool
  (.submit request-processor-pool
           ^Runnable (fn []
                       (try (if (delayed-request? request-map)
                              (drop-request request-map)
                              (response-callback (handler-fn request-map)))
                            (catch EofException _
                              ;; connection has been closed by the client
                              )
                            (catch IllegalStateException _
                              ;; request lifecycle changed, async timeout handler has already
                              ;; closed the request
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
           ;; number of concurrent requests in the system > allowed limit
           (drop-request request-map)
           (-> {:body "<h1>Try again later</h1>"}
               (ring-response/status 429)
               response-callback))
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


(defn async-timeout-handler
  "Build the response when the request processing took too long (either due to processing
  of requests itself becoming slow, or due to queueing). This does not shed load for the
  server, but unblocks client connections, which is useful for graceful communication to
  clients as well as freeing up system resources that may be held up by long running
  connections."
  [request respond-callback error-callback]
  (-> {:body "<h1>Try again later</h1>"}
      (ring-response/status 503)
      (ring-response/header "Retry-After" 120)
      respond-callback))


(defn start-load-shedding-server
  "Starts the load shedding server"
  []
  (ctl/info "Staring load-shedding server")
  (let [jetty (jetty/run-jetty load-shedding-app
                               {:port                  3100
                                :join?                 false
                                :async?                true
                                :async-timeout         (* 1000 max-allowed-delay-sec)
                                :async-timeout-handler async-timeout-handler
                                :max-threads           8
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
                                :max-threads         8})]
    (utils/log-load utils/non-load-shedding-server (:pool jetty) 1000)))


(defn -main
  "Starting point to start one of the servers, if the environment variable SHED_LOAD is
  set to false, start the non-load shedding server, otherwise start the load shedding
  server."
  [& _]
  (env/start)
  (if (= "TRUE" (cs/upper-case (or (System/getenv "SHED_LOAD") "false")))
    (start-load-shedding-server)
    (start-basic-server)))
