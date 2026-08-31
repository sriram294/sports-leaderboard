#!/usr/bin/env python3
"""Fail when repository-owned iOS paths contain likely credential material."""

import re
import sys
from pathlib import Path


PATTERN = re.compile(
    r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"
    r"|AIza[0-9A-Za-z_-]{30,}"
    r"|[A-Za-z0-9_]*SECRET\s*=\s*\S+"
)
EXCLUDED_DIRECTORIES = {".git", "build", "DerivedData"}


def files_under(path: Path):
    if path.is_file():
        yield path
        return
    for candidate in path.rglob("*"):
        if not candidate.is_file():
            continue
        if any(part in EXCLUDED_DIRECTORIES for part in candidate.parts):
            continue
        if candidate.name.endswith(".example"):
            continue
        yield candidate


findings = []
seen = set()
for argument in sys.argv[1:]:
    for candidate in files_under(Path(argument)):
        resolved = candidate.resolve()
        if resolved in seen:
            continue
        seen.add(resolved)
        try:
            lines = candidate.read_text(encoding="utf-8").splitlines()
        except (OSError, UnicodeDecodeError):
            continue
        for line_number, line in enumerate(lines, start=1):
            if PATTERN.search(line):
                findings.append(f"{candidate}:{line_number}")

if findings:
    print("Possible credential material found:", file=sys.stderr)
    print("\n".join(findings), file=sys.stderr)
    raise SystemExit(1)

print("Credential-pattern scan passed.")
