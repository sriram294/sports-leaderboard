#!/bin/bash
# Runs inside the swift:6.2 container with ios/ mounted at /src (see ../linux-check.sh).
set -uo pipefail
cd /src
OUT=/tmp/playboard-linux; rm -rf "${OUT}"; mkdir -p "${OUT}"
HERE=/src/scripts/linux

swiftc -parse-as-library -emit-module -emit-library -module-name Combine \
  -o "${OUT}/libCombine.so" -emit-module-path "${OUT}/Combine.swiftmodule" "${HERE}/CombineShim.swift" || exit 1

# App sources that need no Apple-only UI or SDK framework (AppleStubs.swift fills in the few
# provider types AppEnvironment references); SwiftUI screens are left to Xcode.
APP=$(grep -LE "^import (SwiftUI|UIKit|GoogleSignIn|AuthenticationServices|FirebaseMessaging|FirebaseCore|PhotosUI|Charts|UserNotifications)" \
  $(find Playboard -name "*.swift"))
echo "== type-checking $(echo ${APP} | wc -w) app files (Swift 6, complete concurrency, warnings as errors)"
swiftc -parse-as-library -emit-module -emit-library -enable-testing -module-name Playboard \
  -swift-version 6 -strict-concurrency=complete -warnings-as-errors \
  -I "${OUT}" -L "${OUT}" -lCombine -o "${OUT}/libPlayboard.so" -emit-module-path "${OUT}/Playboard.swiftmodule" ${APP} "${HERE}/AppleStubs.swift" || exit 2

# Test files that import Apple-only frameworks or exercise excluded types.
TESTS=$(ls PlayboardTests/*.swift | grep -v 'DesignSystemTests\|AuthenticationViewModelTests')
echo "== compiling $(echo ${TESTS} | wc -w) test files"
swiftc -parse-as-library -swift-version 6 -warnings-as-errors -I "${OUT}" -L "${OUT}" -lCombine -lPlayboard \
  -Xlinker -rpath -Xlinker "${OUT}" -o "${OUT}/tests" ${TESTS} "${HERE}/TestMain.swift" "${HERE}/TestPrelude.swift" || exit 3
"${OUT}/tests"
