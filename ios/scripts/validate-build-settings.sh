#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/common.sh
require_xcode_26_2

settings=""
if ! settings="$(xcodebuild -project "${PROJECT}" -scheme "${SCHEME}" -configuration Release -showBuildSettings -disableAutomaticPackageResolution -onlyUsePackageVersionsFromResolvedFile 2>&1)"; then
  printf '%s\n' "${settings}" >&2
  echo "Unable to read build settings; resolve the locked package graph first." >&2
  exit 1
fi
readonly settings
check_setting() {
  local key="$1"
  local expected="$2"
  if ! grep -Eq "^[[:space:]]*${key} = ${expected}$" <<< "${settings}"; then
    echo "Required build setting ${key}=${expected} is missing." >&2
    exit 1
  fi
}

check_setting PRODUCT_BUNDLE_IDENTIFIER com.org.playboard
check_setting IPHONEOS_DEPLOYMENT_TARGET 17.0
check_setting SWIFT_VERSION 6.0
check_setting SWIFT_STRICT_CONCURRENCY complete
check_setting SWIFT_TREAT_WARNINGS_AS_ERRORS YES
check_setting TARGETED_DEVICE_FAMILY 1
check_setting CODE_SIGNING_ALLOWED NO
/usr/libexec/PlistBuddy -c 'Print :UISupportedInterfaceOrientations:0' Playboard/Resources/Info.plist | grep -Fx UIInterfaceOrientationPortrait
