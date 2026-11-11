# Task 4 — Move the approved native UI into `feature:assist`

Base: `0f8d1da4c0e7d5a6c973a00b1a3b5c7bd36f7caf`

Read Task 4 in `docs/superpowers/plans/2026-11-11-approved-ui-integration.md` and the approved design/addenda. Scope is the approved UI migration: `feature/assist/**`, only required version-catalog/build changes, package-verifier paths under `web/tests/tools` as needed, and deletion of duplicate `android/**` and `release-artifacts/**` only after successful migration.

Copy approved tests first and capture the red result. Move the delivery at `03b2486` without redesign: preserve package `dev.fuelpilot.ui`, exact atlas pixels/coordinates, five clips/72 frames/timing, Korean copy, reducer behavior, reduced-motion rules, permanent character presence, and compatibility pose resources. Add only required Compose/test dependencies; do not retain the nested Gradle build. Update verification to compare web assets with `feature/assist`.

Required green evidence: 11 native UI tests, `:feature:assist:assembleDebug`, 17 Node tests, and package verifier confirming matching atlases/5 clips/72 frames. If Android is blocked by the known shared Gradle cache move failure, diagnose safely and retry with an isolated project-local Gradle user home only if dependencies are already resolvable; do not claim a pass without output and do not download without approval. Commit with `feat: move approved UI into assist module`. Do not push or use subagents. Write `.superpowers/sdd/2026-11-11-approved-ui-integration/task-4-report.md` with status, red/green evidence, files, self-review, and commit hash; force-add only that report if ignored.
