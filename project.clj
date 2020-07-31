(defproject async_jetty "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [ring "1.8.1"]
                 [compojure "1.6.1" :exclusions [org.clojure/clojure]]
                 [org.clojure/tools.logging "1.1.0"]
                 [org.clojure/core.async "1.3.610"]
                 [clj-http "3.10.1"]
                 ;; https://mvnrepository.com/artifact/org.postgresql/postgresql
                 [org.postgresql/postgresql "42.1.3"]
                 [seancorfield/next.jdbc "1.1.569"]
                 [com.climate/claypoole "1.1.4"]]
  :jvm-opts ^:replace ["-server"
                       "-Xms1g"
                       "-Xmx3g"
                       "-Djava.awt.headless=true"
                       "-Duser.timezone=UTC"]
  :main ^:skip-aot async-jetty.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
