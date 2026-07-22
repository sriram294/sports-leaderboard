# Login Screen

Source: `docs/android screens` (Playboard login), Android `ui/login/*`, `docs/prototype/login.pdf`.

## Purpose
Auth gate shown to signed-out users. Branding + a single "Continue with Google"
action. On success a session starts and the app routes to the Board (or the
create/join flow when the user has no groups — handled in the Groups slice).

## Layout
- Centered column over the ambient glow.
- **Wordmark** (racket-P + "layboard", Paytone One, lime), large.
- Tagline: "Badminton, **beautifully tracked.**" (lime emphasis).
- Optional error block: message + a copyable monospace `Error code: <code>` line.
- **White pill "Continue with Google"** button (56px, white in both themes, Google
  "G" logo + label) — matches the Android white button.
- Muted legal caption at the bottom.

## Behavior / Requirements
1. **Session restore gate.** On boot, if a stored session exists show a **Splash**
   (wordmark + spinner) while validating it with `GET /users/me`; only then render
   the app. No login flash, and never show the Board behind a dead token. No stored
   session → go straight to Login.
2. **Google sign-in.** Google Identity Services returns a Google **ID token**; it is
   POSTed to `/auth/google`, which mints the app's own access + refresh JWTs. The
   Google token is never used as an ongoing credential and never appears in a URL.
3. **GIS button.** GIS renders its real button into a hidden overlay stretched over
   the styled button, so a tap triggers GIS (the reliable FedCM path) while the
   visible control keeps the app's look.
4. **Token refresh.** Each request attaches `Authorization: Bearer <access>`; a `401`
   triggers a single `/auth/refresh` (rotating the refresh token), then the request
   retries once. If refresh fails, the session is cleared and the app returns to Login.
5. **Errors** are surfaced with a friendly message plus a copyable `code`: cancelled
   sign-in, GIS unavailable, `GOOGLE_TOKEN_INVALID`, network failure. Tokens never
   appear in logs.
6. **Logout** best-effort revokes the refresh token (`POST /auth/logout`) and clears
   local state, returning to Login.

## Data needed
- `POST /auth/google` `{ idToken }` → `{ accessToken, refreshToken, expiresIn, user }` (public).
- `POST /auth/refresh` `{ refreshToken }` → rotated tokens (public).
- `POST /auth/logout` `{ refreshToken }` → 204 (Bearer).
- `GET /users/me` → `UserDto` (Bearer) — used for the boot-time session validation.

Session (`{ accessToken, refreshToken, expiresAt, user }`) persists in `localStorage`.

## Current rules
- Access token lifetime ~900s; refresh is attempted once per failed request.
- `localStorage` is the portable browser baseline; production requires HTTPS + a tight
  CSP (documented XSS review) since tokens live in JS-reachable storage.

## Parity notes
- Android uses Credential Manager; the web uses GIS + `/auth/google`. Same backend
  contract, browser-appropriate front door.
- The white Google button is a Google branding requirement — it stays white in the
  light theme too (unlike the rest of the palette).

## Open questions
- Whether to also support GIS One Tap / FedCM auto-prompt on return visits (deferred).
