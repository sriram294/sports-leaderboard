# Matches

**Source:** Android `ui/matches/*` (`MatchesScreen.kt`, `MatchesViewModel.kt`,
`MatchesUiState.kt`) · `docs/requirements/03-matches.md` · `docs/prototype/matches.pdf` ·
`docs/prototype/matches_match_expanded.pdf` · parity target `docs/android screens/matches.jpeg`.

## Purpose
The chronological log of every doubles match in the active group, grouped by day, with each
card expanding in place to its full detail (game breakdown, winner, audit history) and
edit/delete for those allowed.

## Layout (top to bottom)
- **Filter row:** loaded-match count (`N matches`) left; a **`My matches`** toggle pill right
  (outlined; lime-filled when active).
- **Day sections:** a tappable header `DD MMM · N matches` with a caret; the newest day is
  expanded by default, older days collapse to their header.
- **Match card (collapsed):** Team 1 (avatars over names) · per-set score(s) · Team 2, plus an
  expand caret. The winning side's names are lime and carry a **"W"** badge on the inner avatar
  (toward the score). **Guests** render as grey "G" avatars named "Guest 1" / "Guest 2 & Guest 3".
- **Match card (expanded):** `GAME BREAKDOWN` (per-set lines + a lime `Winner:` line),
  `HISTORY` (audit entries `{name} · {action} · DD MMM · HH:MM`), and — for the recorder or a
  moderator — outlined **Edit match** / **Delete match** actions.
- **`Load older matches`** button when more pages remain.

## Behavior / Requirements
1. **Pagination.** `GET /groups/{groupId}/matches?cursor&limit&mine` via TanStack
   `useInfiniteQuery`; "Load older matches" calls `fetchNextPage`, following `nextCursor` until
   it is absent. Re-loads on group change and on match create/delete (query invalidation).
2. **Grouping.** Matches group by **local calendar day**, newest day first, newest match first
   within a day. Day headers show `DD MMM · N matches`. Per-day expand/collapse is tracked by a
   `YYYY-MM-DD` key; only the newest day defaults open.
3. **Expand a card** → lazily `GET .../matches/{matchId}` (MatchDetailDto) for that card only,
   with its own loading / error line. Only one match is expanded at a time; collapsing clears it.
4. **`My matches`** sets `mine=true` (a new query key → refetch from page 1). `keepPreviousData`
   keeps the current list on screen while it refetches, so the pill toggles without a spinner.
5. **Delete** (guarded): a confirm dialog ("Delete match? This permanently removes the match and
   updates the leaderboard.") → `DELETE .../matches/{matchId}` → invalidate matches + leaderboard
   + the user's form. Only shown to the recorder or a moderator.
6. **Edit** routes to the Add/Edit flow carrying the match id (**full behavior lands in Slice 6**;
   this slice wires the navigation only).
7. **Permissions.** `canModify` = viewer is owner/admin **or** the match's `recordedBy.userId`
   equals the signed-in user. Others see no edit/delete.
8. **States:** initial-load spinner; retry on load failure; window-aware empty copy — a distinct
   message for the `My matches` filter vs. a group with no matches.

## Data needed
- `GET /groups/{groupId}/matches?cursor&limit&mine` → `{ matches: MatchSummaryDto[], nextCursor }`.
  Summary: `{ id, playedAt, teams: TeamDto[], sets: SetDto[] }`; `TeamDto { teamNo, isWinner,
  players: PlayerRefDto[] }`.
- `GET /groups/{groupId}/matches/{matchId}` → MatchDetailDto: summary + `recordedBy {userId,
  displayName}`, `recordedAt`, `events: MatchEventDto[] {userId, displayName, action, createdAt}`.
- `DELETE /groups/{groupId}/matches/{matchId}`.

## Current rules (settled)
- **Permissions:** the recorder, group owner, or group admin may edit/delete; others get
  `MATCH_EDIT_FORBIDDEN` on the server.
- **Delete is a soft delete** server-side: the row survives for the audit trail but is excluded
  from history, leaderboard, and profile-stat queries.
- **Guests** have no wire flag: a player ref is a guest when its `displayName` is "Guest N".
  Avatars use a **single-letter** initial (matching Android `PlayerAvatar`), so a guest reads "G".

## Parity notes (browser)
- Android's `PullToRefreshBox` → TanStack refetch + invalidation (no pull gesture).
- The delete confirm is a lightweight modal (Android `AlertDialog`); backdrop click / Cancel
  dismiss it unless a delete is in flight.

## Open questions
- `limit` is left to the server default; revisit if pages come back too large for the web list.
