-- Group avatar/branding, resolving the "Group avatar / branding" open
-- question in data-model.md — same colored-initial fallback pattern as
-- users.avatar_color, generated once at group creation.
alter table groups add column avatar_color text not null default '#7ED321';
