(ns procrustes.middleware)

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