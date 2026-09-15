# Sharing and notifications

Prefer `navigator.share` with a generated leaderboard image/card. The shared board uses the
server's qualified canonical order and Skill rating labels; provisional players are omitted.
Fall back to clipboard or copyable text where unavailable. Request push permission only from
a user action, register browser tokens using `/api/v1/devices` with `platform: "web"`, and
treat failures as non-blocking. Service-worker clicks route to payload group/match when
present. Monthly rank-change and trophy notifications use the same group/month metadata as
the backend; browser capability differences remain documented rather than changing ranking
semantics.

Tests cover native share, fallback, denied permission, registration failure, sign-out unregister, unsupported browsers, and notification click routing. Android parity is visual card content; browser capability differences are documented.
