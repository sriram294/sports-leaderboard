# Playboard — REST API Contracts (Spring Boot)

Base URL: `/api/v1`. All bodies are JSON (`application/json`) unless noted.
Backs the screens defined in [../requirements](../requirements/00-overview.md)
against the schema in [data-model.md](data-model.md).

## Conventions

- **Auth**: every endpoint except `GET /app/update`, `POST /auth/google`, `POST /auth/apple`, and `POST /auth/refresh`
  requires `Authorization: Bearer <accessToken>`.
- **Google Sign-In flow**: Android gets a Google ID token via Credential
  Manager (`GetGoogleIdOption`, using a Web-application OAuth Client ID as
  `serverClientId` so the token's `aud` is verifiable server-side). The
  backend verifies it once with Google's `GoogleIdTokenVerifier`, then
  mints its own access/refresh tokens — the app never uses the Google ID
  token as an ongoing API credential.
- **Apple Sign-In flow**: iOS gets an Apple identity token through
  `AuthenticationServices`. The backend verifies its signature, issuer,
  configured audience, and expiry before minting Playboard tokens. Apple
  name/email values are used only on the first grant; returning users resolve
  by the stable Apple subject.
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
            "photoUrl": null, "avatarColor": "#7ED321",
            "authProviders": ["google"] }
}
```
`401 GOOGLE_TOKEN_INVALID` if the Google token fails verification.

### `POST /auth/apple`
Exchange a native Sign in with Apple identity token for app tokens.

Request:
```json
{ "identityToken": "<apple-identity-token>", "givenName": "Raj", "familyName": "Kumar" }
```
Names are optional because Apple normally returns them only on the first
authorization. Response `200` matches `/auth/google`; `authProviders` contains
every linked provider. `401 APPLE_TOKEN_INVALID` rejects invalid tokens, `503
APPLE_AUTH_NOT_CONFIGURED` fails safely when `APPLE_CLIENT_IDS` is empty, and
`409 AUTH_EMAIL_REQUIRED` applies when an unlinked identity supplies no email.

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

### `DELETE /users/me`
Request: `{ "confirmation": "DELETE" }` → `204`.

Immediately invalidates every access/refresh credential, removes provider identities,
device registrations and active memberships, and anonymizes shared match/group history
as `Deleted player`. Owned groups transfer to the oldest active admin, then oldest active
member; a group with nobody remaining is archived. A later provider sign-in creates a new
unrelated account. `422 ACCOUNT_DELETE_CONFIRMATION_INVALID` rejects any confirmation
other than the exact uppercase value.

---

## Groups

### `GET /groups` → list of groups the caller belongs to
```json
{ "groups": [
  { "id": "uuid", "name": "Saturday Smashers", "avatarColor": "#C7EA2B",
    "sportCode": "badminton_doubles", "memberCount": 6, "matchCount": 10,
    "myRole": "owner", "sessionStart": "19:00", "sessionEnd": "21:00" }
] }
```
`sessionStart`/`sessionEnd` are the group's daily playing window ("HH:mm" local
wall-clock), or `null` when unset. Same shape on the create/join/rename responses.

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

### `DELETE /groups/{groupId}/members/{userId}`
Soft-removes a member → `204`. Owner/admin only. The owner (`403
GROUP_OWNER_PROTECTED`), guests (`400 GROUP_CANNOT_REMOVE_GUEST`), and self (`400
GROUP_CANNOT_REMOVE_SELF`) can't be removed; an admin may remove only regular
members, not other admins (`403 GROUP_ROLE_FORBIDDEN` — only the owner can). The
member's matches/stats stay for history but drop off the roster and leaderboard;
re-adding by email reactivates them.

### `PATCH /groups/{groupId}/members/{userId}`
Change a member's role. **Owner only.** Request: `{ "role": "admin" }` (or
`"member"`) → `200` `MemberDto`. The owner and guests can't be re-roled, owner
can't change their own role, and `owner` can't be assigned (`400
GROUP_ROLE_INVALID`); owner transfer is out of scope.

### `PATCH /groups/{groupId}/session`
Set/clear the group's daily session window. Owner/admin only. Request:
`{ "start": "19:00", "end": "21:00" }` (or `{ "start": null, "end": null }` to
clear) → `200` group object. `422 GROUP_SESSION_INVALID` unless both times are
given with `start < end`, or both omitted.
Creates the person as a real member: they appear in the roster, are pickable for
matches, and accrue stats (they join the leaderboard after their first match).
The email is normalized (trimmed + lowercased); if a user with that email already
exists it's reused (their existing identity wins). When that person later signs
in with Google or Apple using the same email, the provider identity is linked
to this pre-created row — membership and stats carry over, no duplicate. `409
GROUP_MEMBER_EXISTS` if they're already an active member.

---

## Leaderboard & Player Stats

### `GET /groups/{groupId}/leaderboard`
Server-sorted by `rating` desc, then points difference (`pointsFor` −
`pointsAgainst`) desc, then wins desc, with a final user-id key so fully tied
rows keep a stable order across requests. `rank` is the position in that list
(1-based, no shared ranks); members with zero matches are omitted. The Board
screen's podium is the first 3 **non-provisional** entries of this same list —
no separate endpoint, so podium and table never disagree.

Ordering is computed in Java for both the all-time and windowed paths, so they
cannot drift apart; there is deliberately no `ORDER BY` in the query.
```json
{ "rankings": [
  { "rank": 1, "userId": "uuid", "displayName": "Priya", "photoUrl": null,
    "avatarColor": "#FF3D8A", "gamesPlayed": 6, "wins": 6, "losses": 0,
    "pointsFor": 252, "pointsAgainst": 180, "winRate": 1.0,
    "currentStreak": 6, "bestStreak": 6, "rating": 54.1, "provisional": false,
    "recentForm": [true, true, false, true, true, true] }
], "minGamesToRank": 3 }
```
`pointsAgainst` was added alongside the difference tiebreak; `pointsFor` is
retained (rather than replaced by a computed difference) so clients built
against the earlier shape keep deserializing.

**`recentForm`** is the player's last 10 results within the standings window
(the same `from`/`to` as the request), in **chronological** order — oldest
first, newest last. This is deliberately the opposite of `recentMatches`
below (newest-first): `recentForm` exists to feed a left-to-right dots row on
the leaderboard, so chronological order is the render order and no client
needs to reverse it. A player with fewer than 10 matches in the window simply
gets a shorter list; one with none gets `[]`. Computed on demand in one
set-based query per leaderboard fetch, not materialized.

**`rating`** is the Wilson score lower bound on the win rate, scaled to 0–100
with one decimal — a confidence-adjusted win rate, so a small sample scores
below a long record at the same raw percentage. Sorting uses the unrounded
value.

**`minGamesToRank`** is a group-level scalar, `max(1, min(10, ceil(median(games
played) / 2)))` over players with at least one game. Players below it have
`provisional: true`: they are listed **after** every ranked player and are
excluded from the podium, but they still carry a continuing `rank` (N+1, N+2, …)
rather than a sentinel, so clients that predate the flag still render a sanely
numbered list. Clients derive "N more to rank" as
`minGamesToRank - gamesPlayed`.

**Optional time window (`?from=…&to=…`).** Supply both `from` and `to` as
ISO-8601 instants to scope the ranking to the half-open interval `[from, to)`
by `match.playedAt` — this backs the Board's "This Month" toggle. The client
computes the calendar boundaries in device-local time (month = current calendar
month) and sends the resulting UTC instants, so members in different zones split
boundaries by their own midnight. There is no weekly window: `rating` is computed
over the selected window, and one or two sessions is too few games for it to
separate anyone. Omit both params for the all-time ranking (the default and
the original behavior). Windowed responses use the identical shape, ordering,
guest-exclusion, and zero-matches-omitted rules as all-time; the only difference
is `currentStreak`/`bestStreak` are `0` (streaks are all-time-only and the board
doesn't render them). All-time reads the materialized `member_stats` snapshot;
windowed aggregates raw matches on demand. A window covering all of history is
otherwise identical to the all-time response, including every `rating` — pinned
by an integration test, because the end-of-session rank-change notification
diffs two of these responses and any divergence would invent rank changes.

### `GET /groups/{groupId}/members/{userId}/stats`
Backs both the Profile tab (own stats) and tapping a player from the
leaderboard ([02-board-leaderboard.md](../requirements/02-board-leaderboard.md)
requirement #2) — same endpoint, different `userId`.
```json
{
  "userId": "uuid", "displayName": "Raj", "photoUrl": null, "avatarColor": "#9ADE28",
  "matchesPlayed": 8, "wins": 4, "losses": 4, "pointsFor": 315, "pointsAgainst": 320,
  "winRate": 0.5, "currentStreak": 2, "bestStreak": 2,
  "recentMatches": [ /* MatchSummaryDto, newest first, capped at 5 */ ],
  "monthlyFinishes": [
    { "month": "2026-08", "rank": null, "qualifiedPlayers": 7 },
    { "month": "2026-09", "rank": 3, "qualifiedPlayers": 8 }
  ]
}
```
`monthlyFinishes` contains at most the latest 12 captured completed months in
chronological order (newest last). A provisional result or no player row produces
`rank: null`, preserving the calendar gap. `qualifiedPlayers` is the number of
non-provisional players in that frozen month. Months predating V14 have
`standings_captured = false` and are excluded rather than reconstructed.

Partner counts are **not** included here — they're their own on-demand
endpoint below, so a client can defer the query until the player actually
opens a "Partners" list rather than loading it eagerly with the rest of this
payload.

### `GET /groups/{groupId}/members/{userId}/stats/partners`
Every partner this player has had in the group, most games together first
(ties broken by win rate together, descending). Same access rules as `/stats`
(caller must be an active member; target must be an active non-guest member,
else `404 MEMBER_NOT_FOUND`). Guests are excluded as partners. Empty array if
the player has no completed matches with a teammate yet. The Stats screen's
group-wide "Partners" card calls this once per selected player (via a picker),
rather than a separate group-wide endpoint.
```json
[
  { "userId": "uuid", "displayName": "Dev", "avatarColor": "#3DB4FF",
    "gamesTogether": 2, "winsTogether": 2, "winRate": 1.0 },
  { "userId": "uuid", "displayName": "Kiran", "avatarColor": "#F5A623",
    "gamesTogether": 1, "winsTogether": 0, "winRate": 0.0 }
]
```

### `GET /groups/{groupId}/members/{userId}/attendance?from=&to=`
Backs the Profile attendance calendar — the distinct match instants the player
was in within the half-open `[from, to)` window. `from`/`to` are **required**
ISO-8601 instants; the client computes them from the current calendar month in
device-local time (matches are stored UTC, so the client buckets the returned
instants back into local days to paint the month grid). Same access rules as
`/stats` (caller must be an active member; target must be an active non-guest
member, else `404 MEMBER_NOT_FOUND`).
```json
{ "playedAt": ["2026-07-03T06:58:00Z", "2026-07-05T09:30:00Z"] }
```

---

## Matches

### `GET /groups/{groupId}/matches?cursor=&limit=20&mine=`
Flat, cursor-paginated, newest first. The client groups these by date
locally (matches the "09 Jul · 4 matches" UI) — the server stays simple.
`mine=true` scopes the page to matches the **caller** participated in (the
"My matches" filter); cursor/pagination semantics are identical either way.
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
Clients may send an `Idempotency-Key` header (maximum 128 characters). Repeating
the same caller/group/key with the same body returns the originally created match
without applying stats twice. Reusing a key with a different body returns
`409 IDEMPOTENCY_KEY_REUSED`. Callers that omit the header retain the original
non-idempotent behavior.

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
| POST | `/auth/google` | Sign in with Google |
| POST | `/auth/apple` | Sign in with Apple |
| POST | `/auth/refresh` | Refresh access token |
| POST | `/auth/logout` | Revoke refresh token |
| GET | `/app/update` | Public latest debug APK metadata |
| GET | `/users/me` | Own profile identity |
| PATCH | `/users/me` | Update display name |
| POST | `/users/me/photo` | Upload avatar photo |
| DELETE | `/users/me` | Permanently delete and anonymize own account |
| GET | `/groups` | List my groups (group switcher) |
| POST | `/groups` | Create a group |
| POST | `/groups/join` | Join via invite code |
| PATCH | `/groups/{groupId}` | Rename a group (owner/admin) |
| POST | `/groups/{groupId}/invites` | Create invite code |
| GET | `/groups/{groupId}/members` | Roster (Add Match player chips) |
| POST | `/groups/{groupId}/members` | Add a member by email (owner/admin) |
| GET | `/groups/{groupId}/leaderboard` | Board tab |
| GET | `/groups/{groupId}/members/{userId}/stats` | Profile tab / tapped player |
| GET | `/groups/{groupId}/members/{userId}/attendance` | Profile attendance calendar |
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
