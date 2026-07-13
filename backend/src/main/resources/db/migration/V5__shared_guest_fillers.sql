-- Shared guest fillers: guests are pure placeholders (no stats, no google_sub,
-- no real identity) — every group's "Guest N" is functionally identical. Rather
-- than mint 3 fresh synthetic users per group (V4's per-group model, which grows
-- the users table 3 rows per group forever), new groups now link their guest
-- membership rows to a single global pool of 3 shared guest users seeded here.
--
-- Go-forward only: this migration seeds the shared pool but deliberately does NOT
-- touch group_members, existing per-group guest users (V4 backfill), or
-- match_participants. Existing groups keep their current per-group guests; only
-- groups created after this change reference the shared users (see
-- GroupService.seedGuestFillers, kept in sync with the naming/color below).

-- Group-agnostic emails (no group id embedded), unlike the V4 per-group pattern
-- 'guest-N+<groupId>@playboard.local', so these never collide with existing rows.
-- Idempotent: keyed on the email, google_sub stays null (guests never authenticate).
insert into users (email, display_name, avatar_color)
select 'guest-' || n || '@playboard.local',
       'Guest ' || n,
       '#9AA0A6'
from generate_series(1, 3) as n
where not exists (
    select 1 from users u
    where u.email = 'guest-' || n || '@playboard.local'
);
