# Login Screen

Source: `docs/prototype/login.pdf`

## Purpose
Entry point / auth gate. First screen shown to a signed-out user.

## Layout
- App branding: "PLAYBOARD"
- Tagline: "Track every doubles match, climb the win-rate leaderboard,
  across all your groups."
- Single primary action: **Continue with Google**
- Fine print: "By continuing you agree to the terms"

## Behavior
- Tapping "Continue with Google" triggers Google Sign-In.
- On success: if the user has no groups yet, route to a group
  create/join flow; if they belong to one or more groups, route to
  **Board** for the default/last-used group.
- On failure/cancel: stay on Login, show an error state (not in prototype —
  needs a design decision).

## Data touched
- Creates/looks up a **User** record from the Google account (name, email,
  photo URL if available from Google profile — see avatar fallback rule in
  [00-overview.md](00-overview.md)).

## Open questions
- Terms link destination / content.
- Behavior for a brand-new user with zero groups (forced into create/join
  before reaching Board?).
