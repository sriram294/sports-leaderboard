# Add / Edit match

**Source:** Android `ui/add/*` (`AddMatchScreen.kt`, `AddMatchViewModel.kt`,
`AddMatchUiState.kt`, `PlayerPickerSheet.kt`) · `docs/requirements/04-add-match.md` ·
`docs/prototype/add.pdf` · parity target `docs/android screens/add.jpeg`.

## Purpose
The form (reached via the floating center "+") to record a new doubles match — or, in edit
mode, to full-replace an existing one — for the active group.

## Layout (top to bottom)
- **`Recording as {name} · {email}`** — who is submitting.
- **`BUILD TEAMS`**: two columns (`Team 1` / `Team 2`) with a `VS` between; each column has
  **2 circular slots**. An empty slot is a `+` circle; a filled slot shows the player's avatar
  over their name (guests read "Guest").
- **`SCORE BY SET`**: a `Set N` row per set with two numeric inputs (`[0] – [0]`) and, when
  more than one set exists, a remove (✕) control. **`+ Add set`** appends another row.
- **`WHO WON?`**: two selectable cards (`Team 1` / `Team 2`) showing each team's names (or
  `? & ?` for unfilled slots); the winner is auto-highlighted, tap to override.
- **`Record Match`** (edit mode: `Save changes`) — full-width, disabled/dim until valid.

## Behavior / Requirements
1. **Roster** = `GET /groups/{groupId}/members` → real members **+** guests. The player picker
   is a bottom sheet opened from an empty slot; picking assigns to that slot's team. Tapping a
   filled slot removes the player. A player can't be assigned twice.
2. **Guests collapse** to a **single** "Guest" entry in the picker (the first still-unassigned
   guest); consuming it advances to the next, and the entry disappears once all guests are used.
   Guests are marked with a `GUEST` tag.
3. **Sets**: inputs accept up to 2 digits each; `+ Add set` appends, ✕ removes (never below 1).
4. **Winner** is auto-derived from sets won (`autoWinner`), and a manual tap **overrides** it.
5. **Validation** — `Record Match` enabled only when: both teams have 2 players, **every** set
   is two non-negative integers with no tie, and a winner is determined.
6. **Submit** builds `RecordMatchRequest` `{ playedAt, teams:[{teamNo,playerIds}], sets:
   [{setNo,team1Score,team2Score}], winningTeamNo }`. Create → `POST .../matches` with
   `playedAt = now`; **edit** → `PATCH .../matches/{id}` round-tripping the **original**
   `playedAt` (the endpoint overwrites it, so losing it would re-date the match). On success,
   invalidate Board + Matches (+ Stats) + the user's form; new → route to **Board**, edit →
   **Matches**. On failure, keep the form and show a message (maps `MATCH_INVALID_TEAMS` /
   `MATCH_INVALID_SCORES` / `MATCH_EDIT_FORBIDDEN`).
7. **Edit mode** is entered when the Matches "Edit" action routes here with `{ editMatchId }` in
   navigation state; the form pre-fills from `GET .../matches/{id}` (teams, sorted sets, winner).

## Data needed
- `GET /groups/{groupId}/members` → `{ members: MemberDto[], guests: MemberDto[] }`.
- `GET /groups/{groupId}/matches/{matchId}` (edit prefill).
- `POST /groups/{groupId}/matches` · `PATCH /groups/{groupId}/matches/{matchId}` — both take
  `RecordMatchRequest`.

## Current rules (settled)
- **Team size is 2** (badminton doubles); no sport picker.
- **Non-participant recording is allowed** — the recorder need not be one of the four players.
- **Guests** have `role: "guest"`; they never reach the leaderboard or stats.

## Parity notes (browser)
- Android's `PlayerPickerSheet` (ModalBottomSheet) → a fixed bottom-sheet modal; backdrop click
  or ✕ dismisses it. Score fields use `inputMode="numeric"` (digit-filtered) rather than a
  native number spinner, so partial entry stays representable.

## Open questions
- Badminton scoring rules (win-by-2, cap at 30) are enforced server-side, not in the form — the
  client only blocks ties and blanks; a `MATCH_INVALID_SCORES` surfaces the rest.
