# SNN — Slice name

## Outcome

State the user-visible capability delivered by this slice.

## Entry criteria

- Previous slice merged and its PR/merge evidence is available.
- First commit records that evidence, marks the previous slice `done`, and makes this the only `in_progress` slice.
- Branch was created from refreshed `master` and is recorded in `roadmap.md` and `memory.md`.

## Scope

List exact application, backend, documentation, migration, and CI changes permitted.

## Public contracts

Record endpoint, DTO, persistence, navigation, notification, and platform contracts. Say “none” when unchanged.

## UX and state contract

Specify content plus loading, empty, failure, permission, offline, accessibility, Dynamic Type, light/dark, and native interaction behavior.

## Architecture and tests

Name Screen, ViewModel, immutable State, Repository protocol/implementation boundaries, injected fakes, unit/UI/integration tests, and fixtures.

## Exclusions

Name work intentionally reserved for later slices.

## Verification evidence

- Exact repeatable commands.
- Xcode version and simulator.
- `.xcresult`, JUnit, screenshot, log, archive, and other durable artifact paths.
- Repository secret/signing scan when applicable.

## Exit criteria

List objective PR-handoff conditions. Keep this slice `in_progress` until the next transition commit (or the post-S08 closure PR).

## Next transition

Name the next branch and the evidence its first commit must record.
