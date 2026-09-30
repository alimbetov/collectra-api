#!/usr/bin/env python3
"""Fail when a repository Markdown link points to a missing local file."""

from __future__ import annotations

import re
import sys
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
SCOPES = [ROOT / "README.md", ROOT / "frontendweb" / "README.md", ROOT / "docs"]
LINK = re.compile(r"(?<!!)\[[^\]]*\]\(([^)]+)\)")


def markdown_files():
    for scope in SCOPES:
        if scope.is_file():
            yield scope
        elif scope.is_dir():
            yield from sorted(scope.rglob("*.md"))


def local_target(source: Path, raw: str) -> Path | None:
    value = raw.strip().strip("<>")
    if not value or value.startswith(("#", "http://", "https://", "mailto:")):
        return None
    value = unquote(value.split("#", 1)[0].split("?", 1)[0])
    if not value:
        return None
    return (ROOT / value.lstrip("/")) if value.startswith("/") else (source.parent / value)


def main() -> int:
    failures: list[str] = []
    checked = 0
    for source in markdown_files():
        text = source.read_text(encoding="utf-8")
        for match in LINK.finditer(text):
            target = local_target(source, match.group(1))
            if target is None:
                continue
            checked += 1
            if not target.resolve().exists():
                failures.append(
                    f"{source.relative_to(ROOT)} -> {match.group(1)} "
                    f"(resolved: {target.resolve().relative_to(ROOT) if ROOT in target.resolve().parents else target.resolve()})"
                )
    print(f"Checked {checked} local Markdown links.")
    if failures:
        print("Broken local Markdown links:", file=sys.stderr)
        for failure in failures:
            print(f"  - {failure}", file=sys.stderr)
        return 1
    print("All local Markdown links resolve.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
