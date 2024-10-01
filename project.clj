(defproject procrustes "0.1.0-SNAPSHOT"
  :description "Demo of how loadshedding can benefit your web server"
  :url "https://mourjo.me"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [mourjo/ring-jetty-adapter "1.0.0-1.8.1"]
                 [compojure "1.6.1" :exclusions [org.clojure/clojure]]
                 [org.clojure/tools.logging "1.1.0"]
                 [org.clojure/core.async "1.3.610"]
                 [clj-http "3.10.1"]
                 [org.postgresql/postgresql "42.1.3"]
                 [seancorfield/next.jdbc "1.1.569"]
                 [com.climate/claypoole "1.1.4"]
                 [log4j/log4j "1.2.17"]
                 [org.slf4j/slf4j-log4j12 "1.7.30"]
                 [ring/ring-defaults "0.3.2"]
                 [org.eclipse.jetty/jetty-jmx "9.4.31.v20200723"]
                 [clj-statsd "0.4.0"]
                 [org.clojure/java.jmx "1.0.0"]
                 [cheshire "5.10.0"]]
  :jvm-opts ^:replace ["-server"
                       "-Xms1g"
                       "-Xmx3g"
                       "-Djava.awt.headless=true"
                       "-Duser.timezone=UTC"]
  :java-source-paths ["java_src"]
  :javac-options ["-target" "11" "-source" "11"]
  :main procrustes.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}}
  :aot :all)
