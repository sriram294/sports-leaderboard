# iOS decisions

Append new entries; never renumber or rewrite accepted decisions. Supersede an entry by adding another decision that names it.

| ID | Date | Decision | Rationale |
|---|---|---|---|
| IOS-DEC-001 | 2026-08-30 | Use Swift 6 language mode with Xcode 26.2, SwiftUI, iOS 17+, iPhone portrait only. | Establishes one modern, testable native baseline. |
| IOS-DEC-002 | 2026-08-30 | Use bundle ID `com.org.playboard`; derive test bundle IDs from it. | Matches the established product identity. |
| IOS-DEC-003 | 2026-08-30 | Use native MVVM/Repository boundaries and initializer injection from one `AppEnvironment`; add no DI framework or project generator. | Keeps composition explicit and previews/tests deterministic. |
| IOS-DEC-004 | 2026-08-30 | Target branded feature parity while following native iOS behavior and accessibility conventions. | Parity describes capability and identity, not pixel imitation. |
| IOS-DEC-005 | 2026-08-30 | Leaderboard ranges are exactly `This Month` and `All Time`. | Preserves the existing server and product contract. |
| IOS-DEC-006 | 2026-08-30 | Deleted identities may later sign in to create fresh, unrelated accounts. | Deletion revokes and unlinks history without permanently banning an identity. |
| IOS-DEC-007 | 2026-08-30 | Codemagic initially creates unsigned archives and no IPA or store publication. | Allows deterministic CI before signing authority exists. |
| IOS-DEC-008 | 2026-08-30 | Add Google Sign-In `9.2.0..<10.0.0` and Firebase Apple SDK `12.18.0..<13.0.0` with only `GoogleSignIn` and `FirebaseMessaging`; commit `Package.resolved`. | Uses the locked major lines and the selected Xcode-26.2-supported Firebase 12.x release without initializing providers in S00. |
| IOS-DEC-009 | 2026-08-30 | A next-slice transition commit closes the prior slice; a documentation-only closure PR closes S08. | Makes merge evidence part of the authoritative history. |
| IOS-DEC-010 | 2026-08-30 | The active branch is authoritative and only one slice may be `in_progress`. | Prevents competing delivery state. |
| IOS-DEC-011 | 2026-08-30 | Register upstream Manrope as its variable `wght` font on iOS, alongside Paytone One. | iOS 17 can select the 200–800 named weight instances from one registration and avoids the duplicate PostScript names exposed by Android's separately-instanced font files. |
