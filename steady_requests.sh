for i in $(seq 1 10000); do

curl -L -s "http://localhost:3100/slow" > /dev/null &
curl -L -s "http://localhost:3200/slow" > /dev/null &
echo $(date) ": Request " $i;
sleep ${SLEEP:=3}
done;
