#!/usr/bin/env bash
set -euo pipefail
: "${COLLECTRA_TEST_CLIENT_SECRET:?}"
: "${APP_B_TEST_CLIENT_SECRET:?}"
KC_URL="${KC_URL:-http://localhost:8180}"
COLLECTRA_API="${COLLECTRA_API:-http://localhost:8090}"
APP_B_API="${APP_B_API:-http://localhost:8091}"
token() {
  curl --fail --silent --show-error -X POST "$KC_URL/realms/shared/protocol/openid-connect/token" \
    --data-urlencode "grant_type=client_credentials" \
    --data-urlencode "client_id=$1" \
    --data-urlencode "client_secret=$2" | python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])'
}
status() {
  curl --silent --output /dev/null --write-out '%{http_code}' \
    -H "Authorization: Bearer $2" "$1/api/v1/security/probe"
}
expect() {
  local expected="$1" actual="$2" scenario="$3"
  [[ "$expected" == "$actual" ]] || { echo "FAIL $scenario: expected $expected, got $actual"; exit 1; }
  echo "PASS $scenario: HTTP $actual"
}
a=$(token collectra-test "$COLLECTRA_TEST_CLIENT_SECRET")
b=$(token app-b-test "$APP_B_TEST_CLIENT_SECRET")
expect 200 "$(status "$COLLECTRA_API" "$a")" "Collectra token at Collectra audience"
expect 401 "$(status "$APP_B_API" "$a")" "Collectra token at App-B audience"
expect 200 "$(status "$APP_B_API" "$b")" "App-B token at App-B audience"
expect 401 "$(status "$COLLECTRA_API" "$b")" "App-B token at Collectra audience"
echo "Audience isolation proven for test machine clients; browser SSO is a separate gate."
