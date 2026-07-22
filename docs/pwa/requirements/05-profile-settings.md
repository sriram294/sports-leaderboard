# Profile and settings

Use `GET /users/me`, `PATCH /users/me`, and multipart `POST /users/me/photo`; use the same visual identity/stats component for own and viewed players, hiding account actions for viewed players. Stats use `GET /groups/{groupId}/members/{userId}/stats`. Settings includes sign-out.

Validate image type/size before upload and preserve previous data on failure. Tests cover own/viewed states, upload/name success and failure, group scope, and sign-out.
