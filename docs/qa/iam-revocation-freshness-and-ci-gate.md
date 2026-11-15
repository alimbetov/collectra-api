# IAM P0 — revocation freshness and mandatory AC/COL G2 gate

Branch: `feat/iam-shared-platform`  
State: **IMPLEMENTED, NOT YET EXECUTION-VERIFIED**  
Production: **HOLD**; App-B / full user lifecycle / browser login are separate unfinished gates.

## Confirmed failure mode

The former COL revocation poller scanned *every* VERIFIED identity in one scheduled invocation,
serially performing HTTP GET + durable snapshot apply + authenticated HTTP ACK. AC/COL local
authorization requires a fresh ACTIVE snapshot (default 30 seconds). With thousands of
identities, a full scan cannot guarantee freshness; locally unblocked real users may be denied.

Increasing the freshness TTL would weaken the global revocation contract and is **not**
an acceptable fix.

## Target behavior (current implementation)

1. A locally fresh ACTIVE snapshot is admitted without an external request.
2. A locally fresh BLOCKED snapshot is denied without an external request.
3. A missing/expired snapshot is checked against the authoritative AC synchronously on
   the affected user's authorization request, using the registered machine consumer.
   Local state is committed **before** the authenticated ACK; access is only admitted after
   a fresh ACTIVE state is read back from COL PostgreSQL.
4. Concurrent requests for the same subject share an in-flight refresh. At most
   `COLLECTRA_REVOCATION_ON_DEMAND_MAX_CONCURRENT` (default 8) distinct on-demand
   refreshes run simultaneously. Exhaustion, missing credentials, remote failure, stale
   response, database error and timeouts **deny** access; no fail-open fallback.
5. The scheduled background reconciler processes at most 50 VERIFIED subjects per run.
   It advances a keyset cursor even if a particular subject fails, and wraps after the
   final page. The background sweep is a reconciliation safety net, not a guarantee
   that all enrolled identities remain under the 30-second TTL.
6. Failures in the background page are counted without logging subject identifiers,
   access tokens or remote response bodies.

## Test evidence (source-level, not yet observed GREEN)

- `RevocationSnapshotPostgresIT`: stale ACTIVE + AC BLOCKED -> deny;
  stale ACTIVE + AC ACTIVE -> refresh and admit; AC unavailable -> deny;
  concurrent requests to one stale subject -> single remote refresh.
  Uses isolated PostgreSQL schema and a mocked remote consumer.
- `RevocationSnapshotConsumerBatchPostgresIT`: full Liquibase COL PostgreSQL,
  separate synthetic identities and a real localhost HTTP fixture for token/GET/ACK.
  Verifies 50+2 bounded background batches, cursor wrap and on-demand access for
  a subject outside the initial page. The fixture is **not a running AC**.
- `scripts/smoke-iam-macos.sh`: separate running AC/COL/Keycloak/PostgreSQL stacks;
  checks BLOCKED version, old unexpired JWT denial, authenticated autonomous ACK,
  replay, outages and recovery. SMOKE-07 (real App-B) remains **BLOCKED**.

## CI contract

The top-level `.github/workflows/ci.yml` runs verify, frontend and app-common,
then calls the reusable `.github/workflows/iam-g2-real-dual-contour-e2e.yml` as
the `iam-g2` job. The G2 workflow is callable and manually dispatchable; it is
not restricted to smoke-script path changes. A failed G2 must fail the main
CI workflow for its candidate SHA. A skipped G2 due to an upstream failing job
must **not** be called an accepted release.

Both PostgreSQL regressions above are explicitly selected in the main CI
because default Maven Surefire discovery may not execute `*IT` classes.

Repository CI wiring does not itself configure GitHub branch protection.
A production release additionally needs enforced required checks.

## Mandatory capacity/recovery qualification still pending

Run with 1,000 and 10,000 VERIFIED identities and realistic 50/100/500
concurrent active subjects. Inject AC latency, token-service slowdown,
PostgreSQL lag, network partition and AC restart. Collect:

- authorization p95/p99 and counts of stale-snapshot denials;
- remote refresh attempts/successes/failures, concurrency saturation;
- snapshot age and BLOCKED propagation latency;
- autonomous ACK lag, stale/duplicate versions, recovery after restart;
- AC and COL CPU, DB pool utilization and outbound request rates.

Agree measurable availability and security SLOs **before** a production cutover.
The current bounded read-through is a targeted mitigation, **not** a claim
that mass-denial under AC saturation is impossible. A 30-second fail-closed
policy intentionally denies access if authoritative freshness cannot be obtained.

## Commands

```bash
git switch feat/iam-shared-platform
mvn -B -ntp spotless:check
mvn -B -ntp -Dtest=RevocationSnapshotPostgresIT,RevocationSnapshotConsumerBatchPostgresIT test
mvn -B -ntp clean verify -Pslice10a-coverage
bash scripts/smoke-iam-macos.sh
bash scripts/stop-iam-macos.sh
```

Record exact SHA, test reports and run URL. **No PASS is claimed by this document.**
