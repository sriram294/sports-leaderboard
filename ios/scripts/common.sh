#!/bin/bash
set -euo pipefail

readonly PROJECT="Playboard.xcodeproj"
readonly SCHEME="Playboard"
readonly BUILD_DIR="${PWD}/build"

# The project is pinned to Xcode 26.2; CI may opt into another 26.x via PLAYBOARD_XCODE_VERSION.
readonly REQUIRED_XCODE="${PLAYBOARD_XCODE_VERSION:-26.2}"

require_xcode_26_2() {
  local version
  version="$(xcodebuild -version | awk 'NR == 1 { print $2 }')"
  if [[ "${version}" != "${REQUIRED_XCODE}" ]]; then
    echo "Xcode ${REQUIRED_XCODE} is required; found ${version:-unknown}." >&2
    exit 1
  fi
}

# Non-secret provider identifiers injected from the environment (see
# Playboard/Resources/Configuration/README.md). Unset values keep the empty project defaults,
# which build an app that shows its configuration-unavailable state.
PROVIDER_SETTINGS=()
for setting in API_BASE_URL GOOGLE_CLIENT_ID GOOGLE_REVERSED_CLIENT_ID; do
  if [[ -n "${!setting:-}" ]]; then
    PROVIDER_SETTINGS+=("${setting}=${!setting}")
  fi
done

available_iphone_udid() {
  local udid
  udid="$(xcrun simctl list devices available | awk '/iPhone/ { if (match($0, /\([[:xdigit:]-]+\)/)) { candidate = substr($0, RSTART + 1, RLENGTH - 2); if (length(candidate) == 36) { print candidate; exit } } }')"
  if [[ -z "${udid}" ]]; then
    echo "No available iPhone simulator was found." >&2
    exit 1
  fi
  printf '%s\n' "${udid}"
}
