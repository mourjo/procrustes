(ns async-jetty.env
  (:import (me.mourjo SlowPokeSettings)))


(defonce env-bean (SlowPokeSettings.))


(defn start-mbean-server
  []
  (SlowPokeSettings/startServer env-bean))

(defn slow-poke-time
  []
  (.getSlowPokeTime env-bean))