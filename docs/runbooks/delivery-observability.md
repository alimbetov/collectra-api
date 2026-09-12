# Delivery observability runbook

## Processing stuck

Check `collectra_message_processing_stuck` and `collectra_message_processing_oldest_stuck_age_seconds` together. The default processing timeout is five minutes; the alert fires only after the oldest stale attempt exceeds two timeout windows. Verify the delivery worker, provider connectivity and recovery scheduler before changing message state manually.

## Retry backlog

Check `collectra_message_retry_wait_due` and `collectra_message_retry_wait_oldest_age_seconds`. A growing count and age usually means the retry dispatcher is not draining work or the provider remains unavailable. Do not bulk-change `RETRY_WAIT` rows directly; recovery must preserve Message/CampaignRun invariants.

## Failure rate

Use `collectra_message_delivery_total{result="failed"}` only as an operational signal. PostgreSQL CampaignRun counters remain the business source of truth. Inspect normalized `lastErrorCode` through the tenant-scoped Message API; raw provider responses and destinations must not be copied into tickets or logs.

## Dead letter

`collectra_message_dead_letter_depth` represents the RabbitMQ delivery DLQ and is distinct from domain `Message.status=FAILED`. Inspect broker/container failures before replay. Replaying a dead-lettered broker message must remain safe because delivery state transitions are idempotent.

The sampled `collectra_message_dead_letter_total` counts positive increases observed in DLQ depth. It is operational telemetry, not an accounting counter: broker purge or concurrent external DLQ consumers can make it undercount. RabbitMQ broker-native counters should be preferred when a management exporter is deployed.
