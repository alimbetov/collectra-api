#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_dir"

mvn --batch-mode --no-transfer-progress \
  -Dtest=OpenApiCompatibilityIntegrationTest \
  -Dcollectra.openapi.update-baseline=true \
  test

git diff -- src/test/resources/openapi/collectra-api-v1-baseline.json
