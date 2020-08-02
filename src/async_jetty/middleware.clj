(ns async-jetty.middleware
  (:require [clojure.tools.logging :as ctl]))


(defonce counter (atom 0))

(defn wrap-request-id
  [handler]
  (fn [request]
    (let [id (swap! counter inc)]
      (handler (assoc request :request-id id)))))