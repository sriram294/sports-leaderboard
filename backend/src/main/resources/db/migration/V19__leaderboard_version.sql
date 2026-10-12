-- Monotonic invalidation token for conditional leaderboard responses and cache keys.
alter table groups add column leaderboard_version bigint not null default 0;
