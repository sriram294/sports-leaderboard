create table match_record_requests (
    id              uuid primary key default gen_random_uuid(),
    group_id        uuid not null references groups(id) on delete cascade,
    requested_by    uuid not null references users(id) on delete cascade,
    idempotency_key varchar(128) not null,
    request_hash    varchar(64) not null,
    match_id        uuid references matches(id) on delete cascade,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    unique (group_id, requested_by, idempotency_key)
);

create index idx_match_record_requests_match on match_record_requests(match_id);
