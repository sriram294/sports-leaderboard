# iOS working memory

- Active slice: S02 — Groups and app shell
- Active branch: `feature/ios-groups-shell`
- Base: refreshed `origin/master` at merge commit `490fecba985f48d10c6ffc72b3d2014390f28c3f` (PR [#100](https://github.com/sriram294/sports-leaderboard/pull/100), merged 2026-08-31)
- Current state: S01 is merged and closed. Authenticated users still land on the temporary signed-in account screen; S02 group repository, selected-group persistence, native tab shell, group management UI, and deterministic group tests have not started.
- Blockers: Xcode 26.2 is unavailable in this Linux workspace, so native build, UI-test, archive, and screenshot evidence must be captured in Codemagic or on a pinned macOS environment. Real provider identifiers remain external configuration and do not block deterministic S02 work.
- Known-good behavior: S00's foundation remains intact. S01 provides provider-neutral backend identities, Apple token verification, injected native auth providers, Keychain sessions, coalesced refresh, restore/recovery/logout routing, and deterministic auth fakes. On 2026-08-31, the backend suite passed 147/147 tests on JDK 25 against PostgreSQL 16 with Flyway V1–V15, iOS repository/docs/secret + plist/shell/static checks passed, and PR #100's two Vercel checks passed.
- Verification commands: `cd ios && ./scripts/resolve-packages.sh && ./scripts/build.sh && ./scripts/test.sh && ./scripts/verify-repository.sh`; `cd backend && ./mvnw test`
- Exact next action: inventory the existing group API contracts and Android/PWA role behavior, then implement the injected S02 group repository and selected-group store before routing authenticated sessions into the native tab shell.

Do not start leaderboard, match, profile/stat, notification, or account-lifecycle work from later slices. Do not mark S02 `done` until S03's first transition commit after the S02 PR merges.
