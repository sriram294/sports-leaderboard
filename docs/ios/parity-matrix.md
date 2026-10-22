# iOS parity matrix

Legend: **Y** shipped, **P** planned in named slice, **—** not applicable. Android and PWA are current reference implementations; iOS entries describe roadmap ownership, not premature completion.

| Feature / state | Android | PWA | iOS coverage | Loading | Empty | Failure | Permission | Accessibility | Tests | Screenshots |
|---|---:|---:|---|---|---|---|---|---|---|---|
| Design foundation | Y | Y | S00 | P:S00 | P:S00 | P:S00 | — | P:S00 | P:S00 | P:S00 light/dark gallery |
| Google sign-in | Y | Y | P:S01 | P:S01 | — | P:S01 | P:S01 | P:S01 | P:S01 | P:S01 |
| Sign in with Apple | — | — | P:S01 | P:S01 | — | P:S01 | P:S01 | P:S01 | P:S01 | P:S01 |
| Session refresh/logout | Y | Y | P:S01 | P:S01 | — | P:S01 | — | P:S01 | P:S01 | P:S01 |
| Groups and member roles | Y | Y | P:S02 | P:S02 | P:S02 | P:S02 | P:S02 | P:S02 | P:S02 | P:S02 |
| App shell/navigation | Y | Y | P:S02 | P:S02 | — | P:S02 | — | P:S02 | P:S02 | P:S02 |
| Board — This Month | Y | Y | P:S03 | P:S03 | P:S03 | P:S03 | — | P:S03 | P:S03 | P:S03 |
| Board — Month snapshots | Y | Y | P:S09 | P:S09 | P:S09 | P:S09 | — | P:S09 | P:S09 | P:S09 |
| Team-v2 cumulative Skill rating | Y | Y | P:S09 | P:S09 | P:S09 | P:S09 | — | P:S09 | P:S09 | P:S09 |
| Match history/detail | Y | Y | P:S04 | P:S04 | P:S04 | P:S04 | — | P:S04 | P:S04 | P:S04 |
| Record match | Y | Y | P:S04 | P:S04 | P:S04 | P:S04 | — | P:S04 | P:S04 | P:S04 |
| Profile/avatar/settings | Y | Y | P:S05 | P:S05 | P:S05 | P:S05 | P:S05 photos | P:S05 | P:S05 | P:S05 |
| Statistics | Y | Y | P:S05 | P:S05 | P:S05 | P:S05 | — | P:S05 | P:S05 | P:S05 |
| Share leaderboard image | Y | Y | P:S05 | P:S05 | P:S05 | P:S05 | P:S05 share sheet | P:S05 | P:S05 | P:S05 |
| Push notifications | Y | limited | P:S06 | P:S06 | P:S06 | P:S06 | P:S06 notifications | P:S06 | P:S06 | P:S06 |
| App update prompt | Y | — | P:S06 | P:S06 | P:S06 | P:S06 | — | P:S06 | P:S06 | P:S06 |
| Delete account | Y | Y | P:S07 | P:S07 | — | P:S07 | P:S07 re-auth | P:S07 | P:S07 | P:S07 |
| Release/performance/privacy | Y | Y | P:S08 | P:S08 | P:S08 | P:S08 | P:S08 | P:S08 | P:S08 | P:S08 |

Every feature slice must explicitly exercise loading, empty, failure, permission, accessibility, tests, and screenshots where the cell is not `—`. Update cells to **Y** only with evidence in `roadmap.md`.

## Android parity pass (2026-10-22, branch `feature/ios-parity-gaps`)

Compared against the Android app on `master` (`41f7a1c`). Implemented on iOS in this pass, all
pending a first macOS/Xcode build through the manual **iOS build** workflow
([`.github/workflows/ios.yml`](../../.github/workflows/ios.yml)):

| Feature | Android | iOS before | iOS now |
|---|---|---|---|
| Hidden store-review login (long-press wordmark) | Y | missing | `LoginScreen` reviewer form → `POST /auth/reviewer` |
| Edit a match (full replace, original time kept) | Y | missing | Matches → Edit → Add tab in edit mode → `PATCH /matches/{id}` |
| Delete a match (recorder or owner/admin) | Y | missing | Matches → Delete with confirmation → `DELETE /matches/{id}` |
| Newest match day expanded, older days collapsed | Y | missing | `MatchesViewModel.isDayExpanded` |
| Leaderboard player drill-down | Y | missing | podium/row tap → player profile sheet |
| Share leaderboard image | Y | missing | `LeaderboardShareRenderer` (top 10 ranked) → share sheet |
| Profile hero, stat tiles, recent matches | Y | partial | `ProfileScreen` |
| Default avatar picker and photo upload | Y | missing | `PATCH /users/me/avatar`, multipart `POST /users/me/photo` (center-cropped) |
| Rename sheet | Y | inline field | `EditNameSheet`; session user updated and persisted |
| Monthly finishes chart | Y | list | Swift Charts, gaps break the line |
| Attendance heatmap (3 months) | Y | missing | `AttendanceGrid` + `GET /attendance` |
| Months-won trophy shelf | Y | missing | `PlayerStats.trophies` |
| Settings (account, theme, version) | Y | inside Profile | `SettingsScreen` sheet; dark default, light, or system |
| Stats: month/all-time, records, biggest win, monthly winners, partner picker | Y | minimal | `StatsViewModel` / `StatsScreen` |
| Push registration | Y | missing | `PushNotificationService` (FCM; inert without `GoogleService-Info.plist`) + `POST/DELETE /devices` |
| Update prompt | Y | repository only | banner when `/app/update?platform=ios` advertises a newer build |

Also fixed: compile errors (optional base URL passed to non-optional repositories, async calls in
`??` autoclosures, an unstored `deleteAccount` in the shell, a private colour initialiser used from
another file), a logout request that sent the literal `Bearer (session.accessToken)`, and API
timestamps with fractional seconds that `.iso8601` decoding rejected.
