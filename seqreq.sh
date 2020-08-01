for i in $(seq 1 10000); do

for j in $(seq 1 10); do
curl -L 'http://localhost:3100/slow/' &
done;
wait
echo
echo "=================================="
done;