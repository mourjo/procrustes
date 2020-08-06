(ns procrustes.middleware
  (:require [clojure.tools.logging :as ctl]))

(def open-requests (agent 0))
(def completed-requests (agent 0))
(def timers (agent (sorted-map)))
(defonce counter (atom 0))

(defn wrap-request-id
  [handler]
  (fn [request]
    (let [id (swap! counter inc)]
      (handler (assoc request :request-id id)))))


(defmacro time-secs
  [expr]
  `(let [start# (System/nanoTime)
         ret# ~expr]
     {:elapsed-secs (/ (double (- (System/nanoTime) start#)) 1000000000.0)
      :result       ret#}))


(defn wrap-request-counter
  [handler]
  (fn [request]
    (try (send open-requests inc)
         (let [{:keys [result elapsed-secs]} (time-secs (handler request))]
           (send timers
                 update
                 (:uri request)
                 (fn [curr]
                   (take 20 (conj (or curr []) elapsed-secs))))
           result)
         (finally (send completed-requests inc)))))


(defn wrap-exceptions
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Throwable t
        (ctl/error "Something was wrong:" t)))))

(defn wrap-server-type
  [handler server-type]
  (fn [request]
    (handler (assoc request :server-type (:server-type request server-type)))))