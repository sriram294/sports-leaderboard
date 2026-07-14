# Playboard — App Overview

Source prototypes: `docs/prototype/*.pdf`

## What it is
A badminton doubles match tracker for private play groups. Members log match
results, and the app maintains a live leaderboard and per-player stats.

## Navigation
Bottom tab bar, 5 destinations, present on every screen. The floating "+" sits
in the middle so it stays centered:

| Tab | Icon | Purpose |
|---|---|---|
| Board | leaderboard | Group leaderboard / rankings (home) |
| Matches | crossed rackets | Chronological match history |
| Add (center, floating) | + | Record a new match |
| Stats | insights | Group analytics dashboard: records, partnerships, recent form, and biggest win |
| Profile | person | Signed-in user's stats & account |

Per-page requirement docs:
- [01-login.md](01-login.md)
- [02-board-leaderboard.md](02-board-leaderboard.md)
- [03-matches.md](03-matches.md)
- [04-add-match.md](04-add-match.md)
- [05-profile.md](05-profile.md)
- [06-stats.md](06-stats.md)

## Cross-cutting concepts

### Group
- A user can belong to multiple groups, switch via a dropdown in the shared
  header on every tab.
- Group has: name, colored-initial avatar, real-member count, and match count.
- Each new group includes three reusable **Guest** fillers. Guests may fill a
  match slot but are excluded from member counts, leaderboards, and stats.
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

## Current product rules
- Authentication is Google Sign-In; the API exchanges the Google ID token for
  Playboard access and refresh tokens.
- Owners and admins can rename groups, create invites, and add members.
- A match can be edited or deleted by its recorder, the group owner, or an
  admin. Deletion is soft and recalculates affected player stats.
- v1 records badminton doubles; the backend's sport model remains extensible
  for future formats.
