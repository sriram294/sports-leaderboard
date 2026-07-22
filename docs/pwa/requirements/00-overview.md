# PWA scope, shell & navigation

Source: `docs/android screens` (header, group pill, bottom nav across all tabs),
Android `ui/main/MainScreen.kt`, `ui/main/MainTab.kt`, `ui/switcher/*`.

## Purpose
Playboard Web mirrors the Android app — look, feel, layout, features — for current
Chrome, Edge, Safari, and Firefox on phone and desktop widths. Mobile-first; a
560px application shell centered on wide screens. Dark by default with a working
light theme. The Android app + `docs/android screens/*.jpeg` are the parity source
of truth; browser-appropriate equivalents are allowed for Google sign-in, share,
file selection, and push.

## Layout (present on every authenticated tab)
- **Header** (sticky): the **Playboard wordmark** (racket-P + "layboard") left, a
  **settings gear** right.
- **Group switcher pill** directly under the header: group mark (rounded-square
  initial) + name + caret, with "N players" on the right. Tapping expands a panel
  listing the user's groups to switch between (create/join/invite land in the Groups slice).
- **Routed screen** — the active tab's content.
- **Bottom nav** (fixed): Board · Matches · center floating lime **"+"** · Stats ·
  Profile, using Material Symbols icons; the active tab is lime with an underline.

## Behavior / Requirements
1. **Routing.** URL-addressable routes back state-driven navigation: `/board`,
   `/matches`, `/add`, `/stats`, `/profile`, `/player/:userId`, `/settings`; `/`
   redirects to `/board`; unknown paths fall back to `/board`. Deep links work on reload.
2. **Auth gate.** The shell only renders when authed; otherwise Splash (restoring)
   or Login (see [01-login.md](01-login.md)).
3. **Active group** is app-wide state (the web analog of `GroupRepository.selectedGroup`):
   persisted in `localStorage`, resolved against the loaded group list, falling back
   to the first group if the stored one is gone. Switching groups reloads all group-scoped data.
4. **Data revision.** Group-scoped reads (leaderboard, matches, stats, profile) are
   TanStack Query keyed by group; mutations invalidate those keys so every tab reloads
   in lockstep — the web analog of Android's `dataRevision`.
5. **No-group state** shows a create/join prompt (fleshed out in the Groups slice).

## Data needed
- `GET /groups` (switcher + active-group resolution). Group-scoped screens use their
  own endpoints (leaderboard, matches, stats), documented in their slices.

## Acceptance / tests
- Install prompt available; app shell works offline after first load; no horizontal
  scroll at 320px; every authenticated destination reachable by keyboard focus.
- Shell visual review at 390px and 1280px against `docs/android screens/*.jpeg`.
- Login → Board e2e (sign-in exchange → session → shell); group switch reloads data.

## Parity notes
- Android navigation is state-held (`MainScreen`); the web uses real routes for
  shareable deep links, but transient forms are not serialized into URLs.
- Service worker is blocked in e2e so API route mocks aren't bypassed by SW fetch handling.
