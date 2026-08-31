# iOS working memory

- Active slice: S01 — Authentication and session
- Active branch: `feature/ios-auth`
- Base: refreshed `origin/master` at merge commit `5395ccd0f159e0c627a669ff756025457ef5c7b7` (PR [#99](https://github.com/sriram294/sports-leaderboard/pull/99), merged 2026-08-30)
- Current state: S01 implementation is present in the worktree. The backend has provider-neutral identity linking, Apple token verification, and additive `authProviders`; iOS has injected native providers, Keychain sessions, coalesced refresh, restore/recovery/logout routing, login UI, and deterministic unit/UI launch fakes. Full Xcode and PostgreSQL-backed verification remains outstanding.
- Blockers: real Google, Apple, and Firebase configuration is not present. Implementation and deterministic tests can proceed with injected adapters; configured end-to-end provider verification will require externally supplied non-secret identifiers and authorized provider environments.
- Known-good behavior: S00's foundation remains intact. On 2026-08-31, the complete backend suite passed 147/147 tests on JDK 25 against PostgreSQL 16 with Flyway V1–V15, and `ios/scripts/verify-repository.sh` plus plist/shell/static checks passed in the Linux workspace. Xcode 26.2 execution is unavailable in this workspace and remains outstanding for S01.
- Verification commands: `cd ios && ./scripts/resolve-packages.sh && ./scripts/build.sh && ./scripts/test.sh && ./scripts/verify-repository.sh`; `cd backend && ./mvnw test`
- Exact next action: run the iOS build/test/archive chain on Xcode 26.2; fix any strict-concurrency or UI-test failures, capture `.xcresult`, JUnit, archive, and light/dark login screenshots, then configure real provider identifiers for authorized end-to-end verification.

Do not start group or app-shell work from S02. Do not mark S01 `done` until S02's first transition commit after the S01 PR merges.
