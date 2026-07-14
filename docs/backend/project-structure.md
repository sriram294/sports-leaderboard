# Playboard — Backend Project Structure (Spring Boot)

Java + Spring Boot, Maven. **Layer-first, not feature-first**: `entity/`,
`repository/`, `service/`, `controller/`, and `dto/` are each their own
top-level package, with one subfolder per feature underneath — e.g.
`entity/group/Group.java`, `repository/group/GroupRepository.java`,
`service/group/GroupService.java`, `controller/group/GroupController.java`,
`dto/group/GroupDto.java`. This was a deliberate refactor away from an
earlier package-by-feature draft, applied consistently across every layer.

**Status: every endpoint in api-contracts.md is now built** (auth, users,
groups, matches, stats/leaderboard, and device registration) **and self-documented live** via
springdoc-openapi (`/v3/api-docs`, `/swagger-ui.html`). Remaining gaps are
smaller cross-cutting polish (Jackson config, a shared `CursorPage`
helper), not missing product functionality — see § Open questions.

## Directory layout

```
backend/
├── pom.xml
└── src
    ├── main
    │   ├── java/com/org/playboard/
    │   │   ├── PlayboardApplication.java
    │   │   │
    │   │   ├── config/             # cross-cutting Spring config, nothing feature-specific
    │   │   │   ├── SecurityConfig.java          (built — stateless JWT filter chain; permits only POST /auth/google, POST /auth/refresh, /avatars/**, and the OpenAPI/Swagger paths)
    │   │   │   ├── JwtAuthenticationFilter.java (built — reads Bearer <accessToken>, sets caller principal)
    │   │   │   ├── WebConfig.java               (built — serves uploaded avatars back out at /avatars/**)
    │   │   │   └── OpenApiConfig.java           (built — API metadata + bearer security scheme for /v3/api-docs, /swagger-ui.html)
    │   │   │
    │   │   ├── common/             # shared kernel — used across every layer
    │   │   │   ├── ApiExceptionHandler.java   (built — maps ApiException -> ProblemDetail + `code`)
    │   │   │   ├── ApiException.java          (built — base exception carrying an error `code`)
    │   │   │   ├── AvatarColorPicker.java     (built — stable colored-initial fallback color from a seed string; shared by user + group creation)
    │   │   │   ├── CursorPage.java            (not yet built — match list pagination currently hand-rolled in MatchService)
    │   │   │   └── Auditable.java             (createdAt/updatedAt @MappedSuperclass)
    │   │   │
    │   │   ├── entity/                        (all built)
    │   │   │   ├── sport/Sport.java                  (lookup table)
    │   │   │   ├── user/User.java
    │   │   │   ├── group/
    │   │   │   │   ├── Group.java                    (avatar_color added in V3 — see data-model.md)
    │   │   │   │   ├── GroupRole.java                (enum)
    │   │   │   │   ├── GroupRoleConverter.java        (autoApply AttributeConverter — no @Enumerated on the same field, see hibernate-jpa-mapping-gotchas)
    │   │   │   │   ├── GroupMember.java
    │   │   │   │   ├── MemberStatus.java
    │   │   │   │   ├── MemberStatusConverter.java
    │   │   │   │   └── GroupInvite.java
    │   │   │   ├── match/
    │   │   │   │   ├── Match.java
    │   │   │   │   ├── MatchTeam.java
    │   │   │   │   ├── MatchParticipant.java
    │   │   │   │   ├── MatchSet.java
    │   │   │   │   ├── MatchEvent.java               (`snapshot` JSONB column — see § Open questions)
    │   │   │   │   ├── MatchAction.java              (enum)
    │   │   │   │   └── MatchActionConverter.java
    │   │   │   ├── stats/
    │   │   │   │   ├── MemberStats.java              (composite key via @EmbeddedId/@MapsId; win_rate needs @Generated — see hibernate-jpa-mapping-gotchas)
    │   │   │   │   └── MemberStatsId.java
    │   │   │   ├── auth/RefreshToken.java            (id doubles as the refresh JWT's `jti`; see data-model.md)
    │   │   │   └── device/DeviceToken.java           (FCM registration token)
    │   │   │
    │   │   ├── repository/                    (all built)
    │   │   │   ├── sport/SportRepository.java
    │   │   │   ├── user/UserRepository.java
    │   │   │   ├── group/
    │   │   │   │   ├── GroupRepository.java
    │   │   │   │   ├── GroupMemberRepository.java
    │   │   │   │   └── GroupInviteRepository.java
    │   │   │   ├── match/
    │   │   │   │   ├── MatchRepository.java          (keyset pagination queries, not Pageable/OFFSET — see api-contracts.md)
    │   │   │   │   ├── MatchTeamRepository.java      (+ deleteByMatchId, for PATCH's full-replace)
    │   │   │   │   ├── MatchParticipantRepository.java (+ deleteByMatchId, + findPlayerMatchHistory / findRecentMatchesForPlayer / findPartnerHistory projections — feed stats/)
    │   │   │   │   ├── MatchSetRepository.java       (+ deleteByMatchId, + sumScoresByMatchIds projection)
    │   │   │   │   └── MatchEventRepository.java
    │   │   │   ├── stats/MemberStatsRepository.java
    │   │   │   ├── auth/RefreshTokenRepository.java
    │   │   │   └── device/DeviceTokenRepository.java
    │   │   │
    │   │   ├── service/                       (all built)
    │   │   │   ├── auth/
    │   │   │   │   ├── GoogleTokenVerifier.java     (wraps GoogleIdTokenVerifier)
    │   │   │   │   ├── JwtService.java              (issue/verify app access+refresh JWTs)
    │   │   │   │   └── AuthService.java             (orchestrates sign-in/refresh/logout; find-or-create user)
    │   │   │   ├── user/
    │   │   │   │   ├── UserService.java             (get/update profile, delegates photo bytes to AvatarStorageService)
    │   │   │   │   └── AvatarStorageService.java    (local-disk photo upload; see api-contracts.md)
    │   │   │   ├── group/
    │   │   │   │   ├── GroupService.java            (create/join/invite/roster; computes memberCount/matchCount on demand)
    │   │   │   │   └── GroupMembershipGuard.java    (shared "is caller an active member/role" check — plain method calls, not @PreAuthorize; see § Why this shape)
    │   │   │   ├── match/
    │   │   │   │   └── MatchService.java            (list/detail/create/edit/delete; cursor pagination; validation; calls service.stats in the same @Transactional; findRecentMatches() is a cross-service helper for StatsQueryService)
    │   │   │   ├── device/DeviceService.java          (FCM token register/unregister)
    │   │   │   ├── notification/                     (FCM send service + domain event listener)
    │   │   │   └── stats/
    │   │   │       ├── StatsRecalculationService.java  (write path — full rescan per affected player on every match write; see data-model.md § Recompute strategy)
    │   │   │       └── StatsQueryService.java          (read path — leaderboard ranking, player stats, on-demand Best Partner computation)
    │   │   │
    │   │   ├── controller/                    (all built)
    │   │   │   ├── auth/AuthController.java   (POST /auth/google, /auth/refresh, /auth/logout)
    │   │   │   ├── user/UserController.java   (GET/PATCH /users/me, POST /users/me/photo)
    │   │   │   ├── group/GroupController.java (GET/POST /groups, POST /groups/join, POST /groups/{id}/invites, GET /groups/{id}/members)
    │   │   │   ├── match/MatchController.java (GET/POST /groups/{id}/matches, GET/PATCH/DELETE .../matches/{id})
    │   │   │   ├── device/DeviceController.java (POST/DELETE /devices, POST /devices/test)
    │   │   │   └── stats/
    │   │   │       ├── LeaderboardController.java   (GET /groups/{id}/leaderboard)
    │   │   │       └── PlayerStatsController.java   (GET /groups/{id}/members/{userId}/stats)
    │   │   │
    │   │   └── dto/                           (all built)
    │   │       ├── auth/
    │   │       │   ├── GoogleSignInRequest.java
    │   │       │   ├── RefreshRequest.java
    │   │       │   └── TokenResponse.java
    │   │       ├── user/
    │   │       │   ├── UserSummaryDto.java   (embedded in TokenResponse.user)
    │   │       │   └── UserDto.java          (full GET/PATCH /users/me shape)
    │   │       ├── group/
    │   │       │   ├── GroupSummaryDto.java  (shared by GET /groups list items and create/join responses)
    │   │       │   ├── GroupListResponse.java
    │   │       │   ├── CreateGroupRequest.java
    │   │       │   ├── JoinGroupRequest.java
    │   │       │   ├── CreateInviteRequest.java
    │   │       │   ├── InviteResponse.java
    │   │       │   ├── MemberDto.java
    │   │       │   └── MembersResponse.java
    │   │       ├── match/
    │   │       │   ├── PlayerRefDto.java
    │   │       │   ├── TeamDto.java          (`isWinner` via @JsonProperty — Java field is `winner`)
    │   │       │   ├── SetDto.java
    │   │       │   ├── MatchSummaryDto.java  (reused by dto/stats/PlayerStatsDto.recentMatches)
    │   │       │   ├── MatchListResponse.java
    │   │       │   ├── RecordedByDto.java
    │   │       │   ├── MatchEventDto.java
    │   │       │   ├── MatchDetailDto.java
    │   │       │   └── RecordMatchRequest.java  (shared by POST create and PATCH full-replace)
    │   │       └── stats/
    │   │           ├── LeaderboardEntryDto.java
    │   │           ├── LeaderboardResponse.java
    │   │           ├── BestPartnerDto.java
    │   │           └── PlayerStatsDto.java
    │   │
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/             # Flyway — versioned schema changes
    │           ├── V1__init_schema.sql
    │           ├── V2__refresh_tokens.sql
    │           ├── V3__group_avatar_color.sql
    │           ├── V4__group_guests.sql
    │           ├── V5__shared_guest_fillers.sql
    │           └── V6__device_tokens.sql
    │
    └── test
        └── java/com/org/playboard/
            ├── SmokeVerificationTest.java              (live-DB: full entity/repository graph)
            └── service/
                ├── auth/AuthServiceIntegrationTest.java  (live-DB: JWT round trip, refresh rotation, revocation)
                ├── user/UserServiceIntegrationTest.java  (live-DB: profile update, avatar upload/replace, content-type rejection)
                ├── group/GroupServiceIntegrationTest.java (live-DB: create/invite/join/roster flow, role + membership permission checks, exhausted/invalid invite codes)
                ├── match/MatchServiceIntegrationTest.java (live-DB: record/edit/delete with hand-verified stats recompute — sums, streak reversal on edit, reversion on delete — permission checks, validation errors, cursor pagination)
                └── stats/StatsQueryServiceIntegrationTest.java (live-DB: leaderboard ordering + zero-match exclusion, player stats incl. Best Partner across multiple partners, zero-match player stats)
```

## Why this shape

- **Every layer is its own top-level package**, each with feature
  subfolders underneath. Adding a feature touches one subfolder per layer
  (`entity/X`, `repository/X`, `service/X`, `controller/X`, `dto/X`)
  instead of one combined package — the tradeoff accepted for this project
  is more packages per feature in exchange for each layer being a single,
  consistent place to look.
- **`common/` and `config/` stay thin.** Anything feature-specific belongs
  in its own layer/feature subfolder, not in a shared "utils" dumping
  ground. `AvatarColorPicker` earns its place there specifically because
  it's genuinely shared (user *and* group creation), not feature-specific.
- **`stats/` splits write and read concerns into two services** —
  `StatsRecalculationService` (called by `MatchService` on every write) and
  `StatsQueryService` (called by the leaderboard/player-stats controllers).
  Same feature subfolder, but keeping them as separate classes means the
  recompute algorithm and the read-side ranking/Best Partner logic can each
  be found, changed, and tested without touching the other.
- **`StatsRecalculationService` does a full rescan, not incremental sums**
  — see data-model.md § Recompute strategy for why this deviates from the
  original sketch. `MatchService` calls it with the union of old + new
  players inside the same `@Transactional` as the match write.
- **`StatsQueryService` depends on `MatchService`** (for
  `findRecentMatches`, backing `PlayerStatsDto.recentMatches`) rather than
  duplicating match-to-DTO assembly — `MatchService` stays the one place
  that knows how to turn a `Match` entity into `MatchSummaryDto`.
- **Best Partner is computed on demand**, per data-model.md — an ad-hoc
  JPQL join (`join MatchParticipant mp2 on mp2.matchTeam = mp.matchTeam`)
  groups a player's completed matches by teammate, since there's no
  materialized partner table and no mapped inverse association from
  `MatchTeam` back to its participants. "Best" = highest win rate together
  (min 1 game), tie-broken by games played together.
- **Flyway migrations mirror `data-model.md` directly** — that file is the
  design doc; `V1__init_schema.sql` is its literal executable form. Later
  schema changes become `V2__...`, `V3__...` rather than edits to V1.
- **Auth is stateless JWTs, not sessions.** `RefreshToken` rows exist only
  so a refresh token can be revoked/rotated server-side (its row id is the
  JWT's `jti`); access tokens are pure bearer JWTs verified in
  `JwtAuthenticationFilter` with no DB lookup on the hot path.
- **Avatar photos are served as plain static files, not through a
  controller.** `WebConfig` maps `/avatars/**` straight to the storage
  directory and `SecurityConfig` permits it unauthenticated — Coil (the
  Android image loader) never needs to attach a bearer token just to draw a
  profile picture.
- **`GroupMembershipGuard` is a plain `@Component` with method calls**
  (`requireActiveMember`, `requireRole`), not `@PreAuthorize` SpEL. The rule
  needs a DB lookup keyed on two values (path `groupId` + JWT-derived caller
  id) — a normal Java method reads more clearly than a SpEL expression
  calling into a bean, and is trivial to hit directly in an integration
  test without standing up Spring Security's method-security machinery.
- **Match edit/delete permission is recorder-OR-owner/admin** — both `PATCH`
  and `DELETE` raise `MATCH_EDIT_FORBIDDEN` otherwise.
- **The leaderboard excludes members with zero matches played** — a
  reasonable reading of api-contracts.md that wasn't explicit; a "ranking"
  of players who haven't played is noise, not signal.
- **Cursor pagination is a plain base64 `playedAt|id` string** built and
  parsed inline in `MatchService`, not a shared `CursorPage` helper yet —
  there's only one paginated endpoint so far; worth extracting to
  `common/CursorPage.java` if a second one (e.g. a future notifications
  feed) needs the same shape.
- **`SecurityConfig` permits exactly `POST /auth/google` and
  `POST /auth/refresh`, not a `/auth/**` wildcard.** Adding springdoc
  surfaced that the original wildcard also permitted `POST /auth/logout`
  unauthenticated, contradicting api-contracts.md's own stated rule
  ("every endpoint except `POST /auth/google` and `POST /auth/refresh`
  requires `Authorization: Bearer`"). Fixed and verified live: `/auth/
  logout` now 403s without a token, `/auth/google` and `/auth/refresh`
  still don't need one.
- **`OpenApiConfig` sets one global bearer security requirement**, and
  `AuthController.signInWithGoogle`/`.refresh` carry an empty
  `@SecurityRequirements` to opt back out — so Swagger UI's padlocks match
  reality instead of showing every endpoint (including the two public
  ones) as requiring a token.

## Key dependencies (Maven)

| Concern | Dependency |
|---|---|
| Web/REST | `spring-boot-starter-web` |
| Data access | `spring-boot-starter-data-jpa` |
| DB migrations | `spring-boot-starter-flyway` (+ `flyway-database-postgresql`) — Spring Boot 4.x requires the dedicated starter, not bare `flyway-core`, for autoconfiguration to actually run migrations |
| Validation | `spring-boot-starter-validation` |
| Security (JWT filter, not full OAuth client) | `spring-boot-starter-security` — excludes `UserDetailsServiceAutoConfiguration` (see `spring-boot-security-autoconfig-gotcha`), since auth is pure JWT with no in-memory/DB `UserDetailsService` |
| Google ID token verification | `com.google.api-client:google-api-client` (2.9.0) |
| JWT issuing/verification | `io.jsonwebtoken:jjwt-api` / `jjwt-impl` / `jjwt-jackson` (0.13.0) |
| Live API contract for Android | `org.springdoc:springdoc-openapi-starter-webmvc-ui` (3.0.3) — targets Spring Boot 4.x; the 2.x line is for Boot 3.x, don't downgrade to it |
| DB driver | `org.postgresql:postgresql` |

No Lombok. Entities (`User`, `Group`, `Match`, …) are plain Java classes
with hand-written getters/setters/constructors — JPA needs a mutable,
no-arg-constructor shape anyway, so there's little Lombok would buy there.
DTOs (`UserDto`, `MatchSummaryDto`, …) are **Java `record`s** instead —
they're immutable data carriers by nature, and records give constructor +
accessors + `equals`/`hashCode`/`toString` for free without an annotation
processor. Enum-typed DTO fields (`role`, `myRole`) are serialized as
plain lowercase `String`s built at the DTO factory method (`.name().
toLowerCase()`), matching api-contracts.md's `"owner"`/`"member"` casing,
rather than a global Jackson enum module. `TeamDto.isWinner` is the one
exception needing `@JsonProperty` — a record component can't be named
`isWinner` and read naturally as Java (`team.winner()`), so the JSON name
is pinned explicitly instead.

## Open questions

- Object storage for avatar photos is **resolved for now**: local disk
  under `playboard.storage.avatar-dir` (default `./data/avatars`), served
  via `WebConfig`. `AvatarStorageService` is the single seam to swap for an
  S3-compatible bucket later — nothing else depends on how/where the bytes
  live.
- `GroupMembershipGuard` mechanism is **resolved**: plain method-level
  checks (see § Why this shape), not `@PreAuthorize`.
- Group avatar/branding is **resolved for now**: same colored-initial
  fallback as users, generated from the group name at creation
  (`AvatarColorPicker`). No custom group icon upload yet — would need its
  own storage path if added later.
- Match edit/delete permission rule is **resolved**: recorder OR
  owner/admin (see § Why this shape).
- Best Partner ranking metric is **resolved**: highest win rate together
  (min 1 game), tie-broken by games played together — not explicitly
  specified in api-contracts.md, so this is the interpretation implemented.
- `MatchEvent.snapshot` (the JSONB "full match state at this point"
  column) is **not yet populated** — `MatchService` leaves it `null` on
  every event since nothing reads it back yet. Revisit if/when a
  diff/undo view is built for the Matches history panel.
- No Testcontainers yet — every integration test requires a live Postgres
  reachable via `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`, so none of them run
  standalone in CI yet. Worth adding once a CI pipeline exists.
- `springdoc-openapi` is **resolved**: added, live at `/v3/api-docs` and
  `/swagger-ui.html`, verified against a running instance (all 18
  operations present, bearer security scheme correct per-operation). It's
  now the authoritative live contract — api-contracts.md stays as the
  human-readable design doc, but if the two ever disagree, trust the live
  spec.
