# Matches

Use cursor pagination from `GET /api/v1/groups/{groupId}/matches`; fetch detail lazily with `GET .../matches/{matchId}`. Group by local date, allow one expanded card, and show set breakdown, recorder, audit events, edit, and delete. Use `PATCH` and `DELETE` contracts without inventing payloads.

Confirm destructive delete, preserve list on permission errors, and avoid duplicate cursors. Tests cover pagination, lazy detail, expansion exclusivity, delete confirmation, API failure, and group refresh.
