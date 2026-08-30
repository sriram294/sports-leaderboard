#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/common.sh
require_xcode_26_2
mkdir -p "${BUILD_DIR}/logs"
rm -rf "${BUILD_DIR}/Playboard.xcarchive"
xcodebuild archive \
  -project "${PROJECT}" \
  -scheme "${SCHEME}" \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -archivePath "${BUILD_DIR}/Playboard.xcarchive" \
  -derivedDataPath "${BUILD_DIR}/DerivedData" \
  -onlyUsePackageVersionsFromResolvedFile \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY= \
  SWIFT_TREAT_WARNINGS_AS_ERRORS=YES GCC_TREAT_WARNINGS_AS_ERRORS=YES \
  | tee "${BUILD_DIR}/logs/archive.log"
test -d "${BUILD_DIR}/Playboard.xcarchive"
