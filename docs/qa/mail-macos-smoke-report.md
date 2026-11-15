# MAIL-SMOKE-R1: real KumoMTA local acceptance

## Scope and environment

- Branch: `feat/iam-shared-platform` only. No merge into `main`.
- Base commit at execution: `9e8570a2b665161df88e9614387f8d54e42f2a79` plus the mail-smoke changes in this branch.
- Host: macOS 15.6 (24G84), arm64; Java Temurin 17.0.9; Docker client/server 28.3.3/28.3.2.
- Real KumoMTA container version: `2026.09.29-b90d8bc1` (`ghcr.io/kumocorp/kumomta:latest`).
- Test mailbox: local Mailpit SMTP at `mailpit:1025`, API at `http://127.0.0.1:18025`.
- PostgreSQL: real PostgreSQL 15.19 Testcontainers instance, initialized by Liquibase (62 changesets).
- Run command: `./scripts/smoke-mail-macos.sh`.
- Evidence log (local, gitignored): `target/mail-smoke.log`.

## Results

| Check | Result | Evidence |
| --- | --- | --- |
| Delivery enabled / provider selected | PASS | Spring context has exactly one `DeliveryGateway`, `KumoMtaEmailDeliveryGateway`; no `SimulatedDeliveryGateway` |
| Real HTTP POST `/api/inject/v1`: accepted | PASS | KumoMTA HTTP 200; `success_count=1`, `fail_count=0`; Collectra message `2b1818b4-5c2d-4048-8d74-172ce16cc189` |
| PostgreSQL outcome for accepted message | PASS | `messages.status=SENT`; one delivery attempt; `provider_message_id=NULL` |
| SMTP delivery to test mailbox | PASS | Mailpit received `ok@smoke.test`; Mailpit ID `4t8bg2gY8TMwOAKF0EEdTr`, RFC Message-ID `1ok0evC9WmsMAQwUXeFZWl@mailpit` |
| Real HTTP POST `/api/inject/v1`: controlled rejection | PASS | KumoMTA HTTP 200; `success_count=0`, `fail_count=1`; Collectra message `488617f3-3a85-415e-a305-fa4a4b951dd5` |
| PostgreSQL outcome for rejected message | PASS | `messages.status=FAILED`, `last_error_code=KUMO_RECIPIENT_REJECTED`; one delivery attempt; no rejected message in Mailpit |
| Test execution | PASS | `KumoMtaRealMailSmokeIT`: 1 test, 0 failures, 0 errors; Maven BUILD SUCCESS |

The KumoMTA injection response does **not** contain a provider message ID. The Collectra UUID and Mailpit IDs above are distinct identifiers; `provider_message_id=NULL` is expected and must not be replaced with a fabricated KumoMTA ID. `SENT` in Collectra means the injection was accepted, not that an external inbox received the message. This smoke separately proved SMTP receipt by a **local test mailbox only**. External mailbox delivery remains unverified.

The controlled refusal is a policy rejection of `reject@smoke.test` by the real KumoMTA container. Its HTTP transport status is 200 but its per-recipient result is failure; Collectra correctly persisted `FAILED` and did not silently use simulated delivery. If KumoMTA or Mailpit is unavailable, the script exits `BLOCKED` or `FAIL`, never PASS.

## Reproduction

```bash
git switch feat/iam-shared-platform
./scripts/smoke-mail-macos.sh
./scripts/stop-mail-macos.sh
```

The Compose stack binds both HTTP interfaces to host loopback. Its KumoMTA policy accepts only two local `smoke.test` recipients and routes only that domain to Mailpit; it is not a production mail policy. The script leaves containers running after PASS/FAIL for inspection; the stop command removes containers without deleting the spool volume.
