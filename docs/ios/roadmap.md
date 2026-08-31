# iOS roadmap

Base refreshed from `origin/master` on 2026-08-30: `57fb317902eb3198423f043aca45977b9f46d2c6`.

| Slice | Deliverable | Status | Branch | Depends on | PR | Merge commit | Started | Finished | Verification evidence |
|---|---|---|---|---|---|---|---|---|---|
| S00 | Foundation and delivery controls | `done` | `feature/ios-foundation` | — | [#99](https://github.com/sriram294/sports-leaderboard/pull/99) | `5395ccd0f159e0c627a669ff756025457ef5c7b7` | 2026-08-30 | 2026-08-30 | Repository/docs/secret checks, Swift 6.2 parse, strict-concurrency core type-check, and forced 11-pin lock resolution passed; PR #99 merged 2026-08-30; Vercel checks passed; no Codemagic result was reported |
| S01 | Authentication and session | `in_progress` | `feature/ios-auth` | S00 | pending | pending | 2026-08-30 | — | 2026-08-31 partial: backend 147/147 tests passed on JDK 25 + PostgreSQL 16 with Flyway V1–V15; iOS repository/docs/secret + plist/shell checks passed; Xcode 26.2 evidence pending |
| S02 | Groups and app shell | `not_started` | `feature/ios-groups-shell` | S01 | — | — | — | — | — |
| S03 | Leaderboard | `not_started` | `feature/ios-leaderboard` | S02 | — | — | — | — | — |
| S04 | Matches and recording | `not_started` | `feature/ios-matches` | S03 | — | — | — | — | — |
| S05 | Profile, stats, settings, sharing | `not_started` | `feature/ios-profile-stats` | S04 | — | — | — | — | — |
| S06 | Notifications and app updates | `not_started` | `feature/ios-notifications` | S05 | — | — | — | — | — |
| S07 | Account lifecycle and hardening | `not_started` | `feature/ios-account-lifecycle` | S06 | — | — | — | — | — |
| S08 | Release readiness and parity closure | `not_started` | `feature/ios-release` | S07 | — | — | — | — | — |

## Status invariants

The `Status` column must contain exactly one `in_progress` entry until S08 merges. A slice becomes `done` only in the next slice's transition commit. S08 becomes `done` in the permitted documentation-only closure PR.

Evidence must name the command, Xcode version, simulator/device, result, and durable artifact path. A successful local or CI run is required before changing a slice from `in_progress`.
