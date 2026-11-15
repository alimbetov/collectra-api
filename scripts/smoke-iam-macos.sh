#!/usr/bin/env bash
set -Eeuo pipefail
set +x

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT"
mkdir -p "$ROOT/target"
REPORT="$ROOT/target/iam-g2-e2e-report.md"
COMPOSE=(docker compose --env-file .env -f compose.iam-smoke.yaml)
ISSUER=""
API=""
COMMON=""
KC=""
CURRENT=0
RESULT=(unused BLOCKED BLOCKED BLOCKED BLOCKED BLOCKED BLOCKED BLOCKED BLOCKED)
DETAIL=(unused "not reached" "not reached" "not reached" "not reached" "not reached" "not reached" "App-B implementation unavailable" "not reached")
RESPONSE=""
STATUS=""
TMP=""

mark() {
  RESULT[$1]=$2
  DETAIL[$1]=$3
  printf 'SMOKE-%02d %s: %s\n' "$1" "$2" "$3"
}

report() {
  local code=$1 i os arch sha docker_version keycloak_version
  if (( code != 0 && CURRENT > 0 )) && [[ ${RESULT[$CURRENT]} == BLOCKED ]]; then
    mark "$CURRENT" FAIL "runner stopped before the scenario completed"
  fi
  os=$(sw_vers -productVersion 2>/dev/null || printf 'unknown')
  arch=$(uname -m)
  sha=$(git rev-parse HEAD)
  docker_version=$(docker version --format '{{.Server.Version}}' 2>/dev/null || printf 'unavailable')
  keycloak_version=$("${COMPOSE[@]}" exec -T keycloak /opt/keycloak/bin/kc.sh --version 2>/dev/null | head -n 1 || printf 'unavailable')
  {
    printf '# IAM macOS functional smoke report\n\n'
    printf 'Run UTC: %s  \n' "$(date -u '+%Y-%m-%d %H:%M:%S')"
    printf 'Source HEAD: `%s`  \n' "$sha"
    printf 'Working tree: `%s`  \n' "$(git status --porcelain | wc -l | tr -d ' ') changed paths (including this report)"
    printf 'macOS: `%s` / `%s`  \n' "$os" "$arch"
    printf 'Java: `%s`  \n' "$(java -version 2>&1 | head -n 1)"
    printf 'Docker: `%s`  \n' "$docker_version"
    printf 'Keycloak: `%s`  \n\n' "$keycloak_version"
    printf '| Scenario | Result | Evidence |\n| --- | --- | --- |\n'
    for i in 1 2 3 4 5 6 7 8; do
      printf '| SMOKE-%02d | %s | %s |\n' "$i" "${RESULT[$i]}" "${DETAIL[$i]}"
    done
    printf '\nBootstrap prerequisites: AC operator, OAuth consumer binding and verified COL identity link are provisioned with disposable PostgreSQL. Credential cutover is transactionally seeded for G2, not through onboarding HTTP.\n'
    printf '\nG2 tests both separate JVMs and PostgreSQL databases, autonomous COL ACK, stale ACTIVE fail-closed, retry, AC outage/recovery, and blocked-token rejection.\n'
    printf '\nLimits: App-B not implemented, so SMOKE-07 and two-consumer ENFORCED remain BLOCKED. Browser OIDC/PKCE, public onboarding and email verification are separate gates. No simulated consumer ACK is issued.\n'
    printf '\nRun: ./scripts/smoke-iam-macos.sh ; stop: ./scripts/stop-iam-macos.sh ; report: target/iam-g2-e2e-report.md ; secrets: .env (ignored).\n'
    if (( code != 0 )); then
      printf '\nBlocking issue: runner exited with code `%s`; inspect local container logs without sharing secrets.\n' "$code"
    fi
  } > "$REPORT"
  [[ -z "$TMP" ]] || rm -f "$TMP"
}
trap 'report $?' EXIT

need() { command -v "$1" >/dev/null || { echo "Missing tool: $1" >&2; exit 2; }; }
for tool in docker java mvn curl jq openssl sw_vers uuidgen; do need "$tool"; done
[[ $(uname -s) == Darwin ]] || { echo 'NOT RUN ON MACOS: this runner requires macOS' >&2; exit 2; }
docker info >/dev/null

if [[ ! -f .env ]]; then
  umask 077
  {
    for name in IAM_DB_PASSWORD IAM_KC_BOOTSTRAP_PASSWORD IAM_KC_ADMIN_CLIENT_SECRET \
      IAM_OPERATOR_CLIENT_SECRET IAM_CONSUMER_CLIENT_SECRET IAM_RABBIT_PASSWORD \
      IAM_STORAGE_ACCESS_KEY IAM_STORAGE_SECRET_KEY IAM_COLLECTRA_JWT_SECRET \
      IAM_OTP_PEPPER IAM_USER_PASSWORD; do
      printf '%s=%s\n' "$name" "$(openssl rand -hex 24)"
    done
    printf 'IAM_KC_PORT=18180\nIAM_COMMON_PORT=18090\nIAM_API_PORT=18081\n'
  } > .env
fi
set -a
source .env
set +a
for name in IAM_DB_PASSWORD IAM_KC_BOOTSTRAP_PASSWORD IAM_KC_ADMIN_CLIENT_SECRET \
  IAM_OPERATOR_CLIENT_SECRET IAM_CONSUMER_CLIENT_SECRET IAM_RABBIT_PASSWORD \
  IAM_STORAGE_ACCESS_KEY IAM_STORAGE_SECRET_KEY IAM_COLLECTRA_JWT_SECRET \
  IAM_OTP_PEPPER IAM_USER_PASSWORD; do
  [[ -n ${!name:-} ]] || { echo "Missing $name in .env" >&2; exit 2; }
done
KC="http://localhost:${IAM_KC_PORT:-18180}"
COMMON="http://localhost:${IAM_COMMON_PORT:-18090}"
API="http://localhost:${IAM_API_PORT:-18081}"
ISSUER="$KC/realms/shared"
TMP=$(mktemp)
chmod 600 "$TMP"

echo 'Building App Common and Collectra API jars'
"${COMPOSE[@]}" stop collectra-api app-common >/dev/null
mvn --batch-mode --no-transfer-progress -q -f app-common/pom.xml -DskipTests package
mvn --batch-mode --no-transfer-progress -q -DskipTests package
"${COMPOSE[@]}" config --quiet
"${COMPOSE[@]}" up --quiet-pull -d postgres rabbitmq rustfs keycloak

wait_http() {
  local url=$1 attempt
  for attempt in $(seq 1 90); do
    if curl -fsS --max-time 3 "$url" >/dev/null 2>&1; then return 0; fi
    sleep 3
  done
  echo "Service readiness timeout: $url" >&2
  return 1
}
wait_http "$ISSUER/.well-known/openid-configuration"

kc() { "${COMPOSE[@]}" exec -T keycloak /opt/keycloak/bin/kcadm.sh "$@"; }
kc config credentials --server http://localhost:8080 --realm master \
  --user admin --password "$IAM_KC_BOOTSTRAP_PASSWORD" >/dev/null
client_id() {
  kc get clients -r shared -q "clientId=$1" --fields id --format csv | tail -n 1 | tr -d '"\r'
}
user_client_id=$(client_id iam-smoke-user)
if ! kc get "clients/$user_client_id/protocol-mappers/models" -r shared | \
  jq -e 'any(.[]; .name == "sub")' >/dev/null; then
  kc create "clients/$user_client_id/protocol-mappers/models" -r shared \
    -s name=sub -s protocol=openid-connect -s protocolMapper=oidc-sub-mapper \
    -s 'config."access.token.claim"=true' -s 'config."introspection.token.claim"=true' >/dev/null
fi
for client in iam-smoke-operator iam-smoke-consumer iam-smoke-kc-admin; do
  id=$(client_id "$client")
  [[ $id =~ ^[0-9a-fA-F-]{36}$ ]] || { echo "Keycloak client missing: $client" >&2; exit 1; }
  case "$client" in
    iam-smoke-operator) secret=$IAM_OPERATOR_CLIENT_SECRET ;;
    iam-smoke-consumer) secret=$IAM_CONSUMER_CLIENT_SECRET ;;
    iam-smoke-kc-admin) secret=$IAM_KC_ADMIN_CLIENT_SECRET ;;
  esac
  kc update "clients/$id" -r shared -s "secret=$secret" >/dev/null
done
admin_client_id=$(client_id iam-smoke-kc-admin)
kc update "clients/$admin_client_id" -r shared -s fullScopeAllowed=true >/dev/null
if ! kc get "clients/$admin_client_id/protocol-mappers/models" -r shared | \
  jq -e 'any(.[]; .name == "client roles")' >/dev/null; then
  kc create "clients/$admin_client_id/protocol-mappers/models" -r shared \
    -f /opt/keycloak/data/smoke/kc-admin-roles-mapper.json >/dev/null
fi
kc add-roles -r shared --uusername service-account-iam-smoke-kc-admin \
  --cclientid realm-management --rolename manage-users >/dev/null
operator_id=$(client_id iam-smoke-operator)
operator_subject=$(kc get "clients/$operator_id/service-account-user" -r shared \
  --fields id --format csv | tail -n 1 | tr -d '"\r')
[[ $operator_subject =~ ^[0-9a-fA-F-]{36}$ ]] || { echo 'Operator service subject missing' >&2; exit 1; }

run_id=$(openssl rand -hex 5)
email="iam-smoke-${run_id}@example.invalid"
slug="iam-smoke-${run_id}"
kc create users -r shared -s "username=$email" -s "email=$email" \
  -s firstName=IAM -s lastName=Smoke -s enabled=true -s emailVerified=true >/dev/null
kc set-password -r shared --username "$email" \
  --new-password "$IAM_USER_PASSWORD" --temporary=false >/dev/null
subject=$(kc get users -r shared -q "email=$email" --fields id --format csv | tail -n 1 | tr -d '"\r')
[[ $subject =~ ^[0-9a-fA-F-]{36}$ ]] || { echo 'Keycloak user subject missing' >&2; exit 1; }

"${COMPOSE[@]}" up --quiet-pull --force-recreate -d app-common
wait_http "$COMMON/actuator/health"
sql_common() { "${COMPOSE[@]}" exec -T postgres psql -X -qAt -v ON_ERROR_STOP=1 -U smoke -d app_common -c "$1"; }
sql_collectra() { "${COMPOSE[@]}" exec -T postgres psql -X -qAt -v ON_ERROR_STOP=1 -U smoke -d collectra -c "$1"; }
sql_common "INSERT INTO subject_access_state(issuer,subject_id,state,version) VALUES ('$ISSUER','$operator_subject','ACTIVE',1) ON CONFLICT DO NOTHING" >/dev/null
sql_common "UPDATE revocation_consumers SET enabled=true,oauth_client_id='iam-smoke-consumer' WHERE consumer_id='collectra-api'" >/dev/null

token() {
  curl -fsS --max-time 10 -X POST "$ISSUER/protocol/openid-connect/token" \
    -d grant_type=client_credentials -d "client_id=$1" --data-urlencode "client_secret=$2" | jq -er .access_token
}
operator_token=$(token iam-smoke-operator "$IAM_OPERATOR_CLIENT_SECRET")
consumer_token=$(token iam-smoke-consumer "$IAM_CONSUMER_CLIENT_SECRET")
user_token=$(curl -fsS --max-time 10 -X POST "$ISSUER/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=iam-smoke-user \
  --data-urlencode "username=$email" --data-urlencode "password=$IAM_USER_PASSWORD" | jq -er .access_token)

http() {
  STATUS=$(curl -sS --max-time 15 -o "$TMP" -w '%{http_code}' "$@")
  RESPONSE=$(<"$TMP")
}
CURRENT=1
"${COMPOSE[@]}" up --quiet-pull --force-recreate -d collectra-api
wait_http "$API/actuator/health"
http -X POST "$API/api/v1/auth/tenants/register" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg slug "$slug" --arg email "$email" --arg password "$IAM_USER_PASSWORD" \
    '{slug:$slug,companyName:"IAM Smoke",email:$email,password:$password}')"
[[ $STATUS == 201 ]] || { mark 1 FAIL "tenant register returned HTTP $STATUS"; exit 1; }
identity=$(sql_collectra "SELECT id || '|' || tenant_id FROM user_accounts WHERE email='$email'")
user_id=${identity%%|*}
tenant_id=${identity##*|}
[[ $user_id =~ ^[0-9a-fA-F-]{36}$ && $tenant_id =~ ^[0-9a-fA-F-]{36}$ ]] || { mark 1 FAIL 'tenant bootstrap identity missing'; exit 1; }
# G2 disposable bootstrap, NOT real-user HTTP onboarding acceptance.
# Keep local UUID, revoke legacy refresh sessions and advance the authorization version atomically.
sql_collectra "BEGIN;
INSERT INTO collectra_identity_links(issuer,subject_id,collectra_user_id,status,verified_at)
VALUES ('$ISSUER','$subject','$user_id','VERIFIED',now());
UPDATE user_accounts SET credential_authority='KEYCLOAK',
 authorization_version=authorization_version+1
WHERE id='$user_id' AND credential_authority='LEGACY';
UPDATE refresh_sessions SET revoked_at=now() WHERE user_id='$user_id' AND revoked_at IS NULL;
COMMIT;" >/dev/null
[[ $(sql_collectra "SELECT credential_authority FROM user_accounts WHERE id='$user_id'") == KEYCLOAK ]] || {
  mark 1 FAIL 'COL credential authority cutover failed'; exit 1;
}
http -X POST "$COMMON/api/v1/platform/subjects/$subject/initialize?issuer=$ISSUER" \
  -H "Authorization: Bearer $operator_token"
[[ $STATUS == 200 && $(jq -r .state <<< "$RESPONSE") == ACTIVE ]] || { mark 1 FAIL "subject initialize returned HTTP $STATUS"; exit 1; }
for attempt in $(seq 1 20); do
  [[ $(sql_collectra "SELECT state FROM collectra_global_access_snapshots WHERE issuer='$ISSUER' AND subject_id='$subject'") == ACTIVE ]] && break
  sleep 2
done
http "$API/api/v1/identity/me" -H "Authorization: Bearer $user_token" \
  -H "X-Collectra-Tenant-ID: $tenant_id"
[[ $STATUS == 200 && $(jq -r .id <<< "$RESPONSE") == "$user_id" ]] || {
  mark 1 FAIL "real Keycloak JWT to Collectra returned HTTP $STATUS or wrong local user"; exit 1;
}
mark 1 PASS 'Keycloak password grant JWT accepted by Collectra tenant API (HTTP 200)'

CURRENT=2
http -X POST "$COMMON/api/v1/platform/subjects/$subject/block?issuer=$ISSUER" \
  -H "Authorization: Bearer $user_token" -H "Idempotency-Key: deny-$run_id"
[[ $STATUS == 403 ]] || { mark 2 FAIL "unprivileged block returned HTTP $STATUS, expected 403"; exit 1; }
http "$API/api/v1/identity/me" -H "Authorization: Bearer $user_token" \
  -H "X-Collectra-Tenant-ID: $(uuidgen | tr '[:upper:]' '[:lower:]')"
[[ $STATUS == 401 ]] || { mark 2 FAIL "foreign tenant header returned HTTP $STATUS, expected 401"; exit 1; }
mark 2 PASS 'user denied platform block (403) and foreign tenant selector (401)'

CURRENT=3
block_key="block-$run_id"
http -X POST "$COMMON/api/v1/platform/subjects/$subject/block?issuer=$ISSUER" \
  -H "Authorization: Bearer $operator_token" -H "Idempotency-Key: $block_key"
[[ $STATUS == 202 ]] || { mark 3 FAIL "operator block returned HTTP $STATUS"; exit 1; }
operation=$(jq -er .operationId <<< "$RESPONSE")
version=$(jq -er .version <<< "$RESPONSE")
state=$(sql_common "SELECT state || '|' || version FROM subject_access_state WHERE issuer='$ISSUER' AND subject_id='$subject'")
[[ $state == "BLOCKED|$version" ]] || { mark 3 FAIL "database state is $state"; exit 1; }
mark 3 PASS "App Common HTTP block committed BLOCKED v$version in PostgreSQL"

CURRENT=4
denied=0
for attempt in $(seq 1 20); do
  http "$API/api/v1/identity/me" -H "Authorization: Bearer $user_token" \
    -H "X-Collectra-Tenant-ID: $tenant_id"
  if [[ $STATUS == 401 ]]; then denied=1; break; fi
  sleep 2
done
[[ $denied == 1 ]] || { mark 4 FAIL "old JWT still accepted: HTTP $STATUS"; exit 1; }
mark 4 PASS 'pre-block JWT rejected by Collectra after consumer synchronization (HTTP 401)'

CURRENT=5
http "$COMMON/api/v1/internal/revocations/$subject?issuer=$ISSUER" \
  -H "Authorization: Bearer $consumer_token"
[[ $STATUS == 200 && $(jq -r .state <<< "$RESPONSE") == BLOCKED && $(jq -r .version <<< "$RESPONSE") == "$version" ]] || {
  mark 5 FAIL "consumer snapshot returned HTTP $STATUS or wrong state"; exit 1;
}
mark 5 PASS "OAuth Consumer fetched BLOCKED v$version over HTTP"

CURRENT=6
http -X POST "$COMMON/api/v1/internal/revocations/ack" \
  -H "Authorization: Bearer $consumer_token" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg issuer "$ISSUER" --arg subject "$subject" --argjson version "$version" \
    '{issuer:$issuer,subject:$subject,version:$version}')"
[[ $STATUS == 202 ]] || { mark 6 FAIL "HTTP ACK returned $STATUS"; exit 1; }
ack=$(sql_common "SELECT version FROM revocation_acks WHERE consumer_id='collectra-api' AND issuer='$ISSUER' AND subject_id='$subject'")
[[ $ack == "$version" ]] || { mark 6 FAIL "ACK database version is $ack"; exit 1; }
mark 6 PASS "service OAuth HTTP ACK persisted v$version in PostgreSQL"

CURRENT=7
mark 7 BLOCKED 'no real App-B executable in repository; no second Consumer/ACK simulated'

CURRENT=8
http -X POST "$COMMON/api/v1/platform/subjects/$subject/block?issuer=$ISSUER" \
  -H "Authorization: Bearer $operator_token" -H "Idempotency-Key: $block_key"
[[ $STATUS == 202 && $(jq -r .operationId <<< "$RESPONSE") == "$operation" && $(jq -r .version <<< "$RESPONSE") == "$version" ]] || {
  mark 8 FAIL "idempotent replay returned HTTP $STATUS or changed operation"; exit 1;
}
"${COMPOSE[@]}" restart collectra-api >/dev/null
wait_http "$API/actuator/health"
for attempt in $(seq 1 20); do
  http "$API/api/v1/identity/me" -H "Authorization: Bearer $user_token" \
    -H "X-Collectra-Tenant-ID: $tenant_id"
  [[ $STATUS == 401 ]] && break
  sleep 2
done
[[ $STATUS == 401 ]] || { mark 8 FAIL "old JWT accepted after restart: HTTP $STATUS"; exit 1; }
[[ $(sql_common "SELECT state || '|' || version FROM subject_access_state WHERE issuer='$ISSUER' AND subject_id='$subject'") == "BLOCKED|$version" ]] || {
  mark 8 FAIL 'central state regressed after restart'; exit 1;
}
op_state=$(sql_common "SELECT state FROM enforcement_operations WHERE id='$operation'")
for attempt in $(seq 1 20); do
  op_state=$(sql_common "SELECT state FROM enforcement_operations WHERE id='$operation'")
  [[ $op_state == PROPAGATING ]] && break
  sleep 2
done
[[ $op_state == PROPAGATING ]] || { mark 8 FAIL "Keycloak dispatcher did not apply block; operation $op_state"; exit 1; }
applied=$(sql_common "SELECT keycloak_applied_at IS NOT NULL FROM access_state_outbox WHERE issuer='$ISSUER' AND subject_id='$subject' AND target_version=$version")
[[ $applied == t ]] || { mark 8 FAIL 'Keycloak application was not persisted'; exit 1; }
enabled=$(kc get "users/$subject" -r shared --fields enabled | jq -r .enabled)
[[ $enabled == false ]] || { mark 8 FAIL 'Keycloak user remained enabled'; exit 1; }
[[ $op_state != ENFORCED ]] || { mark 8 FAIL 'operation falsely ENFORCED without App-B'; exit 1; }
mark 8 PASS "idempotent replay/restart preserved BLOCKED v$version; Keycloak disabled user; operation $op_state (not ENFORCED)"
