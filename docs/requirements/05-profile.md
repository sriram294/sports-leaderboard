# Profile Screen

Source: `docs/prototype/profile.pdf`

## Purpose
Signed-in user's account info and personal stats within the current group.
Also doubles as the layout reused for viewing **any** player's stats when
tapped from the [Board](02-board-leaderboard.md) leaderboard.

## Layout
- Header: group switcher (same component as Board/Matches/Add)
- Account row: "Signed in with Google", email, **Sign out** button
  (own profile only — hidden when viewing another player)
- Identity card: avatar, name, "N matches played", win rate %
- Stat tiles (2x3 grid): Wins, Losses, Pts For, Streak (+ Best streak
  sub-label), Best Streak, Pts Against
- **Best Partner** card: partner avatar/name, "NW / M games together",
  win% with that partner
- **Recent Matches** list: win/loss color-coded left border, date,
  "w/ [partner] vs [opponent1 & opponent2]", set scores

## Behavior / Requirements
1. Own profile: full account section (email, sign out) + stats.
2. **Viewed via Board tap** (another player): same stats layout, no
   account section — see [02-board-leaderboard.md](02-board-leaderboard.md)
   requirement #2.
3. Avatar follows the global rule: uploaded photo if set, else colored initial
   circle ([00-overview.md](00-overview.md)). On an own profile, tap the
   avatar to select and upload a replacement photo; the profile name can also
   be changed through the edit-name sheet.
4. Stats (matches played, win rate, W/L, PF/PA, streak, best streak, best
   partner, recent matches) are scoped to the **currently selected group**
   — switching groups via the header recalculates all of it for the player
   being viewed.
5. Recent Matches list — tapping an entry could deep-link to that match's
   expanded view on the Matches tab (not specified in prototype, natural
   extension — flag as open question).

## Data needed
- Per player per group: matchesPlayed, wins, losses, ptsFor, ptsAgainst,
  currentStreak, bestStreak, winRate.
- Best partner: computed pairing with highest win% (min games threshold
  TBD) among all partners played with in this group.
- Recent matches: last N matches involving this player, most recent first.

## Open questions
- Photo crop and remove-photo-to-revert-to-initial are not implemented.
- Best Partner minimum-games threshold (a 1-game 100% "best partner" may
  be misleading — same concern as leaderboard ranking).
- Do stats aggregate across all groups anywhere, or always per-group only?
- Tapping a name inside Recent Matches (e.g. "Dev", "Marcus & Kiran") —
  does it navigate to that player's profile (consistent with Board tap
  behavior)?
