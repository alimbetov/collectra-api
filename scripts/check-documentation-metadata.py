#!/usr/bin/env python3
"""Detect stale execution metadata in current documentation headers."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
LEGACY_BRANCH = re.compile(
    r"(?:fix/pre-channel-release-gate|fix/audit-defects-and-release-gate-alignment|"
    r"fix/project-consistency-defects|spec/functional-hardening-user-journeys|"
    r"audit/a01-a12-project-assurance)"
)


def main() -> int:
    failures: list[str] = []
    checked = 0
    for path in sorted(DOCS.rglob("*.md")):
        text = path.read_text(encoding="utf-8")
        header = "\n".join(text.splitlines()[:30])
        checked += 1
        lifecycle = next(
            (line for line in text.splitlines()[:12] if line.startswith("Documentation lifecycle:")),
            "",
        )
        historical_context = "HISTORICAL" in lifecycle or "SUPPORTING REFERENCE" in lifecycle

        for line in header.splitlines():
            stripped = line.strip()
            if LEGACY_BRANCH.search(stripped):
                if stripped.startswith(("Historical ", "Documentation lifecycle:", "> Historical")):
                    continue
                if historical_context and not stripped.startswith(("Branch:", "Baseline:", "Current ")):
                    continue
                failures.append(f"{path.relative_to(ROOT)}: stale execution metadata: {stripped}")

            if stripped.startswith("Branch:") and "main" not in stripped and not historical_context:
                failures.append(f"{path.relative_to(ROOT)}: active branch metadata requires reconciliation: {stripped}")

    print(f"Checked documentation metadata in {checked} Markdown files.")
    if failures:
        print("Stale documentation metadata:", file=sys.stderr)
        for failure in sorted(set(failures)):
            print(f"  - {failure}", file=sys.stderr)
        return 1
    print("No stale active branch metadata detected.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
