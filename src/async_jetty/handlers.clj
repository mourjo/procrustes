(ns async-jetty.handlers
  (:require [hiccup.core :as hiccup]
            [async-jetty.env :as env]))


(defn generate-body
  [request]
  (hiccup/html [:h1 (str "You summoned " (:uri request))]))


(defn slow-poke
  [request]
  (Thread/sleep (* 1000 (env/slow-poke-time)))
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (generate-body request)})


(defn fast-poke
  [request]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (generate-body request)})
