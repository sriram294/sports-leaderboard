# iOS working memory

- Active slice: S04 — Matches and recording
- Active branch: `feature/ios-matches`
- Base: refreshed `origin/master` at `4e37d309faadfc047bf89ef6a129b704780c5e76`, after PR [#102](https://github.com/sriram294/sports-leaderboard/pull/102) merged as `57cd90fe6a0fd794720a11a0a874c66e211e81af` on 2026-08-31 and the CI repair landed as `7f493ed3000147fc70744f88599d553cf42a7532`
- Current state: S03 is merged and closed. The Board tab has month/all-time data, cancellation-safe state, podium/rankings, accessibility alternatives, and deterministic scenarios. Matches and Add tabs remain placeholders; no iOS match models, repository, history/detail state, recording form, validation, request-ID injection, or S04 tests exist yet.
- Blockers: Xcode 26.2 is unavailable in this Linux workspace. The generalized Codemagic workflow and repaired dependency validation should provide macOS evidence on the S04 PR. S02's backend self-leave debt remains recorded outside S04 scope.
- Known-good behavior: S00–S03 are merged. The full iOS Swift tree parses under Swift 6.2; group + leaderboard non-UI sources/tests pass strict-concurrency type-checking with warnings as errors; 12/12 deterministic tests pass. The portable credential scan, 12-pin SwiftPM lock including `grpc-binary` 1.69.0, shell parsing, and truthful build-setting failure propagation pass locally. No successful Xcode artifacts have been reported.
- Verification commands: `cd ios && ./scripts/resolve-packages.sh && ./scripts/build.sh && ./scripts/test.sh && ./scripts/verify-repository.sh`; `cd backend && ./mvnw test`
- Exact next action: inventory match list/detail/record DTOs, authorization, score validation, rating deltas, and idempotency behavior; then implement injected S04 models/repository and deterministic validation before replacing the Matches and Add placeholders.

Do not start profile/stat, notification, or account-lifecycle work from later slices. Do not mark S04 `done` until S05's first transition commit after the S04 PR merges.
