-- Monthly finishing positions intentionally start at this migration. Existing trophy rows
-- remain false and are never reconstructed from mutable historical matches.
alter table monthly_trophy
    add column standings_captured boolean not null default false;

create table monthly_standing (
    id           uuid primary key default gen_random_uuid(),
    group_id     uuid not null references groups(id) on delete cascade,
    user_id      uuid not null references users(id),
    month        date not null,
    rank         int not null check (rank > 0),
    rating       numeric(5,1) not null,
    games_played int not null check (games_played > 0),
    wins         int not null check (wins >= 0),
    provisional  boolean not null,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    constraint uq_monthly_standing_group_month_user unique (group_id, month, user_id),
    constraint fk_monthly_standing_trophy foreign key (group_id, month)
        references monthly_trophy(group_id, month) on delete cascade
);

create index idx_monthly_standing_user_history
    on monthly_standing(group_id, user_id, month desc);
