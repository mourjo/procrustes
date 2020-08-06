(ns procrustes.middleware
  (:require [clojure.tools.logging :as ctl]))

(def open-requests (agent 0))
(def completed-requests (agent 0))
(defonce counter (atom 0))

(defn wrap-request-id
  [handler]
  (fn [request]
    (let [id (swap! counter inc)]
      (handler (assoc request :request-id id)))))


(defn wrap-request-counter
  [handler]
  (fn [request]
    (try (send open-requests inc)
         (handler request)
         (finally (send completed-requests inc)))))


(defn wrap-exceptions
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Throwable t
        (ctl/error "Something was wrong:" t)))))