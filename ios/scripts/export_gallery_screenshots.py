#!/usr/bin/env python3
"""Copy named gallery attachments from xcresulttool's UUID-based export."""

import json
import shutil
import sys
from pathlib import Path


attachments_root, output_root = map(Path, sys.argv[1:3])
records = []
for manifest_path in attachments_root.rglob("manifest.json"):
    payload = json.loads(manifest_path.read_text(encoding="utf-8"))
    stack = [payload]
    while stack:
        value = stack.pop()
        if isinstance(value, dict):
            if "exportedFileName" in value and "suggestedHumanReadableName" in value:
                records.append((manifest_path.parent, value))
            stack.extend(value.values())
        elif isinstance(value, list):
            stack.extend(value)

for appearance in ("light", "dark"):
    marker = f"gallery-{appearance}-top"
    match = next(
        (
            (directory, record)
            for directory, record in records
            if marker in str(record["suggestedHumanReadableName"]).lower()
        ),
        None,
    )
    if match is None:
        raise SystemExit(f"Missing {marker} screenshot attachment.")
    directory, record = match
    shutil.copy2(directory / record["exportedFileName"], output_root / f"gallery-{appearance}.png")
