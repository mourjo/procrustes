for i in $(seq 1 10000); do

curl -L -s "http://localhost:$PORT/slow/" > /dev/null &
sleep ${SLEEP:=3};
date;
done;
