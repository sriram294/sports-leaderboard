# Scalable Read Models and Bounded Background Work

## Goal

Keep match writes and current leaderboard reads bounded as each group's match history grows without limit. PostgreSQL remains authoritative; Redis accelerates versioned, read-only leaderboard responses. Push delivery remains deliberately best-effort.

## Scope

- Bound notification concurrency, queueing, FCM call time, and overload behavior.
- Replace full-history work on normal match writes and leaderboard reads with maintained projections.
- Cache current leaderboard responses in Redis and validate them with ETags.
- Add intentional PWA and iOS client cache policies.
- Prevent generated release artifacts and local runtime data from entering Git.

This does not introduce a durable notification outbox, cross-region replication, or a general event bus.

## Success criteria

For a group with arbitrarily long match history, normal current-month leaderboard reads do not scan or replay historical match rows. New-match writes touch only the match, its participants, and their projection rows. A rebuild after an edit or delete may be asynchronous and bounded to one group/month.

- API latency target: p50 under 150 ms, p95 under 400 ms, p99 under 1,000 ms for cached/read-model leaderboard requests.
- Client target on mobile 4G: LCP under 2.5 s, INP under 200 ms, CLS under 0.1.
- Availability target: 99.9% monthly API availability; a Redis outage degrades to PostgreSQL projections rather than preventing reads.

## Data model

`groups` gains a monotonic `leaderboard_version` integer. It increments for every match or membership mutation that changes visible standings.

Two PostgreSQL projections own derived state:

1. `member_stats` remains the all-time aggregate per group/member. A new match applies a signed delta for matches, wins, losses, points, and streak-relevant tail state.
2. `group_period_leaderboard` stores a completed snapshot for one `(group_id, period_start, period_end, algorithm_version)`, including the source version and rebuilding state. Team-v2 rating state is persisted at this period boundary instead of reconstructed from the full match table on every read.

Projection state has `ready`, `rebuilding`, and `failed` values. The last `ready` snapshot remains readable while a replacement rebuild is running. Projection rows are unique per group, period, and algorithm version.

## Write path

New match writes run in one PostgreSQL transaction:

1. Persist the match, teams, participants, and sets.
2. Apply additive deltas to the affected `member_stats` rows and current-period rating state.
3. Increment `groups.leaderboard_version` with optimistic or row-level locking.
4. Record which current period requires snapshot refresh.
5. Publish an after-commit invalidation event.

Edits and deletes calculate their old/new affected periods. They increment the same version, mark those period projections `rebuilding`, and submit one deduplicated group/period rebuild task. The request does not synchronously scan old matches. If the bounded rebuild executor is saturated, the projection stays `rebuilding` and a scheduled reconciliation picks it up later.

## Read path and Redis

The current leaderboard lookup uses the group's visible version and period to form `leaderboard:{groupId}:{period}:{algorithmVersion}:{version}`. The cached representation has a short TTL and includes an ETag derived from those dimensions.

On a hit, the service returns the cached serialized response. On a miss, it reads the ready PostgreSQL projection, returns it, and populates Redis. Conditional requests with a matching `If-None-Match` return `304 Not Modified`.

Redis is never the source of truth. On Redis timeout/error, the service reads PostgreSQL directly, emits a metric, and still serves the response. Versioned keys avoid stale overwrites; TTL is only memory cleanup, not invalidation correctness.

## Notification executor

`notificationExecutor` is a named `ThreadPoolTaskExecutor` used explicitly by notification listeners. It has a fixed, configurable core/max pool, bounded queue, FCM connection/read/call timeouts, a descriptive thread prefix, and `DiscardOldestPolicy`.

Rejected or discarded work is logged with event type and group ID and increments a counter. Listener failures increment a failure counter. Notification loss under sustained overload is accepted; match/group mutations are not retried or rolled back because push delivery is best-effort.

Micrometer metrics expose executor active count, pool size, queue depth, completed tasks, failures, rejections, and discards. Alerts trigger on sustained queue occupancy or rejected tasks.

## Client cache behavior

PWA TanStack Query defaults use endpoint-specific stale times: short for leaderboard/matches, medium for member/profile data, and long for stable assets. Existing group-scoped invalidation after mutations remains authoritative and forces revalidation immediately.

iOS creates a configured `URLSession` with bounded request/resource timeouts and a shared memory/disk `URLCache`. GET requests use protocol cache policy; repositories evict or bypass affected cached reads after successful mutations. Authentication requests are not cached.

## Repository policy

Git ignores Android release artifacts, baseline-profile output, backend local avatar data, and other generated runtime files. Signing material remains ignored. A PEM may only be committed when documented as a public, intentionally pinned certificate; otherwise it stays outside the repository. CI uploads release artifacts and mapping files using retention policy rather than committing them.

## Rollout and verification

1. Add migrations and projections while retaining current read behavior behind a feature flag.
2. Backfill projections for existing groups, verify output against the current ranker, then enable read-model/Redis reads.
3. Enable incremental write updates, then migrate edit/delete rebuilds to the bounded executor.
4. Remove legacy live replay only after reconciliation has produced no mismatches for a complete month.

Tests cover signed projection deltas, edit/delete rebuild scheduling and deduplication, version/ETag behavior, Redis fallback, executor saturation/discard metrics, PWA invalidation, and iOS cache invalidation. Load tests demonstrate constant query counts for current leaderboard reads as historical match count increases.
