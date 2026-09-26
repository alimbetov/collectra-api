# Remediation Branch Handoff

Branch: `fix/pre-vc9-functional-hardening-remediation`
Base specification SHA: `fae4e1ef2a30a716325fc4bbd1822694243ef498`

Implementation task: execute `docs/qa/pre-vc9-functional-hardening-remediation-spec.md` using the normative contracts inherited from the spec branch.

This branch is for production code, migrations, frontend code, tests and documentation evidence required to remediate pre-VC9 hardening findings. Do not implement VC-9.

Start by regenerating the inventory and expanding the completion ledger; then execute Phase A-G. Do not treat the known FH-001..007 list as exhaustive.

Mandatory code companion: `docs/qa/pre-vc9-remediation-code-level-guide.md`. It binds each WP to current Collectra classes, frontend patterns, Liquibase conventions and test architecture. Read it before changing production code.
