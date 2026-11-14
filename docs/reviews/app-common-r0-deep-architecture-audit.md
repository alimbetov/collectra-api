# App Common R0 — Deep Architecture Audit and Consistency Review
Date: 2026-11-14
Status: CONDITIONAL DESIGN BASELINE / IMPLEMENTATION HOLD
Review type: architecture and specification consistency review by assistant; NOT independent S2 approval.

## Reviewed sources
- docs/specs/app-common-r0-functional-technical-spec.md
- docs/adr/adr-iam-004-app-common-platform-boundary.md
- docs/adr/adr-iam-005-keycloak-jwt-bearer-contract.md
- docs/specs/iam-r1-2-canonical-multiapp-identity-contract.md
- docs/reviews/iam-r1-1-technical-audit.md

Review method: GitHub branch document reads and contract cross-check; earlier source findings in IAM-R1.1 audit are inherited as *unverified historical observations*. No new source-code-wide inspection, tests, builds, deployed Keycloak, SMTP, PostgreSQL or CI execution was performed.

## Verdict
Product boundary and token issuer are now coherent: **App Common** owns shared platform infrastructure; **Collectra** owns business authorization; **Keycloak** issues JWT. But existing spec had unqualified assumptions about suspension, notification delivery, recipient verification and realm provisioning. Those were corrected in APP-COMMON-R0 section 12. Remaining decisions and runtime evidence are required before implementation approval/production.

## Findings and dispositions
| ID | Severity | Finding | Disposition |
|---|---|---|---|
| ARCH-01 | P0 | Application SUSPENDED conflates blocking new login with invalidating previously issued JWT | SPECIFIED in section 12.2; runtime propagation test pending |
| ARCH-02 | P0 | SMTP timeout and provider acceptance cannot guarantee exactly-once or DELIVERED | SPECIFIED in section 12.3; provider policy pending |
| ARCH-03 | P0 | Operator, service, business-user authority boundaries insufficiently explicit | SPECIFIED in section 12.1; security test pending |
| ARCH-04 | P1 | Multiple documents have conflicting platform naming and source precedence | ADR-IAM-004 supersedes earlier ownership; canonical order below |
| ARCH-05 | P1 | Realm provisioning may adopt colliding realm or duplicate after timeout | SPECIFIED in section 12.5; Keycloak version/tests pending |
| ARCH-06 | P1 | Notification recipient resolver lacks freshness and trust protocol | SPECIFIED in section 12.4; exact transport pending |
| ARCH-07 | P1 | MFA assurance is not pinned to executable Keycloak configuration | IAM-R1.3 mapping requirement retained; version/tests pending |
| ARCH-08 | P1 | No fixed token TTL/client allowlist and missing-audience rejection matrix | SPECIFIED in section 12.6; environment values pending |
| ARCH-09 | P1 | Shared PostgreSQL may blur cross-app DB access | SPECIFIED in section 12.7; SQL privileges/tests pending |
| ARCH-10 | P1 | Legacy Collectra auth still issues local JWT and parses subject as local UUID per earlier audit | Migration plan exists; implementation not verified |
| ARCH-11 | P1 | Operator realm lifecycle and bootstrap/break-glass controls not executable | Decision and negative tests required |
| ARCH-12 | P2 | Observability/retention/consent and data residency lack approved operational policy | Release gate; pending owner decisions |

## Canonical source precedence
1. ADR-IAM-004: product ownership and independent deployment (App Common vs Collectra).
2. ADR-IAM-005: Keycloak JWT bearer issuer, validation and audience rules.
3. APP-COMMON-R0 section 12: detailed platform execution semantics and hardening; supersedes sections 1–11 on the same topic.
4. APP-COMMON-R0 sections 1–11: scope, API and acceptance baseline.
5. IAM-R1.2/R1.3: app-local identity migration, onboarding, MFA and historical compatibility where not superseded above.
6. Earlier ADR-IAM-003 and IAM-R1.1 drafts: historical context, non-normative when conflicting.

## Evidence and release gates
- **Documentation remediation:** completed for ARCH-01..09 as stated, not a proof of implementation.
- **Implementation:** NOT VERIFIED; App Common independent repository creation not verified.
- **PostgreSQL/Keycloak/HTTP/SMTP E2E:** NOT RUN in this review.
- **Independent S2 approval:** NOT OBTAINED.
- **Production:** HOLD until decisions DEC-01..13 are signed, P0/P1 runtime risks closed, threat model reviewed, two-realm security isolation proven, migration regression GREEN and restore drill meets approved RPO/RTO.
- **Next step:** independent S2 review of revised spec, then RED integration fixtures for HARD-01..16 and original AC-01..20 before GREEN implementation. No merge to main without explicit authorization.
