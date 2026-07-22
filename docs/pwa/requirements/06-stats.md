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
- **BEST PARTNERSHIP · recent** — the two teammates' overlapping avatars, `{name} & {name}`,
  `{winsTogether}W / {gamesTogether} games together`, and the pair's win% (brand).
- **FORM · recent** — each ranked player: avatar · name · up to 5 W/L pills, newest-first.
- **BIGGEST WIN · recent** — a `+{margin} pts` badge, both team lines (winner marked "W" + bright,
  loser muted), and the set score(s).

## Behavior / Requirements
1. **Records** are all-time, from the leaderboard (`GET .../leaderboard`) + the group's match
   count. **Win leader** = the top entry with ≥ `MIN_LEADER_GAMES` (2) games, else the top-ranked
   (so a lone 1-game 100% doesn't headline). Streak records show only from `MIN_STREAK` (2) up.
2. **Recent sections** (partnership / form / biggest win) are derived **client-side** from the
   **first page** of matches (`useMatchesInfinite` page 0) — labeled "· recent". No new endpoints
   for these; they sharpen automatically as more matches load.
3. **Best partnership** — the teammate pair with the best win rate together (min
   `MIN_PARTNERSHIP_GAMES` (2) games, tie-broken by games), pairs keyed order-independently.
4. **Form** — each ranked player's last `FORM_WINDOW` (5) results, newest-first; players absent
   from the window are dropped.
5. **Biggest win** — the recent match with the largest total-points margin (summed across sets).
6. **Monthly winners** are **served** (`GET .../trophies`), not derived — a crown is awarded once
   when a month closes and never recomputed. Rendered only when non-empty (absent from the v4.4
   screenshot, which is a group with no closed-month trophies).
7. **States** — spinner while leaderboard/matches load; retry on leaderboard failure; a
   `Play some matches to see insights.` empty state when the group has no matches.

## Data needed
- `GET /groups/{groupId}/leaderboard` (rankings) · the group's `matchCount`.
- `GET /groups/{groupId}/matches` (first page) — partnership / form / biggest win.
- `GET /groups/{groupId}/trophies` (MonthlyTrophyDto[]) — monthly winners.

## Current rules (settled)
- All derivations are **pure** and unit-tested without the network (`domain-stats.test.ts`).
- `firstMaxBy` keeps the earlier (higher-ranked) entry on ties, matching Kotlin `maxByOrNull`.
- Records are all-time accurate; match-derived sections are only as complete as the loaded pages.

## Parity notes (browser)
- Android's `PullToRefreshBox` → TanStack refetch + invalidation on match mutations.
- The crown is an emoji (👑) rather than the app's 3D crown asset used elsewhere.

## Open questions
- A minimum-games threshold before a player appears in records/partnerships (same concern as
  leaderboard ranking — a 1-game 100% is noise); currently only the win-leader gates on games.
