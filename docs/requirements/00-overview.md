# Shuttle Stats — App Overview

Source prototypes: `docs/prototype/*.pdf`

## What it is
A badminton doubles match tracker for private play groups. Members log match
results, and the app maintains a live leaderboard and per-player stats.

## Navigation
Bottom tab bar, 4 destinations, present on every screen:

| Tab | Icon | Purpose |
|---|---|---|
| Board | leaderboard | Group leaderboard / rankings (home) |
| Matches | crossed rackets | Chronological match history |
| Add (center, floating) | + | Record a new match |
| Profile | person | Signed-in user's stats & account |

Per-page requirement docs:
- [01-login.md](01-login.md)
- [02-board-leaderboard.md](02-board-leaderboard.md)
- [03-matches.md](03-matches.md)
- [04-add-match.md](04-add-match.md)
- [05-profile.md](05-profile.md)

## Cross-cutting concepts

### Group
- A user can belong to multiple groups, switch via a dropdown (shown on
  Board/Matches/Add/Profile header).
- Group has: name, member count, match count.
- Group switcher dropdown shows all "Your Groups" the user belongs to, plus a
  "Create or join a group" action.

### Player / Avatar
- Every player has a display name and an avatar.
- **Avatar = user-uploaded photo if set, else a colored initial circle
  (fallback).** Color is assigned per-player and stays consistent everywhere
  the player appears (rankings, podium, match cards, team builder, profile).
- Applies to every screen that renders a player avatar.

### Match
- Doubles only: 2 players per team, 2 teams per match.
- Scored by set (e.g. `21-12`), supports multiple sets (+ Add Set), winner
  derived from sets won or explicitly selected.
- Has an audit trail: who recorded it, when.

## Open questions (carried across all pages)
- Auth: Google Sign-In only, or additional providers later?
- Permissions: who can edit/delete a match — recorder only, or any group
  member/admin? (raised in [03-matches.md](03-matches.md))
- Group roles: is there an "admin"/"owner" concept for group management
  (approving join requests, removing members)?
- Sports/format scope: doubles-only for v1, or should the data model stay
  open to singles / other sports later?
