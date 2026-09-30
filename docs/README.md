# Collectra Documentation Index

Status: CANONICAL — current documentation entry point  
Reviewed against: `main@9b3369d52af5bfaf0436019b4b8eb97e51f0b956`  
Date: 2026-09-30

## 1. Source-of-truth precedence

When documentation conflicts, use this order:

1. current code, migrations, generated OpenAPI and executable tests on `main`;
2. [Collectra System Overview](architecture/collectra-system-overview.md);
3. [Project Assurance Ledger](audit/project-assurance-ledger.md), [A01 Product Capability Coverage](audit/a01-product-capability-coverage.md), [A02 Golden Journey](audit/a02-golden-journey.md);
4. [Technical Architecture Audit Ledger](architecture/technical-audit-ledger.md);
5. [Pre-Channel Release Gate](qa/pre-channel-release-gate.md);
6. current implementation contracts explicitly marked CANONICAL/ACTIVE;
7. historical slice, FW, VC and hardening plans.

A historical document may still contain valid design rationale or invariants, but its branch name, baseline SHA, readiness label and execution order are not current project status unless reaffirmed by a current canonical document.

## 2. Current canonical set

| Concern | Canonical document |
|---|---|
| System architecture and boundaries | [architecture/collectra-system-overview.md](architecture/collectra-system-overview.md) |
| Project-wide assurance program | [audit/project-assurance-ledger.md](audit/project-assurance-ledger.md) |
| Product capability coverage | [audit/a01-product-capability-coverage.md](audit/a01-product-capability-coverage.md) |
| End-to-end Golden/Failure Journey | [audit/a02-golden-journey.md](audit/a02-golden-journey.md) |
| Technical / production audit | [architecture/technical-audit-ledger.md](architecture/technical-audit-ledger.md) |
| Pre-provider release qualification | [qa/pre-channel-release-gate.md](qa/pre-channel-release-gate.md) |
| Public money wire contract | [specs/public-money-decimal-string-contract.md](specs/public-money-decimal-string-contract.md) |
| OpenAPI compatibility gate | [specs/openapi-compatibility-gate.md](specs/openapi-compatibility-gate.md) |
| Production provider acceptance boundary | [specs/real-kumomta-environment-acceptance.md](specs/real-kumomta-environment-acceptance.md) |

## 3. Historical documentation

The following families are retained for traceability and implementation rationale, not as current execution plans:

- `docs/roadmap/backend-mvp-roadmap.md` and earlier backend slice roadmaps;
- `docs/specs/frontendweb-fw*.md` FW3-FW12 plans;
- `docs/specs/slice-02..10*.md` historical delivery slices;
- PRE-VC9 and functional-hardening execution briefs under `docs/qa/`;
- `docs/qa/CODEX-START-HERE.md` and `docs/qa/final-handoff-audit.md`;
- old branch-specific handoff/remediation documents.

Do not delete these artifacts: they explain why current contracts and invariants exist.

## 4. Documentation maintenance rule

A current document must identify its status and, when evidence-sensitive, its audited SHA/run. Branch-specific instructions must not be presented as current after the branch has merged or been deleted.

When implementation changes:
1. update the closest canonical contract/ledger;
2. retain historical plans unless misleading;
3. mark superseded plans explicitly;
4. never promote a release or audit row to `VERIFIED` from source inspection alone;
5. prefer links to canonical ledgers over copying current status into multiple documents.
