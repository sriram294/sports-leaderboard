#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/common.sh
require_xcode_26_2
mkdir -p "${BUILD_DIR}/logs"
xcodebuild build \
  -project "${PROJECT}" \
  -scheme "${SCHEME}" \
  -configuration Debug \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath "${BUILD_DIR}/DerivedData" \
  -onlyUsePackageVersionsFromResolvedFile \
  SWIFT_TREAT_WARNINGS_AS_ERRORS=YES GCC_TREAT_WARNINGS_AS_ERRORS=YES \
  | tee "${BUILD_DIR}/logs/build.log"
