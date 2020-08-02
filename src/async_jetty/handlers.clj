(ns async-jetty.handlers
  (:require [hiccup.core :as hiccup]
            [clojure.tools.logging :as ctl]))


(def sleep-time-sec 3)


(defn generate-body
  [request]
  (hiccup/html [:h1 (str "You summoned " (:uri request))]))


(defn slow-poke
  [request]
  (Thread/sleep (* 1000 sleep-time-sec))
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (generate-body request)})


(defn fast-poke
  [request]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (generate-body request)})
