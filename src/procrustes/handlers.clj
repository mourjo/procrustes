(ns procrustes.handlers
  (:require [hiccup.core :as hiccup]
            [procrustes.env :as env])
  (:import (java.util UUID)))


(defn generate-body
  [request]
  (hiccup/html [:h1 (str "You summoned " (:uri request) " " (UUID/randomUUID))]))


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
