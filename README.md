# Playboard

Playboard is an Android app for private badminton doubles groups. Record matches, track a live leaderboard, review match history, compare player stats, and share a leaderboard card. The app pairs a Kotlin/Jetpack Compose client with a Java/Spring Boot REST API and PostgreSQL.

## Repository layout

- `android/` — Android client: Compose UI, Hilt, Retrofit, DataStore, and Firebase Cloud Messaging.
- `backend/` — Spring Boot API, JPA entities, Flyway migrations, and PostgreSQL integration tests.
- `docs/requirements/` — implemented product behavior by screen.
- `docs/backend/` — human-readable API contract, schema, and backend architecture.
- `docs/prototype/` — UI reference PDFs.

## Run locally

Requirements: Android Studio with an Android SDK, JDK 25 for the backend, and PostgreSQL.

```bash
# Android (from android/)
./gradlew :app:assembleDebug
./gradlew :app:installDebug

# Backend (from backend/)
export GOOGLE_CLIENT_ID="your-web-oauth-client-id"
export JWT_SECRET="at-least-32-bytes-of-random-secret"
./mvnw spring-boot:run
```

The backend defaults to `jdbc:postgresql://localhost:5432/playboard` with the
`playboard` username and password. Override `DB_URL`, `DB_USERNAME`, and
`DB_PASSWORD` as needed. Flyway applies migrations on startup. Android's API
base URL is defined in `android/app/build.gradle.kts`.

### Sideloaded debug updates

The public `GET /api/v1/app/update` endpoint advertises the latest debug APK.
Set `PLAYBOARD_UPDATE_DEBUG_VERSION_CODE`, `PLAYBOARD_UPDATE_DEBUG_VERSION_NAME`,
and `PLAYBOARD_UPDATE_DEBUG_DOWNLOAD_URL` on the backend after publishing the
APK as an HTTPS GitHub Release asset. Increment `versionCode` for every build
and keep the same debug signing key; otherwise Android will reject the upgrade.

## Verify changes

```bash
# Android unit tests
(cd android && ./gradlew :app:testDebugUnitTest)

# Backend tests; require PostgreSQL and the environment variables above
(cd backend && ./mvnw test)
```

The live API exposes OpenAPI at `/v3/api-docs` and Swagger UI at
`/swagger-ui.html`. See [AGENTS.md](AGENTS.md) for contributor conventions and
the `docs/` directory before changing a feature or API contract.
