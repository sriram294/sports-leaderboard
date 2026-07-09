create extension if not exists "pgcrypto"; -- gen_random_uuid()

-- ─────────────────────────────────────────────────────────────────────────
-- Identity
-- ─────────────────────────────────────────────────────────────────────────

create table users (
    id            uuid primary key default gen_random_uuid(),
    google_sub    text unique,               -- nullable: room for other auth providers later
    email         text not null unique,
    display_name  text not null,
    photo_url     text,                      -- null => client falls back to initial + avatar_color
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
    id          uuid primary key default gen_random_uuid(),
    sport_id    smallint not null references sports(id),
    name        text not null,
    created_by  uuid not null references users(id),
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    is_active   boolean not null default true
);

-- text + check, not native enum types: Postgres enums need explicit JDBC
-- casts that make Hibernate mapping needlessly fragile; a check constraint
-- gives the same guarantee without the ORM friction.
create table group_members (
    id          uuid primary key default gen_random_uuid(),
    group_id    uuid not null references groups(id),
    user_id     uuid not null references users(id),
    role        text not null default 'member' check (role in ('owner', 'admin', 'member')),
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
