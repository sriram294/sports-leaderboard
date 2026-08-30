#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/common.sh
require_xcode_26_2
mkdir -p "${BUILD_DIR}/logs" "${BUILD_DIR}/attachments"
rm -rf "${BUILD_DIR}/TestResults.xcresult" "${BUILD_DIR}/attachments"
mkdir -p "${BUILD_DIR}/attachments"

readonly simulator_udid="$(available_iphone_udid)"
set +e
xcodebuild test \
  -project "${PROJECT}" \
  -scheme "${SCHEME}" \
  -configuration Debug \
  -destination "platform=iOS Simulator,id=${simulator_udid}" \
  -derivedDataPath "${BUILD_DIR}/DerivedData" \
  -resultBundlePath "${BUILD_DIR}/TestResults.xcresult" \
  -onlyUsePackageVersionsFromResolvedFile \
  SWIFT_TREAT_WARNINGS_AS_ERRORS=YES GCC_TREAT_WARNINGS_AS_ERRORS=YES \
  2>&1 | tee "${BUILD_DIR}/logs/test.log"
readonly test_status=${PIPESTATUS[0]}
set -e

xcrun xcresulttool get test-results tests \
  --path "${BUILD_DIR}/TestResults.xcresult" \
  --format json > "${BUILD_DIR}/test-results.json"
python3 scripts/xcresult_to_junit.py "${BUILD_DIR}/test-results.json" "${BUILD_DIR}/junit.xml"
xcrun xcresulttool export attachments \
  --path "${BUILD_DIR}/TestResults.xcresult" \
  --output-path "${BUILD_DIR}/attachments"
python3 scripts/export_gallery_screenshots.py "${BUILD_DIR}/attachments" "${BUILD_DIR}"

exit "${test_status}"
