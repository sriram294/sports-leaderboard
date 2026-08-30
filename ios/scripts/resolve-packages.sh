#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/common.sh
require_xcode_26_2
test -f Playboard.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved
mkdir -p "${BUILD_DIR}/logs"
xcodebuild -resolvePackageDependencies \
  -project "${PROJECT}" \
  -scheme "${SCHEME}" \
  -onlyUsePackageVersionsFromResolvedFile \
  | tee "${BUILD_DIR}/logs/resolve-packages.log"
