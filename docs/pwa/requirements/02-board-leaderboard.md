# Board and leaderboard

Use `GET /api/v1/groups/{groupId}/leaderboard`. Render the first three rankings as the podium and the same list as the table. Support GP, W, L, PF, and Win% sorting while retaining server order for ties; show avatar photo or initials. Tap a player for read-only stats and provide browser share.

Loading, retry, and zero-match states are required. Parity is Android Board including podium, group switcher, spacing, colors, and ranking semantics. Tests cover fetch, sorting, ties, empty state, player drill-down, and group change.
