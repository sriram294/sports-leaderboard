# iOS working memory

- Active slice: S05 — Profile, statistics, settings, and sharing
- Active branch: `feature/ios-profile-stats`
- Base: refreshed `origin/master` at `33c3d7a27c1f4fc4b4441c8f9540035116e7add4` after Android publishing PR [#103](https://github.com/sriram294/sports-leaderboard/pull/103); S03 previously merged as PR [#102](https://github.com/sriram294/sports-leaderboard/pull/102) at `57cd90fe6a0fd794720a11a0a874c66e211e81af`
- Current state: S04 is merged and closed by PR #105 (merge SHA unavailable in this workspace). S05 now has profile/stats DTOs and an authenticated repository, group-scoped Profile and Insights screens, independent partner expansion/loading, editable display name, and deterministic preview data. Settings appearance persistence, photo upload, native sharing, and full test coverage remain to be implemented.
- Blockers: Xcode 26.2 and `swiftc` are unavailable in this Linux workspace, so new Swift sources have not yet been compiled or executed here. The backend integration suite cannot connect to PostgreSQL in this workspace; `MatchServiceIntegrationTest` therefore stops during application-context startup before exercising V16. The generalized Codemagic workflow should provide macOS evidence on the S04 PR. S02's backend self-leave debt remains outside S04 scope.
- Known-good behavior: S00–S03 remain merged. For S04, `cd backend && ./mvnw -q -DskipTests compile` passes on JDK 25, `cd ios && ./scripts/verify-repository.sh` passes, and `git diff --check` is clean. The attempted targeted backend suite reached Spring startup but failed only because PostgreSQL was unavailable; the attempted iOS resolve/build/test commands stop at the recorded missing-`xcodebuild` blocker. No successful S04 Xcode or database-backed test artifact has been reported.
- Verification commands: `cd ios && ./scripts/resolve-packages.sh && ./scripts/build.sh && ./scripts/test.sh && ./scripts/verify-repository.sh`; `cd backend && ./mvnw test`
- Exact next action: finish S05 settings appearance, photo update, sharing renderer/share sheet, tests, and UI scenarios; then run the iOS verification commands with Xcode 26.2 and record `.xcresult`, JUnit, and screenshot artifact paths.

Do not start notification or account-lifecycle work from later slices. Do not mark S05 `done` until S06's first transition commit after the S05 PR merges.
