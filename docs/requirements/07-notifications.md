# 07 — Push notifications

Extends the existing FCM pipeline (PR #31–#36) from three ad-hoc pushes into a governed
system. This document covers the **initial release**: two notifications plus the
foundation they need.

## Goals

- Close out a play session with something worth opening the app for.
- Tell people about roster/admin changes that affect them.
- **Do that without getting the app muted.** Volume control is a first-class
  requirement, not a polish pass (see [Volume budget](#volume-budget)).

## Scope

Two notifications ship first:

| # | Notification | Recipients | Trigger |
|---|---|---|---|
| 1 | **Daily rank change** — "You moved up 2 places" | Everyone whose leaderboard position changed | Scheduled (session end) |
| 2 | **Promoted to admin** | The promoted member | Event (role change) |

`MATCH_ACTIVITY` already exists and is retrofitted into the taxonomy so it obeys the same
channel rules.

**Deferred:** personal/member achievements, weekly group digest, court call, and the
per-user notification-preferences table, API and Profile UI. Channels give users a mute
escape hatch in system settings in the meantime, which is why prefs could wait.

---

## Categories

Each category maps 1:1 to an **Android notification channel**, so a user can mute the daily
recap without silencing match pushes. Before this, everything posted to one
`match_activity` channel — muting the recap would have muted *everything*, which is exactly
how an app loses its notification permission for good.

| Category | Channel id | About |
|---|---|---|
| Match activity | `match_activity` | A match was logged or edited *(exists; id kept so installs keep their setting)* |
| Daily summary | `daily_summary` | End-of-session leaderboard movement |
| Group update | `group_updates` | Roster and admin changes |

The channel id is set **server-side** on every push via `AndroidConfig`. Without it, a push
arriving while the app is backgrounded is rendered by the system using the manifest's
default channel — which would silently put every category back on one channel.

---

## Category specs

### A. Daily rank change

**When a session ends.** There is no reliable "session ended" signal in the data model:
`groups.session_start`/`session_end` (V9) are static wall-clock config with no timezone,
never joined against matches. So the trigger is **inactivity-based**:

- A job runs every 15 minutes over groups with a match in the last 24h.
- A session is over when no match has been logged for **90 minutes**.
- `sessionStart` = `played_at` of the first match in that contiguous block, found by
  walking back while consecutive gaps stay under the quiet period.

This needs no timezone, adapts to sessions that run long or end early, and works for groups
that never configured a window.

**Computing the change.** `member_stats` is a running aggregate that is overwritten on every
match write, so there is no stored history to diff against. Both sides are computed from
raw matches with the same query:

```
before = rankedStandings(groupId, from = EPOCH, to = sessionStart)
after  = rankedStandings(groupId, from = EPOCH, to = now)
```

`rankedStandings` is extracted from the existing windowed-leaderboard path
(`StatsQueryService`), so it inherits the canonical order — **win rate desc → points-diff
desc → wins desc → userId asc** — and the guest/inactive-member exclusion. Deliberately
*not* diffed against the `member_stats` ranking: that path reads a DB-generated `win_rate`
while the aggregate path computes `BigDecimal` to 4 dp, and mixing the two could manufacture
a rank change that didn't happen.

| Case | Copy |
|---|---|
| Moved up | "You moved up 2 places — you're #3 in Smashers." |
| Moved down | "You slipped 1 place to #5 in Smashers." |
| First time ranked | "You're on the board at #6 in Smashers." |
| No change | *nothing sent* |

Recipients are **everyone whose position changed**, not just those who played — being
overtaken while sitting out is exactly the thing worth knowing.

Known edge case: a **backdated** match is attributed to the session containing its
`played_at`, not tonight's. Acceptable — these notifications celebrate live play, not
bookkeeping.

### B. Promoted to admin

Published from `GroupService.changeMemberRole`, which is owner-only. Only promotions are
announced — a demotion is a quiet administrative act, and saying nothing beats sending
someone a push about losing admin.

| Event | Recipients | Copy |
|---|---|---|
| Promoted to admin | The promoted user | "You're now an admin of Smashers." |

---

## Cross-cutting infrastructure

### Notification log

`notification_log(user_id, category, dedupe_key, ...)` with a **unique constraint on
(user_id, category, dedupe_key)**. Two jobs:

- **Idempotency** — dedupe keys like `rank_change:<groupId>:<sessionStart>`. Claim-then-send:
  a Railway redeploy mid-job, or a second instance, can't double-send because the DB rejects
  the duplicate first. Required, not polish — a job on a 15-minute tick would otherwise
  re-send every tick.
- **Audit** — "why did I get this?" is otherwise unanswerable.

### Scheduling

No `@EnableScheduling` existed anywhere in the backend. A `SchedulingConfig` plus one job
covers the session scan. Idempotency comes from the log's unique constraint rather than
ShedLock, which keeps this safe on two instances without adding a dependency.

### Android

- Three channels in `NotificationChannels`, created at app start.
- **Small-icon fix**: `setSmallIcon` used `R.drawable.app_icon_large`, a full-colour PNG.
  Small icons are drawn from the alpha channel only, so it rendered as a white blob. Now uses
  the monochrome `ic_stat_name`, and the manifest declares
  `default_notification_icon` so system-rendered background pushes match.
- **Group deep-link**: tapping a push switches the active group to the one it refers to, so a
  push about a group you aren't viewing doesn't drop you on another group's board. Read from
  intent extras, which covers both delivery paths — FCM copies the data payload onto the
  launch intent for background pushes. Routing to a specific *tab* is not implemented; the
  app opens on Board by default.

---

## Volume budget

**Today: 8-player group, 10 matches in one evening → 7 pushes/match → ~70 pushes.** That is
already past the point of annoyance, and the daily recap adds at most one more per member
per session on top.

Mitigations in this release:

1. Rank change sends **nothing** when a position didn't move.
2. One push per member per session, guaranteed by the dedupe key.
3. **Recommended follow-up:** collapse `MATCH_ACTIVITY` into a per-session digest
   ("6 matches logged in Smashers tonight") instead of one push per match. Out of scope
   here, and the single biggest remaining win.

---

## Delivery plan

Two PRs against `master`, foundation first — shipping triggers before governance is how you
train users to mute you.

| # | Slice | Contents |
|---|---|---|
| **A** | **Foundation + promote** | `NotificationCategory`; `notification_log` migration + claim guard; category-aware `sendToUsers` with server-set channel id; three channels; small-icon fix; group deep-link; promoted-to-admin event + listener. Retrofits the existing 3 pushes. |
| **B** | **Daily rank change** | `SchedulingConfig`; `rankedStandings` extraction; session-gap detection; rank diff + send. Backend-only. |

Per repo workflow: update `docs/backend/api-contracts.md` and `docs/backend/data-model.md`
alongside; unit tests + `:app:testDebugUnitTest` + `:app:assembleDebug` before each PR.

## Open decisions

1. **Late-night sessions** — a session ending at 23:00 pushes at 00:30. Suppressing that
   needs a timezone, which this design otherwise avoids entirely. Recommendation: **send
   anyway** for the first release; revisit if it actually annoys anyone.
2. **Quiet period length** — 90 minutes is a guess at the gap between "still playing" and
   "packed up". Worth checking against real match timestamps in prod.
3. **Per-session match digest** (volume budget #3) — the real fix for fatigue. Next?

## Risks

- **Notification fatigue** → permission revoked wholesale. Mitigated by per-category
  channels and by suppressing no-change recaps; decision (3) is the real fix.
- **Multi-instance double-send** — covered by the log's unique constraint, not by luck.
- **Long-running session spanning midnight** — treated as one session, which is correct
  under gap-based detection even though "daily" suggests otherwise.
