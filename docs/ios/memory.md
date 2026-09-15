# S09 hand-off — team-v2 leaderboard parity

Updated 2026-09-15 after team-v2 implementation PR [#126](https://github.com/sriram294/sports-leaderboard/pull/126) and caption cleanup PR [#127](https://github.com/sriram294/sports-leaderboard/pull/127) merged into `master` (`2be8c64`).

## Current state

- iOS `LeaderboardEntry` and `Leaderboard` decode the optional team-v2 metadata
  safely and retain legacy fallback behavior.
- Board presentation carries cumulative Skill ratings across ranges, separates
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
