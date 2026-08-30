# iOS working memory

- Active slice: S01 — Authentication and session
- Active branch: `feature/ios-auth`
- Base: refreshed `origin/master` at merge commit `5395ccd0f159e0c627a669ff756025457ef5c7b7` (PR [#99](https://github.com/sriram294/sports-leaderboard/pull/99), merged 2026-08-30)
- Current state: transition commit in progress; S00 is recorded `done`, S01 is the only `in_progress` slice, and S01 product implementation has not started.
- Blockers: real Google, Apple, and Firebase configuration is not present. Implementation and deterministic tests can proceed with injected adapters; configured end-to-end provider verification will require externally supplied non-secret identifiers and authorized provider environments.
- Known-good behavior: S00's project, controls, design foundation, 11-pin SPM graph, and scripts are present on `master`. Repository/docs/secret checks, Swift 6.2 parsing, strict-concurrency core type-checking, and forced lock resolution passed before PR #99 merged. GitHub reported passing Vercel checks and no Codemagic result.
- Verification commands: `cd ios && ./scripts/resolve-packages.sh && ./scripts/build.sh && ./scripts/test.sh && ./scripts/verify-repository.sh`; `cd backend && ./mvnw test`
- Exact next action: implement the S01 backend provider-neutral authentication contract and native injected auth/session boundaries described in `slices/S01.md`, beginning with focused failing tests and credential-free provider fakes.

Do not start group or app-shell work from S02. Do not mark S01 `done` until S02's first transition commit after the S01 PR merges.
