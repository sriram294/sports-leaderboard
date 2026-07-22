# Groups

Use `GET /groups`, `POST /groups`, `POST /groups/join`, `PATCH /groups/{id}`, invite/member endpoints, and role fields from the API contract. Persist last group locally, clear player/expanded-match state on switch, and hide owner/admin controls for regular members. Group errors render stable `code` values.

Tests cover create/join/switch, empty groups, role visibility, rename/invite/add-member errors, and refresh after mutation.
