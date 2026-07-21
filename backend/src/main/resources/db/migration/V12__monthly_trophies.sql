-- One verdict per group per completed calendar month: who topped that month's leaderboard.
--
-- The board's monthly window is a live view that resets — nothing recorded who actually won
-- a month once it ended. These rows are that record, and they are deliberately a *snapshot*:
-- a match edited or deleted after the month closed does not retract a trophy someone has
-- already been shown. Recomputing on read would make trophies silently disappear months
-- later, which is worse than disagreeing with a recomputed historical board.
--
-- user_id is nullable, and that nullability is load-bearing. A month can close with no
-- winner, and it still has to be marked decided — otherwise the award job re-evaluates it on
-- every tick, forever. A null user_id means "evaluated, no qualifying player", not "not yet
-- processed".
--
-- Note this is NOT the "too few games" case: the ranking threshold slides with the group's
-- median, so the median player always clears it and any month with play among eligible
-- members produces a winner. An empty month means nobody *eligible* played it — every
-- participant has since left the group, or only guests were on court.
--
-- month is the first day of the awarded month in Asia/Kolkata (the group's real timezone;
-- nothing in this schema stores a per-group zone, so the job owns that choice).
create table monthly_trophy (
    id           uuid primary key default gen_random_uuid(),
    group_id     uuid not null references groups(id) on delete cascade,
    user_id      uuid references users(id),
    month        date not null,
    -- Snapshot of the winning stats as they stood when the month closed, so the trophy can
    -- be rendered without re-running the ranker over a window whose data may have moved.
    rating       numeric(5,1),
    games_played int,
    wins         int,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now()
);

-- The idempotency guarantee itself, not merely an access path: the award job claims a month
-- with `insert ... on conflict do nothing`, so a repeated scan — or a second Railway
-- instance scanning concurrently — cannot double-award.
create unique index idx_monthly_trophy_group_month on monthly_trophy(group_id, month);

-- Supports the per-player trophy list on a profile.
create index idx_monthly_trophy_user on monthly_trophy(user_id, month desc);
