load_shedding_server:
	SHED_LOAD=true java -server -Dcom.sun.management.jmxremote.port=1919 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -jar /Users/mourjo/personal/repos/procrustes/target/uberjar/procrustes-0.1.0-SNAPSHOT-standalone.jar

non_load_shedding_server:
	SHED_LOAD=false java -server -Dcom.sun.management.jmxremote.port=1920 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -jar /Users/mourjo/personal/repos/procrustes/target/uberjar/procrustes-0.1.0-SNAPSHOT-standalone.jar

compile:
	lein -U do clean, compile, uberjar

steady_requests:
	# SLEEP=1 ./steady_requests.sh
	lein run -m procrustes.client/steady

burst_request:
	# ./burst_requests.sh
	lein run -m procrustes.client/burst