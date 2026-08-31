create table user_auth_identities (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null references users(id) on delete cascade,
    provider    text not null check (provider in ('google', 'apple')),
    subject     text not null,
    created_at  timestamptz not null default now(),
    unique (provider, subject),
    unique (user_id, provider)
);

create index idx_user_auth_identities_user on user_auth_identities(user_id);

-- Preserve every existing Android/PWA Google identity. The legacy column stays
-- in place for backward-compatible reads and a safe rolling deployment.
insert into user_auth_identities (user_id, provider, subject)
select id, 'google', google_sub
from users
where google_sub is not null;
