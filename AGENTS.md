# Repository Guidelines

## Project Structure & Module Organization

`android/` contains the Kotlin/Jetpack Compose client (`com.org.playboard`). Keep feature UI in `ui/<feature>/`, with a `Screen`, `ViewModel`, and immutable `UiState`; place reusable controls in `ui/components/`. Networking, repositories, and domain models live under `data/`; Hilt modules are in `di/`.

`backend/` is the Java Spring Boot REST API. Follow its `controller/`, `service/`, `dto/`, `entity/`, and `repository/` layers. Database changes are append-only Flyway migrations in `backend/src/main/resources/db/migration/` (for example, `V7__add_feature.sql`). Requirements, API contracts, data model notes, and visual references are under `docs/`.

`ios/` contains the Swift 6/SwiftUI client. Keep app composition in `Playboard/App/`, shared infrastructure in `Playboard/Core/`, and later feature MVVM/Repository groupings in `Playboard/Features/<feature>/`. The authoritative iOS delivery state is `docs/ios/roadmap.md`; read `docs/ios/memory.md` before resuming iOS work.

## Build, Test, and Development Commands

Run Android commands from `android/`:

```bash
./gradlew :app:testDebugUnitTest  # JVM unit tests
./gradlew :app:assembleDebug      # build the debug APK
./gradlew :app:installDebug       # install on a connected device/emulator
```

Run backend commands from `backend/` (JDK 25):

```bash
./mvnw -q compile                 # compile the API
./mvnw test                       # run Spring tests
./mvnw spring-boot:run            # start locally
```

Backend integration tests require PostgreSQL plus `GOOGLE_CLIENT_ID` and a 32-byte-or-longer `JWT_SECRET`; configuration defaults are in `application.yml`.

Run iOS commands from `ios/` on the pinned Xcode version:

```bash
./scripts/resolve-packages.sh
./scripts/build.sh
./scripts/test.sh
./scripts/archive-unsigned.sh
```

## Coding Style & Architecture

Use Kotlin, Jetpack Compose, and Material 3 for Android work. Preserve MVVM plus Repository boundaries: composables render state and emit events; ViewModels own UI logic; repositories own data access. Use one screen package per feature, immutable state, PascalCase types, camelCase members, and KDoc on public classes/functions. Keep backend responsibilities in their existing layers and use descriptive DTO and migration names. No formatter or linter is configured; match nearby code and standard IDE formatting.

Use Swift 6, SwiftUI, and native MVVM/Repository boundaries for iOS. Production dependencies are composed once in `AppEnvironment`; previews and tests use initializer-injected deterministic implementations. Do not add a dependency-injection framework or project generator.

## Testing Guidelines

Add focused JUnit tests alongside changed code: Android tests use `*Test.kt` under `android/app/src/test/`; backend tests use `*Test.java` under `backend/src/test/`. Mock network boundaries and use the shared Android test repository helper where appropriate. Run the affected module's suite before opening a PR.

## Commits & Pull Requests

Recent commits use concise, imperative subjects such as `Share the leaderboard as an image` and `Fix FCM ...`. Keep each commit scoped to one change. PRs should explain user-facing behavior, link the requirement/issue when available, include screenshots for UI work, and call out migrations or required environment changes. Target `master`; do not self-merge.

For iOS, only one roadmap slice may be `in_progress`. During a slice, its active feature branch is authoritative. The next slice's first transition commit records merge evidence, marks the previous slice `done`, and starts the next slice; after S08, use a documentation-only closure PR.
