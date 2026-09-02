# iOS release-readiness checklist

This checklist is the S08 hand-off for the unsigned iOS release candidate. It
does not authorize signing or App Store publication.

## Repository controls

- [x] `PrivacyInfo.xcprivacy` is present and declares no collected data or tracking.
- [x] Provider and signing material remains external to the repository.
- [x] Swift Package Manager dependencies are locked by `Package.resolved`.
- [x] Debug and Release builds disable code signing in the project settings.
- [x] Swift strict concurrency and warnings-as-errors are enabled.

Run `cd ios && ./scripts/verify-release-readiness.sh`.

## Required Xcode 26.2 evidence

Run the commands from `docs/ios/README.md` on the pinned macOS runner and retain:

- `ios/build/TestResults.xcresult`, `ios/build/junit.xml`, and `ios/build/test-results.json`
- `ios/build/Playboard.xcarchive` and its dSYM bundle
- `ios/build/logs/*.log`
- light/dark gallery and feature-state screenshots under `ios/build/`
- simulator/device matrix, accessibility audit, reduced-motion/large-text checks,
  performance baseline, dependency-license review, and secret/signing scan

## Release decision

S08 must not be marked complete until the Xcode artifacts and audit results are
attached to the S08 PR, every parity exception is explicitly accepted, and the
unsigned archive is reproducible. Signing credentials and App Store publication
remain a separately authorized follow-up.
