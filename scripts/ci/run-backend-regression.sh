#!/usr/bin/env bash
set -euo pipefail

report_dir=${1:?Pass a dedicated report directory}
mkdir -p "$report_dir"
report_dir=$(cd "$report_dir" && pwd)
container="hisab-ci-mongo-${RANDOM}-$$"
server_pid=''
cleanup() {
  if [[ -n "$server_pid" ]]; then kill "$server_pid" 2>/dev/null || true; fi
  docker logs "$container" >"$report_dir/mongodb.log" 2>&1 || true
  docker rm -f "$container" >/dev/null 2>&1 || true
}
trap cleanup EXIT
wait_for() {
  for ((attempt = 1; attempt <= 45; attempt++)); do
    if "$@" >/dev/null 2>&1; then return 0; fi
    sleep 1
  done
  printf 'Timeout waiting for %s\n' "$1" >&2
  return 1
}

docker run -d --name "$container" -p 127.0.0.1:27017:27017 mongo:8.0 --replSet rs0 --bind_ip_all
wait_for docker exec "$container" mongosh --quiet --eval 'quit(db.adminCommand({ping:1}).ok === 1 ? 0 : 1)'
docker exec "$container" mongosh --quiet --eval 'rs.initiate({_id:"rs0",members:[{_id:0,host:"localhost:27017"}]})'
wait_for docker exec "$container" mongosh --quiet --eval 'quit(db.hello().isWritablePrimary ? 0 : 1)'

export JWT_SECRET
JWT_SECRET=$(python3 -c 'import secrets; print(secrets.token_hex(32))')
export MONGO_URI='mongodb://127.0.0.1:27017/?replicaSet=rs0&serverSelectionTimeoutMS=5000'
export MONGO_DATABASE=hisab_ci HOST=127.0.0.1 PORT=18081 HISAB_ENV=production
export UPLOAD_DIR="$report_dir/uploads"
hisab-server/build/install/hisab-server/bin/hisab-server >"$report_dir/hisab-server.log" 2>&1 &
server_pid=$!
wait_for curl --fail --silent --max-time 2 http://127.0.0.1:18081/healthz
python3 scripts/qa/check_api.py --extended --port 18081 | tee "$report_dir/api-regression.txt"
