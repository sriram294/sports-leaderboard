create table group_period_projection (
    group_id uuid not null references groups(id) on delete cascade,
    period_start timestamptz not null,
    period_end timestamptz not null,
    algorithm_version varchar(32) not null,
    source_version bigint not null,
    status varchar(16) not null check (status in ('rebuilding', 'ready', 'failed')),
    standings jsonb,
    failure_message text,
    requested_at timestamptz not null default now(),
    rebuilt_at timestamptz,
    primary key (group_id, period_start, period_end, algorithm_version)
);
create index idx_group_period_projection_pending on group_period_projection(status, requested_at);
