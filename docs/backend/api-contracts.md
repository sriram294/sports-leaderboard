# Playboard — REST API Contracts (Spring Boot)

Base URL: `/api/v1`. All bodies are JSON (`application/json`) unless noted.
Backs the screens defined in [../requirements](../requirements/00-overview.md)
against the schema in [data-model.md](data-model.md).

## Conventions

- **Auth**: every endpoint except `GET /app/update`, `POST /auth/google`, and `POST /auth/refresh`
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

## App updates

### `GET /app/update`

Public endpoint used by distributed debug builds. When no release is configured,
it returns `{ "versionCode": null, "versionName": null, "downloadUrl": null,
"available": false }`. A configured response contains the integer `versionCode`,
display `versionName`, and an HTTPS GitHub Release asset `downloadUrl`, with
`available: true`. Invalid or partial server configuration returns `500` rather
than advertising an unusable APK.

Configure the current debug release with:

```text
PLAYBOARD_UPDATE_DEBUG_VERSION_CODE=2
PLAYBOARD_UPDATE_DEBUG_VERSION_NAME=1.1
PLAYBOARD_UPDATE_DEBUG_DOWNLOAD_URL=https://github.com/<owner>/<repo>/releases/download/v1.1/Playboard-debug.apk
```

Release sequence: increment Android `versionCode` and set `versionName`, build
with the same debug signing key used by installed testers, publish the APK as a
GitHub Release asset, then update these backend variables. Never point this
debug endpoint at an APK signed with a different key.

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
], "guests": [
  { "userId": "uuid", "displayName": "Guest 1", "photoUrl": null,
    "avatarColor": "#9AA0A6", "role": "guest" }
] }
```
`members` contains real players; `guests` contains the group's reusable filler
players. Guests are valid match participants but never count toward membership,
leaderboard, or player-stat results.

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
Server-sorted by win rate desc, then points difference (`pointsFor` −
`pointsAgainst`) desc, then wins desc, with a final user-id key so fully tied
rows keep a stable order across requests. `rank` is the position in that list
(1-based, no shared ranks); members with zero matches are omitted. The Board
screen's podium is just the first 3 entries of this same list — no separate
endpoint, so podium and table never disagree.

Note the `member_stats` index covers `(group_id, win_rate desc, wins desc)`
only, so the difference key is sorted in memory — negligible at group sizes.
```json
{ "rankings": [
  { "rank": 1, "userId": "uuid", "displayName": "Priya", "photoUrl": null,
    "avatarColor": "#FF3D8A", "gamesPlayed": 6, "wins": 6, "losses": 0,
    "pointsFor": 252, "pointsAgainst": 180, "winRate": 1.0,
    "currentStreak": 6, "bestStreak": 6 }
] }
```
`pointsAgainst` was added alongside the difference tiebreak; `pointsFor` is
retained (rather than replaced by a computed difference) so clients built
against the earlier shape keep deserializing.

**Optional time window (`?from=…&to=…`).** Supply both `from` and `to` as
ISO-8601 instants to scope the ranking to the half-open interval `[from, to)`
by `match.playedAt` — this backs the Board's "This Week" / "This Month" toggle.
The client computes the calendar boundaries in device-local time (month =
current calendar month; week = current calendar week starting Monday) and sends
the resulting UTC instants, so members in different zones split boundaries by
their own midnight. Omit both params for the all-time ranking (the default and
the original behavior). Windowed responses use the identical shape, ordering,
guest-exclusion, and zero-matches-omitted rules as all-time; the only difference
is `currentStreak`/`bestStreak` are `0` (streaks are all-time-only and the board
doesn't render them). All-time reads the materialized `member_stats` snapshot;
windowed aggregates raw matches on demand.

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
unless the caller is the match recorder, group owner, or group admin.

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

### `POST /devices/test`
Diagnostic. Sends a test push to the **caller's own** registered devices and
returns FCM's result → `200` `{ firebaseEnabled, tokens, sent, failed, errors[] }`.
Isolates FCM delivery from the match/event pipeline: `sent > 0` with nothing shown
means an on-device issue; `failed > 0` surfaces the FCM error codes.

---

## Endpoint summary

| Method | Path | Purpose |
|---|---|---|
| POST | `/auth/google` | Sign in |
| POST | `/auth/refresh` | Refresh access token |
| POST | `/auth/logout` | Revoke refresh token |
| GET | `/app/update` | Public latest debug APK metadata |
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
| POST | `/devices/test` | Send a diagnostic push to the caller's devices |

## Open questions

- Whether `POST /users/me/photo` should return a pre-signed upload URL
  instead of accepting the file directly, once photo volume justifies it.
