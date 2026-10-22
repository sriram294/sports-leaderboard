# S09 hand-off — team-v2 leaderboard parity

Updated 2026-09-15 after team-v2 implementation PR [#126](https://github.com/sriram294/sports-leaderboard/pull/126) and caption cleanup PR [#127](https://github.com/sriram294/sports-leaderboard/pull/127) merged into `master` (`2be8c64`).

Board range direction updated 2026-10-02: the leaderboard now shows the live
current month plus up to three captured completed-month snapshots. All Time
remains in Stats/Profile but is no longer a Board choice. Match detail rating
deltas use a fresh Asia/Kolkata month prior. See IOS-DEC-015; S09 remains in
progress pending macOS/Xcode validation.

## Current state

- iOS `LeaderboardEntry` and `Leaderboard` decode the optional team-v2 metadata
  safely and retain legacy fallback behavior.
- Board presentation uses fresh monthly Skill ratings and cumulative All Time ratings, separates
  qualified and provisional rows, preserves the existing “N more to rank” copy,
  and keeps qualified-only podium/share behavior aligned with Android and PWA.
- The board intentionally has no explanatory model, threshold, or
  partner-variety subcaptions.

## Verification recorded

- PWA: `npm test -- --run` — 10 files, 60 tests passed.
- Android: `./gradlew :app:testDebugUnitTest` — BUILD SUCCESSFUL.
- The implementation session exercised the backend against local PostgreSQL;
  the temporary Docker database was stopped after verification.

## Pending before S09 closure

Run on macOS with Xcode 26.2 and record durable paths for build, tests,
accessibility/large Dynamic Type checks, and light/dark screenshots. Keep S09
`in_progress` until that evidence exists; do not claim store signing or
publication from this Linux workspace.

## Android parity pass — 2026-10-22

- Branch `feature/ios-parity-gaps` off `master` `41f7a1c`; scope and file map in
  [parity-matrix.md](parity-matrix.md#android-parity-pass-2026-10-22-branch-featureios-parity-gaps).
- Linux verification (no Xcode here): `ios/scripts/linux-check.sh` type-checks the 37 non-UI
  sources under Swift 6 strict concurrency with warnings as errors and runs 58 Swift Testing
  cases — all passing. SwiftUI screens, Firebase, and UI tests are unverified until the manual
  **iOS build** workflow runs on `macos-26`.
- `scripts/verify-repository.sh` already fails on `master`: it only counts slices `S00`–`S08`
  as in progress, so with S09 active it demands an empty `memory.md`. The workflow runs the
  secret and signing-file checks directly instead.
- Push needs `GoogleService-Info.plist` (kept out of git) and an APNs key in Firebase; the
  backend's FCM sender has only been exercised with Android tokens.
