#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-unit}"

UNIT_TESTS="MessageAttachmentTest,MessageStateServiceAttachmentGateTest,MessageDeliveryRequestServiceTest,MessageAttachmentContentResolverTest,MessageAttachmentServiceTest,AttachmentPropertiesTest,MessageDeliveryWorkerTest,KumoMtaEmailDeliveryGatewayTest,PdfRendererLocaleUnitTest"

case "$MODE" in
  unit)
    echo "Running Slice 7 unit tests..."
    mvn --batch-mode --no-transfer-progress \
      -Dtest="$UNIT_TESTS" \
      test
    ;;
  verify)
    echo "Running formatting check and full Maven verify..."
    echo "Docker Desktop must be running because integration tests use Testcontainers."
    mvn --batch-mode --no-transfer-progress spotless:check
    mvn --batch-mode --no-transfer-progress clean verify
    ;;
  *)
    echo "Usage: bash scripts/test-slice-07.sh [unit|verify]" >&2
    exit 2
    ;;
esac
