# Playboard iOS delivery control

This directory is the authoritative operating record for the native iOS client. It contains no secrets or personal data.

## Reading order

1. [`roadmap.md`](roadmap.md) — slice status, dependencies, branches, merge evidence, and acceptance evidence.
2. [`memory.md`](memory.md) — the compact hand-off for the currently active slice.
3. [`decisions.md`](decisions.md) — append-only architectural and product decisions.
4. [`parity-matrix.md`](parity-matrix.md) — Android/PWA/iOS feature and state coverage.
5. The active contract under [`slices/`](slices/).

## Architecture

The iOS app uses Swift 6, SwiftUI, and native MVVM/Repository boundaries on iOS 17+. Feature code lives under `ios/Playboard/Features/<feature>/` as Screen, ViewModel, immutable State, and Repository protocol groupings. Shared API, model, storage, and design code lives under `Core/`. `AppEnvironment` is the single production composition root. Tests and previews use initializer-injected deterministic in-memory implementations. There is no DI framework and no project generator.

The product targets iPhone portrait only. Its bundle identifier is `com.org.playboard`; test targets derive identifiers from it. Visual behavior is branded parity with native iOS interaction and accessibility conventions.

## Commands

Run these from `ios/` using Xcode 26.2:

```bash
./scripts/resolve-packages.sh
./scripts/build.sh
./scripts/test.sh
./scripts/archive-unsigned.sh
./scripts/verify-repository.sh
```

`test.sh` writes `.xcresult`, JUnit, and light/dark Design Gallery screenshots beneath `ios/build/`. `archive-unsigned.sh` creates an unsigned `.xcarchive`; it never exports an IPA.

## Operating protocol

- Exactly one slice may be `in_progress`; all unfinished peers are `not_started`.
- During a slice, its feature branch is authoritative. Do not try to repair its state from an older `master`.
- Do not mark a slice `done` in its own implementation PR. The next slice's first commit records the previous PR and merge commit, marks the previous slice `done`, and starts the next slice.
- After S08 merges, one documentation-only closure PR records its merge and marks it `done`.
- Update `memory.md` at every meaningful hand-off. Record commands and artifact paths, not claims without evidence.
- Never commit credentials, tokens, signing material, personal data, `GoogleService-Info.plist`, OAuth configuration, or APNs material.
- Preserve unrelated user files and stage paths explicitly.
