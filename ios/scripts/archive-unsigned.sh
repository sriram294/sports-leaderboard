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

# Package the unsigned device app so Sideloadly can sign it for a test device.
# This is deliberately not an installable or distributable IPA until it is signed.
readonly ipa_staging="$(mktemp -d "${BUILD_DIR}/ipa-staging.XXXXXX")"
trap 'rm -rf "${ipa_staging}"' EXIT
mkdir -p "${ipa_staging}/Payload"
cp -R "${BUILD_DIR}/Playboard.xcarchive/Products/Applications/Playboard.app" "${ipa_staging}/Payload/"
ditto -c -k --sequesterRsrc --keepParent "${ipa_staging}/Payload" "${BUILD_DIR}/Playboard-unsigned.ipa"
test -f "${BUILD_DIR}/Playboard-unsigned.ipa"
