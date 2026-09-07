-- Durable state for the replayable team-v1 leaderboard.  The current reader can replay
-- from source matches, while this table makes the version and checkpoint explicit for
-- backfills, diagnostics, and a future materialized read path.
create table team_rating_state (
    group_id uuid not null references groups(id) on delete cascade,
    user_id uuid not null references users(id) on delete cascade,
    algorithm_version varchar(32) not null,
    mean_skill numeric(12,6) not null,
    uncertainty numeric(12,6) not null,
    matches_processed int not null default 0,
    last_played_at timestamptz,
    last_match_id uuid,
    updated_at timestamptz not null default now(),
    primary key (group_id, user_id, algorithm_version)
);

alter table monthly_trophy add column algorithm_version varchar(32) not null default 'wilson-v1';
alter table monthly_standing add column algorithm_version varchar(32) not null default 'wilson-v1';
