(ns async-jetty.handlers
  (:require [hiccup.core :as hiccup]))


(def sleep-time 3000)


(defn generate-body
  [request]
  (hiccup/html [:h1 (str "You summoned " (:uri request))]))


(defn slow-poke
  [request]
  (Thread/sleep sleep-time)
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (generate-body request)})

(defn fast-poke
  [request]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (generate-body request)})
