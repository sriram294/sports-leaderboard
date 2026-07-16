# Board (Leaderboard) Screen

Source: `docs/prototype/leaderboard.pdf`, `docs/prototype/leaderboard_groups_dropdown.pdf`

## Purpose
Home tab. Shows the ranked leaderboard for the currently selected group.

## Layout
- Header: app title + signed-in user's avatar (top right, tap → Profile)
- **Group switcher**: current group name, avatar, member count, expand
  chevron. Expands to a panel listing:
  - Each group the user belongs to (name, avatar, member + match count),
    checkmark on the active one
  - "+ Create or join a group" action
- **Top Players podium**: #1/#2/#3 players by win rate, each showing
  avatar, name, win rate %, W-L record. #1 is visually emphasized
  (larger, glowing highlight).
- **Share** action: renders the active group's leaderboard as an image and
  opens the Android share sheet.
- **Rankings table**: all players in the group, columns: Rank, Player
  (avatar + name), GP, W, L, DIFF, Win% (sortable — shown with a dropdown
  arrow on the Win% column header). DIFF is points for − against, signed
  (`+27` / `-14`), green when positive and red when negative.

## Behavior / Requirements
1. Switching groups reloads podium + rankings for the selected group.
2. **Tapping a player (podium card or rankings row) opens that player's
   stats view** — the same layout as the [Profile](05-profile.md) screen's
   stats section (matches played, win rate, W/L, PF/PA, streak, best
   partner, recent matches), scoped to the tapped player instead of the
   signed-in user. Read-only (no sign-out/account section, since it isn't
   "your" profile).
3. Rankings table is sortable by GP, W, L, DIFF, and Win%; Win% is the default.
4. Avatar rendering follows the global rule: uploaded photo, else colored
   initial ([00-overview.md](00-overview.md)).

## Data needed
- Per group: list of players with GP, W, L, PF, PA, win%. The table shows the
  PF − PA difference rather than PF itself; both raw totals remain on Profile.

## Current ranking behavior

- Canonical order is **win rate desc, then points difference (PF − PA) desc,
  then wins desc**, with a final user-id key so fully tied rows can't shuffle
  between requests. UI-only column sorts retain that canonical relative order
  for equal values.
- Points difference rather than wins breaks a win-rate tie because win rate
  already normalises for games played — at an equal rate, "more wins" only
  means "played more", whereas the difference reflects how decisively the
  matches were won. Note the difference is a running total, so it still
  rewards volume somewhat; a per-game difference would be the pure form.
- Win% is **rounded** for display, not truncated. Truncating showed 42.86%
  and 42.11% both as "42%", making distinct rates look tied while the server
  ranked them apart.

## Open questions

- Minimum games threshold before a player appears/ranks (a 1-game 100%
  player outranking a 6-game 100% player may need a minimum GP rule).
- Empty state: group with zero matches played.
