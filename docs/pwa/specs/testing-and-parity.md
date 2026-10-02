# Testing and parity

Vitest covers domain/API boundary behavior, React Testing Library covers stateful controls, and Playwright covers sign-in restoration, groups, match CRUD, stats refresh, sharing fallback, and deep-link reload. Review screenshots at phone and centered desktop widths against `docs/initialscreenref/` and Android prototypes. Include keyboard focus, semantic labels, loading, empty, API error, and permission cases in every slice.

Leaderboard parity also covers team-v2 metadata and legacy fallback decoding, fresh monthly ratings and captured historical month snapshots, the qualified/Not yet ranked partition, muted provisional values, canonical podium/share order, and the retained `N more to rank` caption. All Time statistics remain available outside the Board. Explanatory model, threshold, and partner-variety subcaptions are intentionally absent from the board UI.
