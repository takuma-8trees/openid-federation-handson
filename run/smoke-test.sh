#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
trustor() { docker compose run --rm -T trustor "$@"; }

attempt=0
until trustor chain fetch trustee > /tmp/oidf-demo-fetch-trustee.log 2>&1; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 20 ]; then cat /tmp/oidf-demo-fetch-trustee.log; exit 1; fi
  sleep 2
done

cat /tmp/oidf-demo-fetch-trustee.log
trustor trust show

shown=$(trustor chain show trustee)
printf '%s\n' "$shown"
printf '%s\n' "$shown" | grep -Fq '[subordinate] iss=https://trust-anchor:8443'

verified=$(trustor chain verify trustee)
printf '%s\n' "$verified"
printf '%s\n' "$verified" | grep -Fq 'TRUSTED: https://trustee:8443 -> https://trust-anchor:8443'

echo 'PASS: Trustee chain verified.'
