-- Account rows are retained only as anonymous tombstones because shared match,
-- group, trophy, and audit history has non-null foreign keys to users.
alter table users add column deleted_at timestamptz;

create index idx_users_active on users(id) where deleted_at is null;
