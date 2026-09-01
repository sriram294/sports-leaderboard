# iOS roadmap

Base refreshed from `origin/master` on 2026-09-01: `4e37d309faadfc047bf89ef6a129b704780c5e76`.

| Slice | Deliverable | Status | Branch | Depends on | PR | Merge commit | Started | Finished | Verification evidence |
|---|---|---|---|---|---|---|---|---|---|
| S00 | Foundation and delivery controls | `done` | `feature/ios-foundation` | — | [#99](https://github.com/sriram294/sports-leaderboard/pull/99) | `5395ccd0f159e0c627a669ff756025457ef5c7b7` | 2026-08-30 | 2026-08-30 | Repository/docs/secret checks, Swift 6.2 parse, strict-concurrency core type-check, and forced 11-pin lock resolution passed; PR #99 merged 2026-08-30; Vercel checks passed; no Codemagic result was reported |
| S01 | Authentication and session | `done` | `feature/ios-auth` | S00 | [#100](https://github.com/sriram294/sports-leaderboard/pull/100) | `490fecba985f48d10c6ffc72b3d2014390f28c3f` | 2026-08-30 | 2026-08-31 | Backend `./mvnw test`: 147/147 passed on JDK 25 + PostgreSQL 16 with Flyway V1–V15; iOS repository/docs/secret + plist/shell/static checks passed in the Linux workspace; PR #100 merged 2026-08-31 with both Vercel checks passing; Xcode 26.2 execution remains unavailable in this workspace |
| S02 | Groups and app shell | `done` | `feature/ios-groups-shell` | S01 | [#101](https://github.com/sriram294/sports-leaderboard/pull/101) | `8f106168fc08f3e9d76a0681e9b19ef5b95923da` | 2026-08-31 | 2026-08-31 | Linux repository/docs/secret and shell checks passed, all iOS Swift parsed with Swift 6.2, and group repository/state/view-model plus unit tests passed strict-concurrency type-checking; PR #101 merged 2026-08-31 with both Vercel checks passing; Xcode 26.2 build/test/UI/screenshot evidence was not reported and the backend has no self-leave contract |
| S03 | Leaderboard | `done` | `feature/ios-leaderboard` | S02 | [#102](https://github.com/sriram294/sports-leaderboard/pull/102) | `57cd90fe6a0fd794720a11a0a874c66e211e81af` | 2026-08-31 | 2026-08-31 | Full Swift 6.2 parse, strict-concurrency type-check, repository/docs/shell controls, and 12/12 deterministic group + leaderboard tests passed; PR #102 merged 2026-08-31 with both Vercel checks passing; Codemagic Xcode 26.2 validation failed before build due missing `rg` and `grpc-binary`, repaired on `master` by `7f493ed` plus evidence update `4e37d30`; no successful Xcode artifacts were reported |
| S04 | Matches and recording | `in_progress` | `feature/ios-matches` | S03 | pending | pending | 2026-09-01 | — | Transitioned from merged PR #102 at `57cd90fe6a0fd794720a11a0a874c66e211e81af` on repaired base `4e37d309faadfc047bf89ef6a129b704780c5e76`; match history/detail, recording, deterministic repository/state/view-model/UI scenarios, and optional backend idempotency support implemented; backend `./mvnw -q -DskipTests compile` and iOS `./scripts/verify-repository.sh` pass locally; Xcode 26.2/simulator artifacts and PostgreSQL integration results remain pending |
| S05 | Profile, stats, settings, sharing | `not_started` | `feature/ios-profile-stats` | S04 | — | — | — | — | — |
| S06 | Notifications and app updates | `not_started` | `feature/ios-notifications` | S05 | — | — | — | — | — |
| S07 | Account lifecycle and hardening | `not_started` | `feature/ios-account-lifecycle` | S06 | — | — | — | — | — |
| S08 | Release readiness and parity closure | `not_started` | `feature/ios-release` | S07 | — | — | — | — | — |

## Status invariants

The `Status` column must contain exactly one `in_progress` entry until S08 merges. A slice becomes `done` only in the next slice's transition commit. S08 becomes `done` in the permitted documentation-only closure PR.

Evidence must name the command, Xcode version, simulator/device, result, and durable artifact path. A successful local or CI run is required before changing a slice from `in_progress`.
