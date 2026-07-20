# Playboard — Claude guide

Playboard is a **badminton doubles match tracker** for private play groups: members log
match results and the app maintains a live leaderboard, per-player stats, and group
insights. It's an Android app backed by a custom REST API.

## Repository layout
- `android/` — Kotlin + Jetpack Compose app (package `com.org.playboard`).
- `backend/` — Java + Spring Boot + Postgres API (package `com.org.playboard`), deployed
  on **Railway from `master`** at `https://playboard-prd.cooperbcknd.in`.
- `docs/` — the source of truth:
  - `docs/requirements/00–06-*.md` — per-screen specs (Login, Board, Matches, Add, Profile, Stats).
  - `docs/backend/api-contracts.md` — REST contract; `docs/backend/data-model.md` — schema.
  - `docs/prototype/*.pdf` — visual reference (the Read tool renders PDFs).
- `PROJECT_RULES.md` — coding conventions (summarized below).

## Build / test / run
**Android** (`cd android`):
- Unit tests: `./gradlew :app:testDebugUnitTest`
- Build APK: `./gradlew :app:assembleDebug`
- Install to a connected device: `./gradlew :app:installDebug`

**Backend** (`cd backend`, JDK 25, Spring Boot):
- Compile: `./mvnw -q compile` (add `-o` to force offline).
- Integration tests are `@SpringBootTest` and need a live Postgres + auth env vars — see
  the "Backend integration test setup" project memory for the exact command (a dockerized
  Postgres runs on host port `55432`; use a fresh DB, and pass `DB_URL`/`DB_USERNAME`/
  `DB_PASSWORD` + non-blank `GOOGLE_CLIENT_ID` + a ≥32-byte `JWT_SECRET`).

## Architecture & conventions (`PROJECT_RULES.md`)
- Kotlin + Compose, **Material 3**, dark theme only (`BrandLime` accents; see `ui/theme/Color.kt`).
- **Screen-level horizontal padding is `10.dp`** — every page's root container uses this
  gutter so content aligns with the shared header. New screens must follow it. Padding
  inside cards/rows/buttons is independent.
- **MVVM + Repository**, Hilt DI. One screen per package; reusable UI in `ui/components`.
  Immutable UI state; no business logic in composables. KDoc public classes/functions.
- Android structure: `data/` (`remote/dto` `@Serializable` DTOs → `PlayboardApi` (Retrofit)
  → repositories → `data/model` domain types), `ui/<feature>/` (`<Feature>UiState.kt` +
  `<Feature>ViewModel.kt` (`@HiltViewModel`) + `<Feature>Screen.kt`, previewable), `di/`.
  The active group is app-wide state in `GroupRepository.selectedGroup`; screens observe it
  plus `dataRevision` (bumped on match / foreground / pull-to-refresh) to reload.
- Backend structure: `controller/ service/ dto/ entity/ repository/`, Flyway migrations in
  `src/main/resources/db/migration`. `member_stats` is recomputed transactionally on every
  match write (`StatsRecalculationService`); leaderboard/stats read from it.

## Feature workflow (per slice)
Build one requirement slice at a time: read `docs/requirements/0X-*.md` + the matching
prototype PDF → branch off `master` → implement (data → ViewModel/UiState → Compose screen)
→ add unit tests → `:app:testDebugUnitTest` + `:app:assembleDebug` → open a PR **against
`master`**. **Bump `versionCode` + `appVersionName` in `android/app/build.gradle.kts`
whenever the PR touches `android/`** (including the Android half of a full-stack PR) —
that version is what ships an APK and drives the in-app update prompt. Backend-only or
docs-only PRs leave it alone; bumping there would cut a release with no app change in it. **Never self-merge** — the user reviews/merges; Railway then redeploys the
backend. Full-stack features go in a single PR (backend + Android). Confirm a Railway
deploy landed by reading the live schema:
`curl -s .../v3/api-docs | jq '.components.schemas.<Dto>.properties | keys'`.

## Gotchas (bite every session)
- **Recurring tamper:** the IDE/env repeatedly re-injects broken edits into
  `android/app/build.gradle.kts` (a bad `archivesBaseName`/`applicationVariants` snippet) and
  sometimes `ui/board/BoardScreen.kt` (WIP podium-medal code with missing imports). Both
  break the build. Revert them **atomically in the same command** as the gradle invocation:
  `git checkout -- app/build.gradle.kts app/src/main/java/com/org/playboard/ui/board/BoardScreen.kt && ./gradlew …`
- **Untracked new files:** newly created files are easy to leave untracked (has bitten
  app icons, the Stats tab, and the shared test helper). Run `git status` for `??` before
  committing; stage new packages explicitly.
- **Unit-test doubles:** ViewModel tests build `GroupRepository` via the shared
  `app/src/test/java/com/org/playboard/testing/TestGroupRepository.kt` helper (in-memory
  `SelectedGroupStore` + unconfined scope). Growing `PlayboardApi` breaks every
  `FakePlayboardApi` double — each needs the new `override`.
- **On-device match recording** needs **4 distinct players** (doubles, teamSize 2). A group
  seeds 3 guests, so 2 real members + 2 guests is enough. Guests never appear on the
  leaderboard or in stats.
