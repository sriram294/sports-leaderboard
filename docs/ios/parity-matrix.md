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
| Board — All Time | Y | Y | P:S03 | P:S03 | P:S03 | P:S03 | — | P:S03 | P:S03 | P:S03 |
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
