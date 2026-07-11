-- Guest fillers: each group gets a small pool of reusable "guest" players so a
-- one-off, non-member player can be dropped into a match without becoming a
-- real member. Guests are excluded from member counts, the leaderboard, and all
-- stats (see StatsRecalculationService / StatsQueryService) — they only exist to
-- fill a match slot. Modeled as group_members with role 'guest' backed by a
-- synthetic users row, so match_participants references them like any player and
-- no match-schema change is needed.

-- Allow the new 'guest' role. The V1 inline check is auto-named
-- group_members_role_check.
alter table group_members drop constraint if exists group_members_role_check;
alter table group_members
    add constraint group_members_role_check
    check (role in ('owner', 'admin', 'member', 'guest'));

-- Backfill 3 guest fillers per existing group (enough for "1 regular + 3 guests"
-- in a doubles match). Idempotent: keyed on a deterministic per-group email, so
-- re-running never duplicates. google_sub stays null (guests never authenticate).
insert into users (email, display_name, avatar_color)
select 'guest-' || n || '+' || g.id::text || '@playboard.local',
       'Guest ' || n,
       '#9AA0A6'
from groups g
cross join generate_series(1, 3) as n
where not exists (
    select 1 from users u
    where u.email = 'guest-' || n || '+' || g.id::text || '@playboard.local'
);

insert into group_members (group_id, user_id, role, status)
select g.id, u.id, 'guest', 'active'
from groups g
cross join generate_series(1, 3) as n
join users u on u.email = 'guest-' || n || '+' || g.id::text || '@playboard.local'
where not exists (
    select 1 from group_members gm
    where gm.group_id = g.id and gm.user_id = u.id
);
