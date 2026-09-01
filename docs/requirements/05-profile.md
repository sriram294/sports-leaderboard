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
- Settings includes **Delete account**. Its destructive confirmation explains anonymous
  shared-history retention and requires the exact typed word `DELETE`.
- Identity card: avatar, name, "N matches played", win rate %
- Stat tiles (2x3 grid): Wins, Losses, Pts For, Streak (+ Best streak
  sub-label), Best Streak, Pts Against
- **Monthly Finishes** line graph: up to 12 completed captured months, oldest
  to newest. Rank #1 is highest; months with no qualified finish are gaps.
- **Partners** card: collapsed by default; expanding fetches and shows every
  partner this player has had, ranked by games together (each row: avatar/name,
  "NW / M games together", win% with that partner)
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
4. Stats (matches played, win rate, W/L, PF/PA, streak, best streak, recent
   matches) are scoped to the **currently selected group** — switching groups
   via the header recalculates all of it for the player being viewed. The
   Partners card is fetched separately, only on expand, and also resets
   (collapses) on a group switch.
5. Recent Matches list — tapping an entry could deep-link to that match's
   expanded view on the Matches tab (not specified in prototype, natural
   extension — flag as open question).
6. Monthly finishes are frozen when each month closes in Asia/Kolkata and are
   scoped to the active group. Provisional results and months the player did
   not play remain calendar gaps. Before the first qualified point, show
   “Finishing positions are recorded after each month closes.” Historical
   months from before snapshot capture launched are not backfilled.
7. Successful account deletion clears the local session and returns to Login. Failure leaves
   the confirmation open for retry. Owned groups transfer automatically; retained shared
   records identify the account only as `Deleted player`.

## Data needed
- Per player per group: matchesPlayed, wins, losses, ptsFor, ptsAgainst,
  currentStreak, bestStreak, winRate.
- Partners: every pairing this player has had in the group, most games
  together first (tie-broken by win rate together), fetched from its own
  endpoint only when the card is expanded — not bundled into the stats above.
- Recent matches: last N matches involving this player, most recent first.
- Monthly finishes: up to 12 `{month, rank, qualifiedPlayers}` values in
  chronological order; `rank` may be null.

## Open questions
- Photo crop and remove-photo-to-revert-to-initial are not implemented.
- Do stats aggregate across all groups anywhere, or always per-group only?
- Tapping a name inside Recent Matches (e.g. "Dev", "Marcus & Kiran") —
  does it navigate to that player's profile (consistent with Board tap
  behavior)?
