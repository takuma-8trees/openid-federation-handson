#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
a() { docker compose exec -T entity-a entity-a "$@"; }
attempt=0
until a chain fetch B > /tmp/oidf-cui-fetch-b.log 2>&1; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 20 ]; then cat /tmp/oidf-cui-fetch-b.log; exit 1; fi
  sleep 2
done
cat /tmp/oidf-cui-fetch-b.log
a trust show
shown=$(a chain show B)
printf '%s\n' "$shown"
printf '%s\n' "$shown" | grep -Fq '[subordinate] iss=https://trust-anchor:8443'
verified=$(a chain verify B)
printf '%s\n' "$verified"
printf '%s\n' "$verified" | grep -Fq 'TRUSTED: https://entity-b:8443 -> https://trust-anchor:8443'
fetched=$(a chain fetch C)
printf '%s\n' "$fetched"
printf '%s\n' "$fetched" | grep -Fq 'HTTP 404'
shown=$(a chain show C)
printf '%s\n' "$shown" | grep -Fq '[subordinate] MISSING'
set +e
verified=$(a chain verify C)
status=$?
set -e
printf '%s\n' "$verified"
[ "$status" -eq 2 ]
printf '%s\n' "$verified" | grep -Fq 'PASS: Entity Configuration'
printf '%s\n' "$verified" | grep -Fq 'NOT_TRUSTED: MISSING_SUBORDINATE'
echo 'PASS: B chain verified; C self-signature valid but chain incomplete.'
