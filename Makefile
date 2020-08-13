load_shedding_server:
	SHED_LOAD=true java -server -Dcom.sun.management.jmxremote.port=1919 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -jar target/uberjar/procrustes-*-standalone.jar

non_load_shedding_server:
	SHED_LOAD=false java -server -Dcom.sun.management.jmxremote.port=1920 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -jar target/uberjar/procrustes-*-standalone.jar

compile:
	lein -U do clean, uberjar

steady_requests:
	lein trampoline run -m procrustes.client/steady

burst_request:
	lein trampoline run -m procrustes.client/burst
