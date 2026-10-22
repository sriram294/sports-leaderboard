#!/bin/bash
# Linux-only partial verification for machines without Xcode: type-checks every iOS source
# file that does not import an Apple UI/SDK framework (models, repositories, view models)
# under Swift 6 strict concurrency with warnings as errors, then runs the unit tests that
# only depend on them. SwiftUI screens, Firebase, and UI tests still need the macOS
# "iOS build" workflow (.github/workflows/ios.yml). Requires Docker.
set -euo pipefail
cd "$(dirname "$0")/.."
exec docker run --rm -v "${PWD}":/src:ro swift:6.2 bash /src/scripts/linux/run-in-container.sh
