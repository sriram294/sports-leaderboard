# Profile & player stats

**Source:** Android `ui/profile/*` (`ProfileScreen.kt`, `ProfileUiState.kt`,
`AttendanceCalendar.kt`, `AvatarPickerSheet.kt`, `EditNameSheet.kt`) ·
`docs/requirements/05-profile.md` · `docs/prototype/profile.pdf` · parity target
`docs/android screens/profile.jpeg`.

## Purpose
A player's stats hub — the **Profile** tab for the signed-in user (editable), and the **Board
drill-down** for any other player (read-only). Both render the same component.

## Layout (top to bottom)
- **Top actions** — own profile: a **settings gear** (→ Settings) and a **manage-groups
  people** icon (→ Groups); viewed player: a **Back** button.
- **Hero** — a ringed avatar (own: a lime **"+"** edit badge → avatar picker, with a spinner
  overlay while a photo uploads); the name (own: a **pencil** → rename sheet); a meta row
  **`{win%} win rate · 🏸 {N} matches`** (win% in brand).
- **2×3 stat tiles** — WINS · LOSSES · PTS FOR / **CURRENT STREAK** (signed, brand) · BEST
  STREAK · PTS AGNST.
- **ACTIVITY** heatmap — GitHub-style, the last 3 calendar months, Mon-first weekday axis; a day
  the player was in a match is a filled brand square.
- **BEST PARTNER** — avatar, name, `{winsTogether}W / {gamesTogether} games together`, and the
  pair's win% (in the partner's avatar color).
- **RECENT MATCHES** — cards with a WIN/LOSS badge + date, `w/ {partners} vs {opponents}`, the
  set score(s), and a colored left edge (brand win / red loss).

## Behavior / Requirements
1. **Stats** = `GET /groups/{groupId}/members/{userId}/stats` (own uses the session user id).
   Recent matches are framed from the viewed player: their team are **partners**, the other team
   **opponents**, `isWin` from their side.
2. **Heatmap** = `GET .../attendance?from&to` over the 3-month window; the response's `playedAt`
   instants map to local day-keys (`activeDays`). Loads independently and degrades silently.
3. **Own vs viewed** — own profile shows the top gear/people icons + edit affordances; a viewed
   player shows a Back button and no edit controls.
4. **Edit (own only)** — **rename** `PATCH /users/me {displayName}`; **default avatar**
   `PATCH /users/me/avatar {avatarId}` (16 bundled `public/avatars/avatarN.png`); **photo**
   `POST /users/me/photo` (multipart `file`). Each returns the updated `UserDto` → update the
   session (identity reflects immediately) and invalidate the stats + leaderboard queries.
5. **Identity source** — own profile draws name/photo/avatar from the **live session** (so an
   edit shows at once), falling back to stats; a viewed player's comes from stats.
6. **States** — full-screen spinner on first stats load; retry on failure; sections
   (heatmap / best partner / recent) render only when their data is present.

## Data needed
- `GET /groups/{groupId}/members/{userId}/stats` (PlayerStatsDto: wins/losses/pointsFor/
  pointsAgainst/winRate/currentStreak/bestStreak/matchesPlayed/bestPartner/recentMatches/trophies).
- `GET /groups/{groupId}/members/{userId}/attendance?from&to` (PlayerAttendanceDto `{playedAt}`).
- `PATCH /users/me` · `PATCH /users/me/avatar` · `POST /users/me/photo` (all → UserDto).

## Current rules (settled)
- Avatars use a **single-letter** initial fallback; the 16 default avatars are "3D Web3 Avatars".
- Photo takes priority over a default avatar id; both fall back to the colored initial.
- The current-streak tile is always brand-colored and shows the **signed** streak.

## Parity notes (browser)
- Android's `ModalBottomSheet` picker/rename → the shared `.sheet-*` bottom sheet.
- Photo upload uses a hidden `<input type=file accept="image/*">`; the browser sets the multipart
  boundary (the fetch client omits its JSON content-type for `FormData` bodies).
- **Transitional:** the gear routes to a Settings stub (account + sign-out) and the people icon
  to a Groups stub (create/join) until Slices 9–10 build those out. The Android **trophy shelf**
  renders only when `trophies` is non-empty (absent from the v4.4 profile screenshot).

## Open questions
- Client-side image type/size validation before upload is deferred to the server for now.
