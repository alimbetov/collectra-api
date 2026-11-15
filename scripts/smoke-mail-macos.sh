#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

for tool in docker mvn curl git; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    printf 'BLOCKED: required tool %s is missing\n' "$tool" >&2
    exit 2
  fi
done

if [[ "$(git branch --show-current)" != "feat/iam-shared-platform" ]]; then
  printf 'BLOCKED: switch to feat/iam-shared-platform before running mail smoke\n' >&2
  exit 2
fi

if [[ "$(uname -s)" != "Darwin" ]]; then
  printf 'BLOCKED: this command is the local macOS acceptance smoke\n' >&2
  exit 2
fi

if ! docker info >/dev/null 2>&1; then
  printf 'BLOCKED: Docker daemon is unavailable\n' >&2
  exit 2
fi

printf 'Starting real KumoMTA and local SMTP test mailbox...\n'
if ! docker compose -f compose.mail-smoke.yaml up -d --quiet-pull; then
  printf 'BLOCKED: KumoMTA/Mailpit could not start; simulated provider is forbidden\n' >&2
  exit 2
fi

ready=false
for _ in {1..60}; do
  kumo_status="$(curl --silent --output /dev/null --write-out '%{http_code}' http://127.0.0.1:18000/ || true)"
  if [[ "$kumo_status" != "000" ]] && curl --fail --silent http://127.0.0.1:18025/api/v1/messages >/dev/null; then
    ready=true
    break
  fi
  sleep 1
done

if [[ "$ready" != true ]]; then
  printf 'BLOCKED: real KumoMTA injection API or Mailpit did not become ready\n' >&2
  docker compose -f compose.mail-smoke.yaml ps >&2
  exit 2
fi

printf 'Running Collectra -> KumoMTA HTTP injection -> SMTP mailbox -> PostgreSQL assertions...\n'
mkdir -p target
if mvn --batch-mode --no-transfer-progress -Dtest=KumoMtaRealMailSmokeIT test \
    | tee target/mail-smoke.log; then
  printf 'PASS: real KumoMTA acceptance, controlled rejection, PostgreSQL and mailbox verified\n'
  printf 'Evidence: target/mail-smoke.log\n'
else
  printf 'FAIL: real mail smoke assertions failed; see target/mail-smoke.log\n' >&2
  exit 1
fi
