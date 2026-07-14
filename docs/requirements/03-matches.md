# Matches Screen

Source: `docs/prototype/matches.pdf`, `docs/prototype/matches_match_expanded.pdf`

## Purpose
Chronological log of every doubles match recorded in the current group.

## Layout
- Header: group switcher (same component as Board)
- List label: "N doubles matches · tap to expand"
- Matches grouped by date (e.g. "09 Jul · 4 matches"), most recent first
- Each match card (collapsed): Team 1 avatars + names, per-set scores,
  Team 2 avatars + names, "W" badge on the winning team, expand chevron

## Behavior / Requirements
1. Tapping a match card expands it in place to show:
   - **Game Breakdown**: score per set, explicit winner line
     ("Winner: Raj & Dev")
   - **History**: audit log entries — who recorded the match and when
     (e.g. "Raj · Recorded this match · 09 Jul · 06:58"). Should also log
     edits (see below) as additional history entries.
   - **Edit** and **Delete** actions on the expanded card.
2. **Edit**: reopens the match in an edit form (same fields as
   [Add Match](04-add-match.md): teams, per-set scores, winner) pre-filled
   with current values. Saving appends an entry to the match's History log
   (who edited, when) and recalculates any leaderboard/profile stats
   dependent on this match.
3. **Delete**: removes the match after confirmation; recalculates
   leaderboard/profile stats. Needs a confirmation dialog (destructive
   action).
4. Only one match expanded at a time, or independently — TBD (prototype
   shows only one expanded example, doesn't confirm multi-expand).

## Data needed
- Match: id, group id, date/time, team1 (2 player ids), team2 (2 player
  ids), sets (array of score pairs), winning team, recordedBy (user id),
  recordedAt, editHistory (list of {userId, action, timestamp}).

## Current rules

- **Permissions**: the recorder, group owner, or group admin may edit or
  delete a match; other members receive `MATCH_EDIT_FORBIDDEN`.
- Delete is a soft delete: the match stays available to the audit trail but is
  excluded from history, leaderboard, and profile-stat queries.

## Open questions
- Any edit window/lock (e.g. can't edit a match older than N days)?
