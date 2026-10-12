# Implementation Plan: Scalable Read Models and Bounded Background Work

## Execution order

Deliver this in four independently deployable slices. Each slice preserves the existing API contract and has a rollback flag or safe fallback.

## Slice 1: bounded, observable notification delivery

1. Add `spring-boot-starter-actuator` and Micrometer dependencies in `backend/pom.xml`.
2. Replace the marker-only `AsyncConfig` with a named `notificationExecutor` in `backend/src/main/java/com/org/playboard/config/AsyncConfig.java`.
   - Bind core size, max size, queue capacity, and keep-alive from `playboard.notifications.executor` properties.
   - Use `ThreadPoolTaskExecutor`, `setThreadNamePrefix("notification-")`, `waitForTasksToCompleteOnShutdown(false)`, and `DiscardOldestPolicy`.
   - Wrap the rejection handler to increment a Micrometer counter and emit a structured warning containing the task type.
3. Change every `NotificationEventListener` method to `@Async("notificationExecutor")`.
4. Introduce a small `NotificationMetrics` component and record submitted, failed, rejected, and discarded work. Export executor gauges through Micrometer.
5. Configure Firebase HTTP transport timeouts in `FirebaseConfig` or the client factory used to initialize `FirebaseMessaging`; retain `PushNotificationService`'s exception swallowing.
6. Add unit tests for executor configuration, listener qualifier use, task rejection/discard metrics, and the invariant that notification failure never rolls back a match write.

Validation: `./mvnw test` with PostgreSQL; Actuator metrics include the executor gauges and notification counters.

## Slice 2: versioning and bounded projection rebuilds

1. Add a Flyway migration to add `groups.leaderboard_version bigint not null default 0` and a `group_period_projection` table keyed by `(group_id, period_start, period_end, algorithm_version)`.
   - Store projection status, source version, serialized standings/rating state, timestamps, and the last failure message.
   - Add an index for `(group_id, period_start, period_end, status)`.
2. Add entities/repositories for projection and version access. Use an atomic SQL update (`leaderboard_version = leaderboard_version + 1 returning leaderboard_version`) or a pessimistic group-row lock; do not read-modify-write in Java.
3. Extract match score/roster delta calculation from `StatsRecalculationService` into a service that can apply the signed effects of one recorded match to each affected `MemberStats` row.
4. On create, apply additive totals synchronously. Keep the legacy full recomputation behind `playboard.projections.incremental-writes-enabled=false` until parity tests pass.
5. On edit/delete, increment the group version, mark each old/new played-month projection rebuilding, and submit a deduplicated group/month rebuild task to a separate bounded `projectionExecutor`.
6. Add a scheduled reconciler that selects rebuilding/failed projections with `SKIP LOCKED`, rebuilds one group/month at a time, and records failures without blocking writes.
7. Add integration tests that compare incremental create results with the current full-rescan results, verify edit/delete only schedule the affected periods, and test concurrent version increments.

Validation: the create path has a bounded query count; edits/deletes return without querying complete player history; projection reconciliation restores exact standings.

## Slice 3: PostgreSQL/Redis leaderboard snapshots and conditional HTTP

1. Add Spring Data Redis/Lettuce dependencies and `playboard.redis` configuration. Create a Docker/local-test Redis profile and health indicator.
2. Implement `LeaderboardSnapshotCache` with keys `leaderboard:{groupId}:{period}:{algorithmVersion}:{version}` and a configurable TTL. Serialize `LeaderboardResponse` with the existing JSON mapper.
3. Refactor `StatsQueryService.getLeaderboard` to:
   - obtain the current group version;
   - read Redis first;
   - on miss, read a `ready` PostgreSQL projection; and
   - fall back to the current ranker only while the projection feature flag is disabled or a controlled backfill is running.
4. Add an after-commit `LeaderboardInvalidatedEvent` that evicts only the obsolete group/period keys. Versioned keys remain correct even if eviction is delayed or fails.
5. Update `LeaderboardController` to return an ETag based on group, period, algorithm, and version; honor `If-None-Match` with `304` for unchanged results. Do not cache authenticated mutations.
6. Add tests for Redis hit/miss, Redis exception fallback, stale-version isolation, ETag/304 behavior, and PostgreSQL projection fallback while rebuilding.
7. Ship with `playboard.projections.reads-enabled=false`, run a backfill/reconciliation command, compare responses in shadow mode, then enable reads.

Validation: normal monthly leaderboard reads perform no `matches` replay; Redis loss preserves successful PostgreSQL responses; unchanged conditional requests return 304.

## Slice 4: client caching and repository hygiene

1. In `pwa/src/main.tsx`, configure TanStack Query defaults for retry and garbage collection. In `pwa/src/queries.ts`, assign endpoint-specific stale times:
   - leaderboard/matches: 30 seconds;
   - members/profile/stats: 5 minutes;
   - trophy/month history: 1 hour.
   Existing mutation invalidation remains immediate.
2. Add API response ETag support to `pwa/src/data.ts`, preserving the last body per query and sending `If-None-Match` for cacheable GETs only if React Query asks for revalidation.
3. Replace `URLSession.shared` in `ios/Playboard/Core/API/APIClient.swift` with an injected configured session: request timeout, resource timeout, memory/disk `URLCache`, and protocol cache policy. Inject deterministic sessions in tests.
4. Add repository-scoped cache eviction after successful iOS match/group/profile mutations; never cache auth/token endpoints.
5. Extend root `.gitignore` to ignore Android release outputs/baseline profile output and `backend/data/`. Confirm whether `android/app/playboard-cert.pem` is an intentional public pin; if not, remove it from the worktree and keep it ignored. Do not add user-created release artifacts to the commit.
6. Add PWA Vitest coverage for stale/invalidation behavior and iOS tests for request cache policy and mutation eviction.

Validation: a mounted unchanged screen does not refetch before its stale time; mutations immediately refresh affected data; iOS uses the injected cache policy; `git status` excludes generated artifacts.

## Release and rollback

- Deploy slices 1 and 4 independently.
- Deploy slice 2 with incremental writes disabled, backfill and compare projections, then enable it per environment.
- Deploy slice 3 with cache reads disabled, observe shadow comparisons, then enable cache reads. Redis errors automatically fall back to PostgreSQL.
- Roll back via feature flags to full recompute/live ranking; migrations remain append-only.

## Required evidence before merge

- Backend unit/integration suite with PostgreSQL and Redis profiles passes.
- Android, PWA, and iOS existing checks remain green.
- Load test demonstrates current leaderboard query count and p95 latency do not increase with historical match count.
- Dashboards show notification rejection/failure behavior under forced saturation and no match-write failures.
