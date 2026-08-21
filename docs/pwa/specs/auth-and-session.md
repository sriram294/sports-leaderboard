# Auth and session

GIS returns a Google ID token only for the exchange endpoint. The API access token is sent as a Bearer token; concurrent `401` responses share one rotating refresh and each request retries at most once. A failed refresh broadcasts session invalidation so the authenticated shell immediately returns to Login. `localStorage` is the current portable browser baseline. Logout revokes the refresh token and clears local state.

## XSS review and deployment controls

Access and refresh tokens are deliberately stored in `localStorage`, so any script executing in the Playboard origin could read them. The production deployment therefore:

- serves only over HTTPS;
- uses the CSP in `pwa/vercel.json`, allowing scripts and frames only from the app origin and Google Identity Services, blocking plugins, framing, and unapproved network destinations;
- bundles application, Firebase, fonts, and UI code locally; no user-authored HTML is rendered and React text interpolation remains escaped;
- never places Google or application tokens in URLs, rendered errors, analytics, or logs;
- treats uploaded photos and remote avatar URLs as images only; they are never injected as markup;
- runs `npm audit`, build, unit tests, and browser tests in the PWA workflow.

Any new third-party script, HTML-rendering API, analytics SDK, or production API origin requires updating this review and the CSP allowlist in the same change. Moving refresh tokens to secure same-site cookies remains the preferred future defense-in-depth migration if the backend contract changes.
