(ns procrustes.env
  (:require [clj-statsd :as statsd])
  (:import (me.mourjo SlowPokeSettings)))


(defonce env-bean (SlowPokeSettings.))


(defn start
  []
  (statsd/setup "localhost" 8125)
  (SlowPokeSettings/startServer env-bean))

(defn slow-poke-time
  []
  (.getSlowPokeTime env-bean))
