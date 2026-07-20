# Playboard — Backend Data Model & Schema

Target: PostgreSQL (relational, ACID transactions for match writes, generated
columns for sortable leaderboard fields, JSONB for audit snapshots). Feeds
the custom REST API the Android app talks to (see
[../requirements/00-overview.md](../requirements/00-overview.md) for the
product requirements this schema serves).

## Design goals

1. **Efficient reads on the hot path.** Board (leaderboard) is the app's
   home screen and gets hit constantly — its stats are **materialized**
   (`member_stats`), not recomputed from raw matches on every request.
2. **Correct writes under edit/delete.** Matches can be edited or deleted
   after the fact ([03-matches.md](../requirements/03-matches.md)), so the
   write path recomputes affected stats rather than blindly incrementing
   counters forever.
3. **Modular: new features shouldn't require schema migrations.**
   - New sport (singles, another racket sport) → new `sports` row, no
     table changes.
   - New per-sport scoring rule → columns on `sports`, not hardcoded app
     logic.
   - New stat on the leaderboard → new column on `member_stats`, computed
     alongside the existing ones in the same recompute path.
4. **Soft delete + audit trail everywhere it matters**, since Matches
   requires a visible History log and Edit/Delete.

## Entity overview

| Table | Purpose |
|---|---|
| `users` | Account identity, avatar |
| `sports` | Lookup: sport/format rules (team size, scoring) |
| `groups` | A play group, tied to one sport |
| `group_members` | Membership + role, many-to-many users↔groups |
| `group_invites` | Join-by-code, backs "Create or join a group" |
| `matches` | One recorded match, soft-deletable |
| `match_teams` | The 2 teams in a match |
| `match_participants` | Players on a team (1 for singles, 2 for doubles, …) |
| `match_sets` | Per-set scores |
| `match_events` | Audit log: created / edited / deleted, by whom, when |
| `member_stats` | Materialized per-group-per-player stats (leaderboard source) |
| `refresh_tokens` | Server-side record backing refresh-token rotation/revocation |
| `device_tokens` | FCM registration tokens per user's device (push notifications) |

`member_stats` is the only denormalized/derived table. Everything else is
normalized; `member_stats` is rebuilt from `matches`/`match_teams`/
`match_participants`/`match_sets` whenever those change (see
[Recompute strategy](#recompute-strategy)).

`member_stats` is an **all-time** snapshot (keyed only by `(group_id, user_id)`,
no time dimension), so it backs only the all-time leaderboard. The **windowed**
leaderboard ("This Week" / "This Month") can't read it — it aggregates the raw
`matches`/`match_teams`/`match_participants`/`match_sets` on demand, filtered by
`matches.played_at ∈ [from, to)` (single set-based query, keyed off the existing
`idx_matches_group_played` index). Same PF/PA-by-team and win logic as the
per-player recompute, but for the whole group at once and bounded by the window.

## Schema (DDL)

```sql
create extension if not exists "pgcrypto"; -- gen_random_uuid()

-- ─────────────────────────────────────────────────────────────────────────
-- Identity
-- ─────────────────────────────────────────────────────────────────────────

create table users (
    id            uuid primary key default gen_random_uuid(),
    google_sub    text unique,               -- nullable: room for other auth providers later
    email         text not null unique,
    display_name  text not null,
    photo_url     text,                      -- host-free path ("/avatars/<id>.jpg"); PUBLIC_BASE_URL is
                                             -- prepended at read time by AvatarUrlResolver so the API can
                                             -- change domain without a data migration.
                                             -- null => client falls back to initial + avatar_color
    avatar_color  text not null,              -- persisted at creation so the fallback color is stable across devices
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now()
);

-- ─────────────────────────────────────────────────────────────────────────
-- Sport / format lookup — makes team size & scoring configurable, not hardcoded
-- ─────────────────────────────────────────────────────────────────────────

create table sports (
    id              smallint primary key generated always as identity,
    code            text not null unique,     -- e.g. 'badminton_doubles'
    name            text not null,            -- e.g. 'Badminton (Doubles)'
    team_size       smallint not null,        -- 1 = singles, 2 = doubles
    points_per_set  smallint not null,        -- e.g. 21
    win_by          smallint not null,        -- e.g. 2
    sets_to_win     smallint not null         -- e.g. 2 (best of 3)
);

insert into sports (code, name, team_size, points_per_set, win_by, sets_to_win)
values ('badminton_doubles', 'Badminton (Doubles)', 2, 21, 2, 2);

-- ─────────────────────────────────────────────────────────────────────────
-- Groups & membership
-- ─────────────────────────────────────────────────────────────────────────

create table groups (
    id            uuid primary key default gen_random_uuid(),
    sport_id      smallint not null references sports(id),
    name          text not null,
    created_by    uuid not null references users(id),
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    is_active     boolean not null default true,
    avatar_color  text not null default '#7ED321'   -- colored-initial fallback, same pattern as users.avatar_color (added in V3)
);

-- text + check, not native enum types: Postgres enums need explicit JDBC
-- casts that make Hibernate mapping needlessly fragile; a check constraint
-- gives the same guarantee without the ORM friction.
create table group_members (
    id          uuid primary key default gen_random_uuid(),
    group_id    uuid not null references groups(id),
    user_id     uuid not null references users(id),
    role        text not null default 'member' check (role in ('owner', 'admin', 'member', 'guest')),
    status      text not null default 'active' check (status in ('active', 'removed')),
    joined_at   timestamptz not null default now(),
    unique (group_id, user_id)
);

create index idx_group_members_user on group_members(user_id) where status = 'active';

-- "Create or join a group" — short-lived join codes
create table group_invites (
    id          uuid primary key default gen_random_uuid(),
    group_id    uuid not null references groups(id),
    code        text not null unique,
    created_by  uuid not null references users(id),
    max_uses    int,                     -- null = unlimited
    used_count  int not null default 0,
    expires_at  timestamptz,
    created_at  timestamptz not null default now()
);

-- ─────────────────────────────────────────────────────────────────────────
-- Matches
-- ─────────────────────────────────────────────────────────────────────────

create table matches (
    id           uuid primary key default gen_random_uuid(),
    group_id     uuid not null references groups(id),
    played_at    timestamptz not null,   -- date/time the match was actually played
    recorded_by  uuid not null references users(id),
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    is_deleted   boolean not null default false,
    deleted_at   timestamptz,
    deleted_by   uuid references users(id)
);

create index idx_matches_group_played on matches(group_id, played_at desc) where is_deleted = false;

-- Always exactly 2 teams per match across every supported sport (racket sports);
-- team_size (players per team) varies per sport, team count does not.
create table match_teams (
    id          uuid primary key default gen_random_uuid(),
    match_id    uuid not null references matches(id),
    team_no     smallint not null check (team_no in (1, 2)),
    is_winner   boolean not null default false,
    unique (match_id, team_no)
);

create table match_participants (
    id             uuid primary key default gen_random_uuid(),
    match_id       uuid not null references matches(id),   -- denormalized from match_teams for a cheap (match_id, user_id) uniqueness check + direct "player's matches" queries
    match_team_id  uuid not null references match_teams(id),
    user_id        uuid not null references users(id),
    unique (match_id, user_id)
);

create index idx_participants_user on match_participants(user_id, match_id);
create index idx_participants_team on match_participants(match_team_id);

create table match_sets (
    id           uuid primary key default gen_random_uuid(),
    match_id     uuid not null references matches(id),
    set_no       smallint not null,
    team1_score  smallint not null,
    team2_score  smallint not null,
    unique (match_id, set_no)
);

-- Audit trail backing the Matches "History" panel + edit/delete accountability
create table match_events (
    id          uuid primary key default gen_random_uuid(),
    match_id    uuid not null references matches(id),
    user_id     uuid not null references users(id),
    action      text not null check (action in ('created', 'edited', 'deleted')),
    snapshot    jsonb,                   -- full match state at this point, for diffing/undo later
    created_at  timestamptz not null default now()
);

create index idx_match_events_match on match_events(match_id, created_at);

-- ─────────────────────────────────────────────────────────────────────────
-- Materialized leaderboard stats — the hot-path read table
-- ─────────────────────────────────────────────────────────────────────────

create table member_stats (
    group_id          uuid not null references groups(id),
    user_id           uuid not null references users(id),
    matches_played    int not null default 0,
    wins              int not null default 0,
    losses            int not null default 0,
    points_for        int not null default 0,
    points_against    int not null default 0,
    current_streak    int not null default 0,   -- positive = win streak, negative = loss streak
    best_streak       int not null default 0,
    win_rate          numeric generated always as (
                          case when matches_played = 0 then 0
                          else round(wins::numeric / matches_played, 4) end
                      ) stored,
    updated_at        timestamptz not null default now(),
    primary key (group_id, user_id)
);

create index idx_member_stats_leaderboard on member_stats(group_id, win_rate desc, wins desc);

-- ─────────────────────────────────────────────────────────────────────────
-- Refresh tokens — server-side record backing rotation + revocation
-- ─────────────────────────────────────────────────────────────────────────

-- The refresh token handed to the client is a signed JWT whose `jti` claim
-- IS this row's id; the JWT itself is never stored. POST /auth/refresh
-- rotates it (revokes this row, inserts a new one), so a stolen-and-reused
-- old token is detectable.
create table refresh_tokens (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null references users(id),
    expires_at  timestamptz not null,
    revoked_at  timestamptz,
    created_at  timestamptz not null default now()
);

create index idx_refresh_tokens_user on refresh_tokens(user_id) where revoked_at is null;

-- FCM registration tokens. A token is globally unique and can be reassigned
-- when a shared device registers it for another user.
create table device_tokens (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null references users(id) on delete cascade,
    token       text not null unique,
    platform    text not null default 'android',
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

create index idx_device_tokens_user on device_tokens(user_id);
```

## Recompute strategy

**Implemented as a single full rescan per affected player**, not the
incremental-sums/rescan-streaks split originally sketched here. Both are
"order-independent for sums, scan-required for streaks" — a full rescan
just does both in the same indexed pass (`idx_participants_user` joined to
`idx_matches_group_played`), bounded by one player's match count in one
group, the same cost this doc already accepted for streaks alone:

- **Sums** (`matches_played`, `wins`, `losses`, `points_for`,
  `points_against`) are recomputed from scratch from that player's
  non-deleted matches, rather than incremented/decremented in place. An
  edit needs the *old* contribution subtracted and the *new* one added;
  getting that delta math wrong on every edit path would silently and
  permanently corrupt stats with no way to detect the drift. A rescan
  can't drift — it's always derived straight from `matches`/`match_teams`/
  `match_participants`/`match_sets`, the source of truth.
- **Streaks** are order-dependent for the same reason they always were —
  editing a match from three weeks ago can change everything after it —
  so `current_streak`/`best_streak` are recomputed by walking that same
  ordered match list.

Implemented in `StatsRecalculationService` (`service/stats/`), called for
the union of old + new players on create/edit/delete, inside the same DB
transaction as the match write — so `member_stats` is never stale for
longer than one request. See
[project-structure.md](project-structure.md#why-this-shape) for the code
pointer.

**Best Partner** ([05-profile.md](../requirements/05-profile.md)) is
deliberately *not* materialized: it's requested for one player at a time
(low frequency, unlike the leaderboard which loads every player at once),
so it's computed on demand — group `match_participants` by teammate for
that player/group, indexed via `idx_participants_user`. If it becomes a
hot path later, add a `partner_stats` table using the same recompute
pattern as `member_stats` without touching anything else.

## How future requirements slot in

- **Singles / another sport**: insert a `sports` row with its own
  `team_size`/scoring; existing tables need no migration since team
  structure already comes from `match_teams`/`match_participants`, not
  fixed columns.
- **Match edit/delete permissions**: `group_members.role` gates the implemented
  recorder-or-owner/admin rule; no schema change is needed.
- **Deep-linking a Recent Match to its expanded Matches entry**: matches
  are already addressable by `id`; just a client-side navigation detail.
- **Group avatar / branding**: resolved via `groups.avatar_color` (V3) —
  same colored-initial fallback pattern as `users.avatar_color`. A custom
  uploaded group icon would still need its own column + storage path if
  added later.

## Open questions carried into API design

- Soft-delete retention: keep `match_events`/deleted matches forever, or
  purge after N days?
- Should `group_invites.code` be a short human-typeable code (e.g. 6
  chars) or a deep-link token? Affects the "join a group" UX.
- Rate limiting / abuse prevention on invite codes (max_uses/expiry are in
  the schema but policy isn't decided).
