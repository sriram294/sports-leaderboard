#!/usr/bin/env python3
"""Fail when a repository-local Markdown link under docs/ios is unresolved."""

import re
import sys
from pathlib import Path
from urllib.parse import unquote


root = Path(__file__).resolve().parents[2]
failures = []
for document in sorted((root / "docs" / "ios").rglob("*.md")):
    for target in re.findall(r"]\(([^)]+)\)", document.read_text(encoding="utf-8")):
        if target.startswith(("http://", "https://", "#", "mailto:")):
            continue
        path_text = unquote(target.split("#", 1)[0]).strip("<>")
        if path_text and not (document.parent / path_text).resolve().exists():
            failures.append(f"{document.relative_to(root)} -> {target}")

if failures:
    print("Unresolved iOS documentation links:", file=sys.stderr)
    print("\n".join(failures), file=sys.stderr)
    raise SystemExit(1)
