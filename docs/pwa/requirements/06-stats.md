# Stats / Insights

**Source:** Android `ui/stats/*` (`StatsScreen.kt`, `StatsUiState.kt`, `StatsComputations.kt`,
`StatsViewModel.kt`) · `docs/requirements/06-stats.md` · parity target
`docs/android screens/stats.jpeg`.

## Purpose
A group-level analytics dashboard scoped to the active group — "who's actually winning / who
plays best together" — complementing the Board (per-player ranking) and Profile (one player).

## Layout (top to bottom)
- **RECORDS** — big total-matches count + `matches played`, then leader rows: **WIN LEADER**
  (win%), **MOST POINTS** (`pointsFor`), **MOST ACTIVE** (`{games} games`), **LONGEST STREAK**
  (`{n} in a row`), **HOT STREAK** (`{n} in a row`). Each row: label · avatar · name · value (brand).
- **MONTHLY WINNERS** (only when present) — a horizontally-scrolling row of crowned avatars with
  name + `MON 'YY`.
- **PARTNERS** — a collapsed card with a player picker. Expanding lazily fetches that
  player's non-guest partners, ordered by games together, with wins/games and pair win%.
- **BIGGEST WIN · recent** — a `+{margin} pts` badge, both team lines (winner marked "W" + bright,
  loser muted), and the set score(s).

Per-player recent form no longer has a section here — it moved to the
[Board](02-board-leaderboard.md) leaderboard row (a dots row under each player's name), which is
both more visible and more accurate (server-computed exact last-10, not derived from whichever
matches happen to be in the loaded page).

## Behavior / Requirements
1. **Records** are all-time, from the leaderboard (`GET .../leaderboard`) + the group's match
   count. **Win leader** = the top entry with ≥ `MIN_LEADER_GAMES` (2) games, else the top-ranked
   (so a lone 1-game 100% doesn't headline). Streak records show only from `MIN_STREAK` (2) up.
2. **Biggest win** is derived **client-side** from the **first page** of matches
   (`useMatchesInfinite` page 0) and labeled "· recent".
3. **Partners** calls `GET .../members/{userId}/stats/partners` only while expanded and
   whenever the selected player changes.
4. **Biggest win** — the recent match with the largest total-points margin (summed across sets).
5. **Monthly winners** are **served** (`GET .../trophies`), not derived — a crown is awarded once
   when a month closes and never recomputed. Rendered only when non-empty (absent from the v4.4
   screenshot, which is a group with no closed-month trophies).
6. **States** — spinner while leaderboard/matches load; retry on leaderboard failure; a
   `Play some matches to see insights.` empty state when the group has no matches.

## Data needed
- `GET /groups/{groupId}/leaderboard` (rankings) · the group's `matchCount`.
- `GET /groups/{groupId}/matches` (first page) — biggest win.
- `GET /groups/{groupId}/members/{userId}/stats/partners` — selected player's partners.
- `GET /groups/{groupId}/trophies` (MonthlyTrophyDto[]) — monthly winners.

## Current rules (settled)
- Record and biggest-win derivations are **pure** and unit-tested without the network (`domain-stats.test.ts`).
- `firstMaxBy` keeps the earlier (higher-ranked) entry on ties, matching Kotlin `maxByOrNull`.
- Records are all-time accurate; match-derived sections are only as complete as the loaded pages.

## Parity notes (browser)
- Android's `PullToRefreshBox` → TanStack refetch + invalidation on match mutations.
- The crown is an emoji (👑) rather than the app's 3D crown asset used elsewhere.

## Open questions
- A minimum-games threshold before a player appears in records/partnerships (same concern as
  leaderboard ranking — a 1-game 100% is noise); currently only the win-leader gates on games.
