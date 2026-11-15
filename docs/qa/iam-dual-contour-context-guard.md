# Collectra IAM — mandatory dual-contour context guard

Status: binding project-context correction. Applies to all future IAM work and reviews on this branch.

## Recorded assistant error (2026-11-15)
The assistant incorrectly asked whether the two assets meant React UI and REST IAM API or two consumer applications, despite the approved AC/COL architecture. It then framed App Common primarily as a BFF for Collectra. This was a failure to follow the existing specification, not an open architecture decision.

## Non-negotiable architecture
- **App Common (AC)**: independent runtime, own persistence and REST API; global Keycloak identity integration, global blocks, revocation and consumer enforcement.
- **Collectra (COL)**: independent runtime, own persistence and REST API; local user UUID, verified issuer/subject identity link, tenant membership, business RBAC and migration authority.
- AC does not own COL tenant memberships or business permissions. COL does not own global identity or global revocation policy.
- Browser UI and BFF are delivery surfaces, not replacements for either contour or the AC/COL integration contract.
- Both contours must be independently buildable, deployable, testable, and recoverable; cross-contour integration requires its own E2E proof.

## Required pre-change checklist
1. Read the existing IAM ADR/specification and this guard before implementing IAM changes.
2. Name the owning contour (AC, COL, or integration contract) for every endpoint, table, test and operational responsibility.
3. Check for architectural contradictions; do not invent or reopen settled decisions.
4. Require separate AC and COL build/test evidence and real dual-contour PostgreSQL/Keycloak E2E evidence.
5. Keep release HOLD and do not claim GREEN until actual logs/checks exist.
6. If an implementation deviates from the approved design, report the deviation and obtain explicit approval rather than silently redefining the model.

This guard records the correction; it does not claim any unexecuted test passed.
