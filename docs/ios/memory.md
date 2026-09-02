# iOS working memory

- Active slice: S06 — Notifications and app updates
- Active branch: `feature/ios-notifications`
- Base: refreshed `origin/master` at `317dbff99d45234d3417b92ecc17407858cacf3e` after S05 PR [#106](https://github.com/sriram294/sports-leaderboard/pull/106) merged; S03 previously merged as PR [#102](https://github.com/sriram294/sports-leaderboard/pull/102) at `57cd90fe6a0fd794720a11a0a874c66e211e81af`
- Current state: S04 is merged and closed by PR #105 (merge SHA unavailable in this workspace). S05 is merged and closed by PR #106 at `317dbff99d45234d3417b92ecc17407858cacf3e`. S06 is now active for notification permission/token lifecycle, push routing, device registration, and iOS update prompts.
- Blockers: Xcode 26.2 and `swiftc` are unavailable in this Linux workspace, so new Swift sources have not yet been compiled or executed here. The backend integration suite cannot connect to PostgreSQL in this workspace; `MatchServiceIntegrationTest` therefore stops during application-context startup before exercising V16. The generalized Codemagic workflow should provide macOS evidence on the S04 PR. S02's backend self-leave debt remains outside S04 scope.
- Known-good behavior: S00–S03 remain merged. For S04, `cd backend && ./mvnw -q -DskipTests compile` passes on JDK 25, `cd ios && ./scripts/verify-repository.sh` passes, and `git diff --check` is clean. The attempted targeted backend suite reached Spring startup but failed only because PostgreSQL was unavailable; the attempted iOS resolve/build/test commands stop at the recorded missing-`xcodebuild` blocker. No successful S04 Xcode or database-backed test artifact has been reported.
- Verification commands: `cd ios && ./scripts/resolve-packages.sh && ./scripts/build.sh && ./scripts/test.sh && ./scripts/verify-repository.sh`; `cd backend && ./mvnw test`
- Exact next action: inspect existing backend device-registration/update contracts, then implement S06 notification adapters, push routing, update repository/API contract, tests, and UI scenarios; run the iOS/backend verification commands with Xcode 26.2 and record `.xcresult`, JUnit, payload fixtures, and screenshot artifact paths.

Do not start account-lifecycle work from later slices. Do not mark S06 `done` until S07's first transition commit after the S06 PR merges.
