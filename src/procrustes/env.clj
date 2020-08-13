(ns procrustes.env
  (:require [clj-statsd :as statsd])
  (:import (me.mourjo SlowPokeSettings)))


(defonce env-bean (SlowPokeSettings.))
(def jmx-nls-conn {:host "localhost" :port 1919})
(def jmx-ls-conn {:host "localhost" :port 1920})
(def grafana-annotations-endpoint "http://localhost/api/annotations")
(def slow-nls-route "http://localhost:3200/slow")
(def fast-nls-route "http://localhost:3200/fast")
(def slow-ls-route "http://localhost:3100/slow")
(def fast-ls-route "http://localhost:3100/fast")


(defn setup-statsd
  []
  (statsd/setup "localhost" 8125))


(defn start
  []
  (setup-statsd)
  (SlowPokeSettings/startServer env-bean))


(defn slow-poke-time
  []
  (.getSlowPokeTime env-bean))


(defn set-slow-poke-time
  [n]
  (.setSlowPokeTime env-bean (int n)))
