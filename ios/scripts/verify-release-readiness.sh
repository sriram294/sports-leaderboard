#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."

test -f Playboard/Resources/PrivacyInfo.xcprivacy
python3 scripts/verify_no_secrets.py Playboard ../docs/ios ../codemagic.yaml
python3 scripts/verify_documentation.py
test -f Playboard.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved
grep -q '"pins"' Playboard.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved
grep -q 'CODE_SIGNING_ALLOWED = NO' Playboard.xcodeproj/project.pbxproj
grep -q 'SWIFT_STRICT_CONCURRENCY = complete' Playboard.xcodeproj/project.pbxproj

echo "Release-readiness controls verified; Xcode build, test, archive, accessibility, and performance evidence still require macOS/Xcode 26.2."
