# Stats (Insights) Screen

Status: **implemented.** The Stats tab is an Insights dashboard backed by the
leaderboard and the current first page of match history.

## Purpose
A group-level analytics dashboard — the "so who's actually winning / who plays
best together" view — scoped to the currently selected group. Complements the
[Board](02-board-leaderboard.md) (per-player ranking) and
[Profile](05-profile.md) (one player's stats) with group-wide records and trends.

## Layout / sections

- **Records** (all-time, accurate — from the leaderboard + `group.matchCount`):
  - Total matches (`Group.matchCount`)
  - Win leader (top leaderboard entry — server-sorted by win rate)
  - Most points (max `pointsFor`)
  - Most active (max `gamesPlayed`)
  - Longest streak and current hot streak
- **Partners** — collapsed by default; expanding reveals a player picker
  (avatar + name dropdown of everyone on the leaderboard, defaulting to the
  signed-in user) and fetches and shows that one player's partners, all-time
  accurate, ranked by games together (ties broken by win rate together), same
  row style as Profile's Partners card. Picking a different player refetches
  for them.
- **Biggest win** — the match with the largest total-points margin (teams + score).

Per-player recent form no longer has a dedicated section here — it moved to
the [Board](02-board-leaderboard.md) leaderboard row itself (a dots row under
each player's name), so it's visible without switching tabs.

## Behavior / Requirements
1. Scoped to the active group; switching groups (shared switcher) recomputes
   everything. Observe `GroupRepository.dataRevision` to refresh silently after a
   match is recorded / edited / deleted.
2. Empty state when the group has no matches ("Play some matches to see insights").
3. Dark theme, `BrandLime` accents, `PlayerAvatar` for faces, stat tiles like the
   Profile grid.

## Data / implementation notes
- Reuse `LeaderboardRepository.getLeaderboard`, `MatchRepository.getMatches`,
  and `GroupRepository.selectedGroup` (`matchCount`, `dataRevision`) for
  Records and Biggest Win.
- Leaderboard-derived records are all-time accurate. Biggest-win is computed
  **client-side from `getMatches()`**, which currently returns only the first
  page (newest ~20) — a reasonable "recent" window; labeled accordingly.
  Improves automatically once Matches pagination is wired (see
  [03-matches.md](03-matches.md)).
- **Partners reuses the same per-player endpoint as Profile** (`GET
  /groups/{groupId}/members/{userId}/stats/partners`, see
  [api-contracts.md](../backend/api-contracts.md)) — there is no separate
  group-wide "every pair" endpoint. The picker's roster comes from the
  leaderboard response already fetched for Records (everyone with at least
  one game — a player with zero games can't have partners either), so no
  extra request is needed just to populate the dropdown. All-time accurate
  and fetched only for the selected player, only when the card is expanded —
  see [data-model.md](../backend/data-model.md) for why this isn't
  materialized.
- Biggest-win is a pure function (`computeBiggestWin`) covered by unit tests
  without the network.
- Implementation: `ui/stats/StatsUiState.kt`, `StatsViewModel.kt`,
  `StatsComputations.kt`, and `StatsScreen.kt`. The ViewModel observes the
  selected group and data revision; `StatsComputationsTest` and
  `StatsViewModelTest` cover the derivations, the expand-to-fetch partner
  flow, and state flow.
- If any section becomes an actual chart (e.g. a win-rate bar), consult the
  dataviz guidance first.

## Open questions
- Minimum-games threshold before a player shows in "records" (same concern
  as leaderboard ranking — a 1-game 100% is noise). Partners has no such
  threshold — every pair that has played together at least once is shown.
- Whether records should be all-time (needs match pagination for match-derived
  ones) or explicitly "recent" for v1.
