# Board / Leaderboard

**Source:** Android `ui/board/*` (`BoardScreen.kt`, `LeaderboardTable.kt`,
`LeaderboardTimeRange.kt`, `BoardUiState.kt`) · `docs/requirements/02-board-leaderboard.md` ·
`docs/prototype/leaderboard.pdf` · parity target `docs/android screens/board.jpeg` (v4.5) ·
form-dots placement reference `docs/android screens/board-form-dots-ref.jpeg`.

## Purpose
The home tab. Shows the active group's leaderboard for a chosen calendar window: a podium of
the top three and a full rankings table whose right-hand metric the user can cycle, each row
carrying that player's recent form.

## Layout (top to bottom)
- **Header row:** `TOP PLAYERS` eyebrow + a muted **`This Month ▾`** window selector on the
  left; a **share** icon on the right.
- **Podium:** three columns bottom-aligned in the order **#2 · #1 · #3**. The champion (#1) is
  centered, larger, wears a 3D crown, and shows its rating in the player's color; runners-up
  flank it a step lower. Each avatar has a colored ring + soft glow and a numbered rank badge
  tucked at its bottom edge. Name + `{rating} rating` (or `{win%} win rate` pre-ratings) below.
- **RANKINGS card:** `RANKINGS` title, then a header row `#  PLAYER  …  RATING ▾` where the
  metric label is the single tappable sort control. One row per player: rank number (colored
  for the top 3), avatar, bold name over a muted `secondaryLine`, a row of small win/loss dots
  (up to 10, oldest on the left) under that, and the big right-hand metric value colored by
  tier. Provisional players sit below a stronger divider with a `—` rank.

## Behavior / Requirements
1. Load `GET /groups/{groupId}/leaderboard?from&to` for the active group; re-load on group
   change, on a recorded/edited/deleted match (query invalidation — the web `dataRevision`),
   and on window change.
2. **Window selector** — `This Month` (default) or `All Time`. `This Month` sends the current
   calendar month `[from, to)` as local-midnight ISO instants; `All Time` sends no window.
   A window change is a server round-trip (different data), not a client re-sort. The choice
   persists across group switches. `keepPreviousData` keeps the table on screen while the new
   window loads, so the header/selector never blinks to a spinner.
3. **Sort metric** cycles `Rating → Win% → Games → Diff` on each tap of the header label.
   Re-sorting is client-side and **never reorders across the ranked/provisional boundary** —
   provisional players stay last whatever the metric. `Rating` keeps canonical server order
   (its tiebreaks would be lost by re-sorting). The metric resets to `Rating` on group change.
4. **Podium** shows the top 3 **ranked** players only (provisional players are never crowned).
   Fewer than 3 ranked → empty slots. Tapping a podium avatar opens that player's profile.
5. **Rows.** `secondaryLine` = `"{games} games · {W}-{L} · {win%}% · {±diff}"`; a provisional
   player short of the threshold shows `"{games} games · {W}-{L} · {win%}% · {N} more to rank"`
   instead. Rank number is `—` and the metric reads `prov` for provisional players. Win% is
   **rounded** (42.86% → 43%). Tapping a row opens the player's profile.
6. **Colors** (both themes, from `Color.kt`): rank #1 brand / #2 textPrimary / #3 winRateMid,
   else muted. Win% tiers ≥50 brand / ≥25 mid / else low. Rating tiers ≥40 brand / ≥25 mid /
   else low. Points diff >0 statWin / <0 statLoss / else muted. Provisional metric is muted.
7. **Form dots** — each row shows that player's last ≤10 results within the selected window,
   in chronological order (oldest left, newest right) — small filled circles, green for a win
   and red for a loss. Ships with the leaderboard response (`recentForm` on each entry), so
   there's no extra request and no client-side reversal. A player with fewer than 10 matches
   just shows fewer dots; one with none shows no dots row at all.
8. **States:** initial-load spinner; retry on load failure; window-aware empty copy when a
   window has no matches (header/selector stays so the user can switch windows).

## Data needed
- `GET /groups/{groupId}/leaderboard?from&to` → `{ rankings: LeaderboardEntryDto[],
  minGamesToRank }`. Entry fields: `rank, userId, displayName, photoUrl, avatarId, avatarColor,
  gamesPlayed, wins, losses, pointsFor, pointsAgainst, winRate, currentStreak, bestStreak,
  rating, provisional, recentForm`.

## Current rules (settled)
- **Ratings** are a Wilson-style confidence-adjusted win rate (0–100). They sit below the raw
  win rate, hence the 40/25 tier thresholds rather than win%'s 50/25 — reusing the win% scale
  would paint the whole board mid-tier.
- **`rating: null`** means a pre-ratings backend; rows then fall back to showing win% in the
  rating column (and the podium shows `{win%} win rate`). `null` ≠ `0.0` — a winless player
  legitimately rates 0.0.
- **No weekly window.** A week is too few games for a confidence-adjusted rating to separate
  anyone, so only `This Month` and `All Time` exist.

## Parity notes (browser)
- Android's `PullToRefreshBox` becomes TanStack Query refetch + invalidation; there is no
  pull-to-refresh gesture.
- The window selector is a small click-outside menu (Android `DropdownMenu`).

## Open questions
- None currently open for this section.
