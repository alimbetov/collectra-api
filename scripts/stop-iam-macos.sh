#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT"
if [[ -f .env ]]; then
  docker compose --env-file .env -f compose.iam-smoke.yaml down
else
  echo 'No local .env; nothing to stop'
fi
