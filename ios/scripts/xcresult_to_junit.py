#!/usr/bin/env python3
"""Convert Xcode's stable test-results JSON tree into a compact JUnit report."""

import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def test_cases(node):
    if isinstance(node, dict):
        if node.get("nodeType") == "Test Case":
            yield node
        for value in node.values():
            yield from test_cases(value)
    elif isinstance(node, list):
        for value in node:
            yield from test_cases(value)


source, destination = map(Path, sys.argv[1:3])
payload = json.loads(source.read_text(encoding="utf-8"))
cases = list(test_cases(payload))
failures = sum(case.get("result") not in {"Passed", "Skipped", "Expected Failure"} for case in cases)
skipped = sum(case.get("result") == "Skipped" for case in cases)
suite = ET.Element("testsuite", name="Playboard", tests=str(len(cases)), failures=str(failures), skipped=str(skipped))

for case in cases:
    element = ET.SubElement(
        suite,
        "testcase",
        classname=str(case.get("targetName", "Playboard")),
        name=str(case.get("name", "Unnamed test")),
        time=str(case.get("duration", 0)),
    )
    result = case.get("result")
    if result == "Skipped":
        ET.SubElement(element, "skipped")
    elif result not in {"Passed", "Expected Failure"}:
        failure = ET.SubElement(element, "failure", message=str(result or "Failed"))
        failure.text = str(case.get("failureMessage", "See TestResults.xcresult for details."))

ET.ElementTree(suite).write(destination, encoding="utf-8", xml_declaration=True)
