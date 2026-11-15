#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
python3 run/smoke-api.py "${1:-http://localhost:18766}" "${2:-}"
