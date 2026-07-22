# Login

Show Playboard branding and Google Identity Services sign-in. Exchange the ID token with `POST /api/v1/auth/google`; persist app access/refresh tokens, refresh once on `401`, and clear the session when refresh fails. A returning user sees session resolution rather than a login flash.

Errors include cancelled sign-in, unavailable GIS, `GOOGLE_TOKEN_INVALID`, and network failure. Tokens never appear in URLs or logs. Parity is the Android login layout and lime-on-black branding.

Acceptance/tests: successful sign-in, cancellation/error recovery, session restore, refresh retry, invalid refresh logout, and protected-route behavior.
