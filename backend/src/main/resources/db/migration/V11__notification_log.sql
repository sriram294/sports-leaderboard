-- Record of pushes already sent, one row per (user, category, dedupe key).
--
-- Two jobs:
--   * Idempotency. Scheduled senders claim a row *before* sending, so a redeploy
--     mid-run — or a second Railway instance — can't double-send: the unique index
--     rejects the duplicate first. This matters most for jobs that poll on a short
--     interval and would otherwise re-send on every tick.
--   * Audit. "Why did I get this notification?" is otherwise unanswerable.
--
-- user_id is a plain FK column with no JPA association: the log is written from
-- post-commit async threads that have no open Hibernate session, and nothing ever
-- navigates from a log row back to the user.
create table notification_log (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid not null references users(id) on delete cascade,
    category   text not null,
    dedupe_key text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- The idempotency guarantee itself, not merely an access path.
create unique index idx_notification_log_dedupe
    on notification_log(user_id, category, dedupe_key);

-- Supports pruning old rows.
create index idx_notification_log_created_at on notification_log(created_at);
