-- Daily playing-session window for a group (local wall-clock time). Both nullable:
-- a group may have no set window. Used by the group-management screen and, later,
-- by session-start notifications. Timezone is deferred to the notifications feature
-- (private play groups are single-timezone).
alter table groups
    add column session_start time,
    add column session_end   time;
