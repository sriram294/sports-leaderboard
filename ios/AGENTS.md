# iOS Working Agreement

## Resume protocol

1. Read `../docs/ios/README.md`, then `roadmap.md`, `memory.md`, `decisions.md`, and the active slice contract.
2. Confirm exactly one slice is `in_progress` and the checked-out branch matches it. During a slice, the active branch is authoritative even when `master` is older.
3. Confirm the base commit and preserve every unrelated tracked or untracked user file.
4. Run the verification commands recorded in `memory.md` before changing known-good behavior.
5. Make only changes allowed by the active slice. Update its evidence and `memory.md` as work advances.

Never put credentials, tokens, OAuth client configuration, `GoogleService-Info.plist`, APNs keys, provisioning profiles, signing certificates, or personal data in this tree.

## Architecture

Use Swift 6 and SwiftUI with immutable view state. Views render state and send actions; `@MainActor` view models own presentation logic; repositories own data access. Compose production dependencies once in `AppEnvironment`, and inject deterministic in-memory implementations into tests and previews. Do not add a DI framework or project generator.

Keep shared types in `Playboard/Core/` and feature code in `Playboard/Features/<feature>/`. Public declarations require concise documentation. UI must support Dynamic Type, VoiceOver, light/dark appearance, minimum 44-point targets, and state indicators that do not rely on color alone.

## Slice transitions

Do not mark the active slice `done` on its own branch. After its PR merges, refresh `master`, create the next slice branch, and make a first documentation transition commit that records the PR and merge commit, marks the prior slice `done`, and marks the new slice `in_progress`. S08 is followed by one documentation-only closure PR.
