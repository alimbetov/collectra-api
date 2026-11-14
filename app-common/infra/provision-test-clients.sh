#!/usr/bin/env bash
set -euo pipefail
: "${KC_BOOTSTRAP_ADMIN_PASSWORD:?}"
: "${COLLECTRA_TEST_CLIENT_SECRET:?}"
: "${APP_B_TEST_CLIENT_SECRET:?}"
KC_URL="${KC_URL:-http://localhost:8180}"
ADMIN_USER="${KC_BOOTSTRAP_ADMIN_USERNAME:-admin}"
docker compose -f "$(dirname "$0")/compose.identity.yaml" exec -T keycloak \
  /opt/keycloak/bin/kcadm.sh config credentials \
  --server http://localhost:8180 --realm master \
  --user "$ADMIN_USER" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" >/dev/null
for client in collectra-test app-b-test; do
  if [[ "$client" == collectra-test ]]; then secret="$COLLECTRA_TEST_CLIENT_SECRET"; else secret="$APP_B_TEST_CLIENT_SECRET"; fi
  client_id=$(docker compose -f "$(dirname "$0")/compose.identity.yaml" exec -T keycloak \
    /opt/keycloak/bin/kcadm.sh get clients -r shared -q clientId="$client" --fields id --format csv | tail -n 1 | tr -d '"\r')
  [[ -n "$client_id" && "$client_id" != id ]] || { echo "Missing $client in shared realm" >&2; exit 1; }
  docker compose -f "$(dirname "$0")/compose.identity.yaml" exec -T keycloak \
    /opt/keycloak/bin/kcadm.sh update "clients/$client_id" -r shared -s "secret=$secret" >/dev/null
done
echo "Two test clients provisioned in the shared realm; secrets were not printed."
