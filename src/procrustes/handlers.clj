(ns procrustes.handlers
  (:require [hiccup.core :as hiccup]
            [procrustes.env :as env])
  (:import (java.util UUID)))


(defn generate-body
  [request]
  (str (hiccup/html [:h1 (str (:server-type request)
                              " You summoned "
                              (:uri request)
                              " "
                              (UUID/randomUUID))])
       (hiccup/html [:br])
       (hiccup/html [:br])
       (hiccup/html [:h2 "Others: "
                     [:a {:href "/slow"} "/slow"]
                     " or "
                     [:a {:href "/fast"} "/fast"]])))


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


(defn default-response
  [request-map]
  (format (hiccup/html [:h1
                        "Hello, %s try routes: "
                        [:a {:href "/slow"} "/slow"]
                        " or "
                        [:a {:href "/fast"} "/fast"]])
          (:server-type request-map)))