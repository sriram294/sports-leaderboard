# Stats (Insights) Screen

Status: **planned / not yet built.** The 5th bottom-nav tab exists but currently
shows an "Insights coming soon" placeholder. This doc is the spec for the real
screen.

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
- **Best partnership** — the teammate pair with the best win rate together
  (min 2 games together, tie-break by games), with both avatars + "Nw / M games".
  Same rule the backend uses for Profile's Best Partner
  (`StatsQueryService.computeBestPartner`).
- **Recent form** — each ranked player's last-5 W/L as colored pills
  (`StatWinGreen` / `StatLossRed`), most-recent-first.
- **Biggest win** — the match with the largest total-points margin (teams + score).

## Behavior / Requirements
1. Scoped to the active group; switching groups (shared switcher) recomputes
   everything. Observe `GroupRepository.dataRevision` to refresh silently after a
   match is recorded / edited / deleted.
2. Empty state when the group has no matches ("Play some matches to see insights").
3. Dark theme, `BrandLime` accents, `PlayerAvatar` for faces, stat tiles like the
   Profile grid.

## Data / implementation notes
- **No new backend endpoints.** Reuse `LeaderboardRepository.getLeaderboard`,
  `MatchRepository.getMatches`, and `GroupRepository.selectedGroup`
  (`matchCount`, `dataRevision`).
- Leaderboard-derived records are all-time accurate. Partnership / form /
  biggest-win are computed **client-side from `getMatches()`**, which currently
  returns only the first page (newest ~20) — a reasonable "recent" window; label
  those sections accordingly. Improves automatically once Matches pagination is
  wired (see [03-matches.md](03-matches.md)).
- Keep the derivations as pure functions (`computeBestPartnership`, `computeForm`,
  `computeBiggestWin`) so they're unit-testable without the network.
- Mirror the Board slice shape: `ui/stats/StatsUiState.kt` +
  `ui/stats/StatsViewModel.kt` (`@HiltViewModel`, observes `selectedGroup` +
  `groupsLoadState` + `dataRevision`, like `BoardViewModel`) + `StatsScreen.kt`
  (previewable content). Add `StatsViewModelTest`.
- If any section becomes an actual chart (e.g. a win-rate bar), consult the
  dataviz guidance first.

## Open questions
- Minimum-games threshold before a player shows in "records"/partnerships
  (same concern as leaderboard ranking — a 1-game 100% is noise).
- Whether records should be all-time (needs match pagination for match-derived
  ones) or explicitly "recent" for v1.
