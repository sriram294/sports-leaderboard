-- Push-notification device tokens. Each row is one FCM registration token for a
-- user's device. A token is globally unique but may move between users on a shared
-- device, so register() upserts on `token` (reassigning user_id). Tokens are pruned
-- when FCM reports them UNREGISTERED/INVALID (see PushNotificationService), and
-- cascade-deleted with the user.
create table device_tokens (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid not null references users(id) on delete cascade,
    token      text not null unique,
    platform   text not null default 'android',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_device_tokens_user on device_tokens(user_id);
