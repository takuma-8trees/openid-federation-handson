#!/bin/sh
set -eu
base=${1:-http://localhost:18765}
attempt=0
until curl -fsS --max-time 3 "$base/" >/dev/null; do
  attempt=$((attempt+1)); [ "$attempt" -lt 30 ] || exit 1
  sleep 1
done
check() {
  body=$(curl -fsS --max-time 20 "$base/api/experiment?scenario=$1&trust=$2")
  printf '%s' "$body" | grep -Eq '"accepted"[[:space:]]*:[[:space:]]*'"$3" || { echo "FAIL $1 / $2"; exit 1; }
  echo "PASS $1 / $2 -> $3"
}
check normal anchor true
check normal other false
for scenario in tampered expired unregistered token-key; do check "$scenario" anchor false; done
check normal anchor true
