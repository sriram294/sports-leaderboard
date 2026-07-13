# Playboard — REST API Contracts (Spring Boot)

Base URL: `/api/v1`. All bodies are JSON (`application/json`) unless noted.
Backs the screens defined in [../requirements](../requirements/00-overview.md)
against the schema in [data-model.md](data-model.md).

## Conventions

- **Auth**: every endpoint except `POST /auth/google` and `POST /auth/refresh`
  requires `Authorization: Bearer <accessToken>`.
- **Google Sign-In flow**: Android gets a Google ID token via Credential
  Manager (`GetGoogleIdOption`, using a Web-application OAuth Client ID as
  `serverClientId` so the token's `aud` is verifiable server-side). The
  backend verifies it once with Google's `GoogleIdTokenVerifier`, then
  mints its own access/refresh tokens — the app never uses the Google ID
  token as an ongoing API credential.
- **Group-scoped endpoints** (`/groups/{groupId}/...`) return `403` if the
  caller isn't an active member of that group.
- **Errors**: [RFC 7807](https://www.rfc-editor.org/rfc/rfc7807) via Spring's
  `ProblemDetail`, plus a stable `code` extension field so the Android app
  can switch on a code instead of parsing message strings:
  ```json
  {
    "type": "about:blank",
    "title": "Not Found",
    "status": 404,
    "detail": "Invite code has expired",
    "code": "INVITE_EXPIRED"
  }
  ```
- **Pagination**: cursor-based (`cursor` + `limit`, default 20 / max 50).
  Avoids `OFFSET` scans as match history grows; the cursor encodes
  `(playedAt, id)` from the last row of the previous page.
- **IDs**: UUID strings everywhere.
- **OpenAPI**: generated live via `springdoc-openapi` at `/v3/api-docs` and
  `/swagger-ui.html` — the source of truth once the backend exists; this doc
  is the contract to build both sides against before that's running.

---

## Auth

### `POST /auth/google`
Exchange a Google ID token (from Android Credential Manager) for app tokens.

Request:
```json
{ "idToken": "<google-id-token>" }
```
Response `200`:
```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "expiresIn": 900,
  "user": { "id": "uuid", "displayName": "Raj", "email": "raj@gmail.com",
            "photoUrl": null, "avatarColor": "#7ED321" }
}
```
`401 GOOGLE_TOKEN_INVALID` if the Google token fails verification.

### `POST /auth/refresh`
Request: `{ "refreshToken": "..." }`
Response `200`: same shape as above minus `user`.
`401 REFRESH_TOKEN_INVALID` if expired/revoked.

### `POST /auth/logout`
Revokes the refresh token. `204`.

---

## Users

### `GET /users/me` → `UserDto`
```json
{ "id": "uuid", "displayName": "Raj", "email": "raj@gmail.com",
  "photoUrl": null, "avatarColor": "#7ED321", "createdAt": "2026-01-05T10:00:00Z" }
```

### `PATCH /users/me`
Request: `{ "displayName": "Raj K" }` → `UserDto`

### `POST /users/me/photo` (`multipart/form-data`, field `file`)
→ `UserDto` with updated `photoUrl`. (v1: direct upload through the API to
object storage; a pre-signed-URL upload flow is a drop-in optimization
later if photo volume grows — doesn't change this contract's shape.)

---

## Groups

### `GET /groups` → list of groups the caller belongs to
```json
{ "groups": [
  { "id": "uuid", "name": "Saturday Smashers", "avatarColor": "#C7EA2B",
    "sportCode": "badminton_doubles", "memberCount": 6, "matchCount": 10,
    "myRole": "owner" }
] }
```

### `POST /groups`
Request: `{ "name": "Saturday Smashers", "sportCode": "badminton_doubles" }`
→ `201` group object (shape above), caller becomes `owner`.

### `POST /groups/join`
Request: `{ "code": "SMASH42" }` → `200` group object.
`404 GROUP_INVITE_INVALID` if code is wrong/expired/exhausted.

### `PATCH /groups/{groupId}`
Rename a group. Requires `owner`/`admin` (`403 GROUP_ROLE_FORBIDDEN` otherwise).
Request: `{ "name": "New Name" }` → `200` group object (avatar color unchanged).

### `POST /groups/{groupId}/invites`
Requires `owner`/`admin` (`403 GROUP_ROLE_FORBIDDEN` otherwise). Request:
`{ "maxUses": 10, "expiresInHours": 168 }` (both optional) →
`{ "code": "SMASH42", "expiresAt": "2026-07-16T00:00:00Z" }`

### `GET /groups/{groupId}/members`
```json
{ "members": [
  { "userId": "uuid", "displayName": "Priya", "photoUrl": null,
    "avatarColor": "#FF3D8A", "role": "member" }
] }
```

### `POST /groups/{groupId}/members`
Add a person to the group by email + name — onboards someone who can't sign in
yet (e.g. no iOS app). Requires `owner`/`admin` (`403 GROUP_ROLE_FORBIDDEN`
otherwise). Request: `{ "email": "sam@gmail.com", "displayName": "Sam" }` →
`201` `MemberDto` (the added member, role `member`).
Creates the person as a real member: they appear in the roster, are pickable for
matches, and accrue stats (they join the leaderboard after their first match).
The email is normalized (trimmed + lowercased); if a user with that email already
exists it's reused (their existing identity wins). When that person later signs
in with Google using the same email, their `google_sub` is linked to this
pre-created row — membership and stats carry over, no duplicate. `409
GROUP_MEMBER_EXISTS` if they're already an active member.

---

## Leaderboard & Player Stats

### `GET /groups/{groupId}/leaderboard`
Server-sorted by win rate desc, then wins desc (matches the `member_stats`
index). The Board screen's podium is just the first 3 entries of this same
list — no separate endpoint, so podium and table never disagree.
```json
{ "rankings": [
  { "rank": 1, "userId": "uuid", "displayName": "Priya", "photoUrl": null,
    "avatarColor": "#FF3D8A", "gamesPlayed": 6, "wins": 6, "losses": 0,
    "pointsFor": 252, "winRate": 1.0, "currentStreak": 6, "bestStreak": 6 }
] }
```

### `GET /groups/{groupId}/members/{userId}/stats`
Backs both the Profile tab (own stats) and tapping a player from the
leaderboard ([02-board-leaderboard.md](../requirements/02-board-leaderboard.md)
requirement #2) — same endpoint, different `userId`.
```json
{
  "userId": "uuid", "displayName": "Raj", "photoUrl": null, "avatarColor": "#9ADE28",
  "matchesPlayed": 8, "wins": 4, "losses": 4, "pointsFor": 315, "pointsAgainst": 320,
  "winRate": 0.5, "currentStreak": 2, "bestStreak": 2,
  "bestPartner": { "userId": "uuid", "displayName": "Dev", "avatarColor": "#3DB4FF",
                   "gamesTogether": 2, "winsTogether": 2, "winRate": 1.0 },
  "recentMatches": [ /* MatchSummaryDto, newest first, capped at 5 */ ]
}
```
`bestPartner` is `null` if the player has no completed matches with a
teammate yet.

---

## Matches

### `GET /groups/{groupId}/matches?cursor=&limit=20`
Flat, cursor-paginated, newest first. The client groups these by date
locally (matches the "09 Jul · 4 matches" UI) — the server stays simple.
```json
{
  "matches": [
    {
      "id": "uuid", "playedAt": "2026-07-09T06:58:00Z",
      "teams": [
        { "teamNo": 1, "isWinner": true, "players": [
            { "userId": "uuid", "displayName": "Raj", "avatarColor": "#9ADE28", "photoUrl": null },
            { "userId": "uuid", "displayName": "Dev", "avatarColor": "#3DB4FF", "photoUrl": null } ] },
        { "teamNo": 2, "isWinner": false, "players": [ /* Marcus, Kiran */ ] }
      ],
      "sets": [ { "setNo": 1, "team1Score": 21, "team2Score": 12 },
                { "setNo": 2, "team1Score": 21, "team2Score": 17 } ]
    }
  ],
  "nextCursor": "eyJwbGF5ZWRBdCI6Li4ufQ=="
}
```

### `GET /groups/{groupId}/matches/{matchId}`
Full detail — fetched only when a card is expanded, keeping the list
payload light (mirrors the schema's list/detail split).
```json
{
  "id": "uuid", "playedAt": "2026-07-09T06:58:00Z",
  "teams": [ /* same as above */ ], "sets": [ /* same as above */ ],
  "recordedBy": { "userId": "uuid", "displayName": "Raj" },
  "recordedAt": "2026-07-09T06:58:00Z",
  "events": [
    { "userId": "uuid", "displayName": "Raj", "action": "created",
      "createdAt": "2026-07-09T06:58:00Z" }
  ]
}
```

### `POST /groups/{groupId}/matches`
Request:
```json
{
  "playedAt": "2026-07-09T06:58:00Z",
  "teams": [
    { "teamNo": 1, "playerIds": ["uuid-raj", "uuid-dev"] },
    { "teamNo": 2, "playerIds": ["uuid-marcus", "uuid-kiran"] }
  ],
  "sets": [ { "setNo": 1, "team1Score": 21, "team2Score": 12 },
            { "setNo": 2, "team1Score": 21, "team2Score": 17 } ],
  "winningTeamNo": 1
}
```
→ `201` `MatchDetailDto`. Validation: `playerIds` count per team must equal
the group's sport `teamSize`; a player can't appear on both teams;
`winningTeamNo` must be `1` or `2`. Triggers the `member_stats` recompute
for all 4 players in the same transaction (see
[data-model.md § Recompute strategy](data-model.md#recompute-strategy)).
`422 MATCH_INVALID_TEAMS` / `422 MATCH_INVALID_SCORES` on validation failure.

### `PATCH /groups/{groupId}/matches/{matchId}`
Same request shape as `POST`, full replace. → `200` `MatchDetailDto`.
Recomputes `member_stats` for the union of old + new players (a roster
edit can affect players no longer on the match). `403 MATCH_EDIT_FORBIDDEN`
if the caller isn't the recorder/an admin (exact rule still open per
[03-matches.md](../requirements/03-matches.md)).

### `DELETE /groups/{groupId}/matches/{matchId}`
Soft-deletes (`is_deleted = true`), recomputes `member_stats` for its
players. `204`. Same permission rule as edit.

---

## Devices (push notifications)

FCM registration tokens for the caller's devices. Used to deliver push
notifications (a match is recorded/edited → active group members except the
actor; a member is added → the added user). Registration is an idempotent
upsert on the token — the same token re-registered by a different user (shared
device) is reassigned, not duplicated.

### `POST /devices`
Body `{ "token": string, "platform"?: string }` (`platform` defaults to
`"android"`). Registers/refreshes the caller's device token. → `204`.

### `DELETE /devices`
Body `{ "token": string }`. Unregisters the token (only the caller's own) so a
signed-out device stops receiving pushes. → `204`. Unknown/foreign tokens are a
no-op.

---

## Endpoint summary

| Method | Path | Purpose |
|---|---|---|
| POST | `/auth/google` | Sign in |
| POST | `/auth/refresh` | Refresh access token |
| POST | `/auth/logout` | Revoke refresh token |
| GET | `/users/me` | Own profile identity |
| PATCH | `/users/me` | Update display name |
| POST | `/users/me/photo` | Upload avatar photo |
| GET | `/groups` | List my groups (group switcher) |
| POST | `/groups` | Create a group |
| POST | `/groups/join` | Join via invite code |
| PATCH | `/groups/{groupId}` | Rename a group (owner/admin) |
| POST | `/groups/{groupId}/invites` | Create invite code |
| GET | `/groups/{groupId}/members` | Roster (Add Match player chips) |
| POST | `/groups/{groupId}/members` | Add a member by email (owner/admin) |
| GET | `/groups/{groupId}/leaderboard` | Board tab |
| GET | `/groups/{groupId}/members/{userId}/stats` | Profile tab / tapped player |
| GET | `/groups/{groupId}/matches` | Matches tab list |
| GET | `/groups/{groupId}/matches/{matchId}` | Expanded match + history |
| POST | `/groups/{groupId}/matches` | Record match (Add tab) |
| PATCH | `/groups/{groupId}/matches/{matchId}` | Edit match |
| DELETE | `/groups/{groupId}/matches/{matchId}` | Delete match |
| POST | `/devices` | Register this device's FCM token |
| DELETE | `/devices` | Unregister this device's FCM token |

## Open questions

- Exact edit/delete permission rule (recorder-only vs. admin-override) —
  endpoint contract stays the same either way, just the `403` condition
  changes server-side.
- Whether `POST /users/me/photo` should return a pre-signed upload URL
  instead of accepting the file directly, once photo volume justifies it.
