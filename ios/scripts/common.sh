#!/bin/bash
set -euo pipefail

readonly PROJECT="Playboard.xcodeproj"
readonly SCHEME="Playboard"
readonly BUILD_DIR="${PWD}/build"

require_xcode_26_2() {
  local version
  version="$(xcodebuild -version | awk 'NR == 1 { print $2 }')"
  if [[ "${version}" != "26.2" ]]; then
    echo "Xcode 26.2 is required; found ${version:-unknown}." >&2
    exit 1
  fi
}

available_iphone_udid() {
  local udid
  udid="$(xcrun simctl list devices available | awk '/iPhone/ { if (match($0, /\([[:xdigit:]-]+\)/)) { candidate = substr($0, RSTART + 1, RLENGTH - 2); if (length(candidate) == 36) { print candidate; exit } } }')"
  if [[ -z "${udid}" ]]; then
    echo "No available iPhone simulator was found." >&2
    exit 1
  fi
  printf '%s\n' "${udid}"
}
