# FUEL PILOT cross-PC handoff — 2026-11-11

This file is the entry point for continuing the current integration on another PC.

## Get the exact checkpoint

```powershell
git clone https://github.com/kako-labs/fuel-pilot.git
cd fuel-pilot
git switch --track origin/integrate/podong-approved
git log -1 --oneline
python scripts/dev.py doctor
```

Do not start from `main` or from the older `podong-ui-approved-2026-11-10` tag. That tag remains the immutable approved UI baseline; the current application integration continues on `integrate/podong-approved`.

## Completed and reviewed

Tasks 1–5 of `docs/superpowers/plans/2026-11-11-approved-ui-integration.md` are complete on this branch.

- Imported the approved 16-frame expressive Podong UI baseline and preserved the fixed clip viewports and frame timings across web and Android.
- Extended the pure deterministic fuel decision engine with last-safe passing context.
- Added real-data port contracts whose unavailable implementations return `Unsupported`; no synthetic data is substituted in real builds.
- Moved the approved Compose UI, atlas resources, animation behavior, event deduplication, moving/unknown cancellation, and reduced-motion behavior into `feature/assist`.
- Added the pure decision-to-UI mapper and stabilized recommendation keys across passing states.
- Task 5 was independently reviewed with no findings. Its parent test run covered 18 Android tests with zero failures.

Completed checkpoint commits through Task 5:

```text
4152c33 docs: record task 5 verification
58da78a fix: keep recommendation key stable across passing
a274498 feat: map fuel decisions to approved UI
b222bae chore: finalize approved UI migration
7821dc6 feat: move approved UI into assist module
97c776f docs: record task 3 integration report
d9e42b6 feat: define unsupported data ports
1da387e docs: record task 2 core report
131a2a9 feat: expose last-safe passing context
```

## Current WIP checkpoint: Task 6

The checkpoint commit following this document intentionally contains unfinished Task 6 work so it cannot be lost between PCs.

Implemented so far:

- `AssistContract.kt`: one-way intents/effects and pure screen-priority reducer.
- `AssistViewModel.kt`: serialized intent handling, restore-before-events gate, request-ID-correlated navigation effects, and durable exposure/refuel writes.
- `AssistStateStore.kt` and `PreferencesAssistStateStore.kt`: Android DataStore-backed durable state with 90-day processed-ID retention and a 256-ID cap.
- `FuelPilotUi.kt`: emits intents rather than directly invoking navigation callbacks.
- Coordinator and persistence tests, including recreation, duplicate-event, cancellation, and ordering cases.

This work is **not yet complete or approved**.

Known verification state:

- Web `npm.cmd test` and `npm.cmd run verify` were run during Task 6 and completed without a reported product failure.
- The first Android Task 6 execution reached 29 tests: all 18 ViewModel tests and all 7 mapper tests passed; 4 DataStore filesystem tests failed because the Windows test filesystem could not rename its temporary file.
- Those four tests were changed to exercise real DataStore/serialization over Okio `FakeFileSystem` rather than replacing the store with a fake.
- Two additional edge tests were added: restored state must survive an initial `Motion.Unknown`, and an invalidated buffered waypoint effect must not reach a late collector.
- A final Android GREEN run has **not** been obtained. Subsequent attempts were blocked by the known Windows Gradle immutable-transform move/rename problem in the isolated cache. Do not report Task 6 as passing until a fresh full run succeeds.

## Resume order

1. Read `AGENTS.md`, `START-HERE.md`, `docs/APPROVED-BASELINE.md`, `docs/APP-SESSION-HANDOFF.md`, and `docs/ANDROID-INTEGRATION.md`.
2. Inspect the WIP diff and finish Task 6 with the two edge tests above using TDD.
3. Run the required verification and record exact results:

   ```powershell
   npm.cmd test
   npm.cmd run verify
   .\gradlew.bat :feature:assist:testDebugUnitTest :feature:assist:assembleDebug --no-daemon --max-workers=1 --console=plain
   python scripts/dev.py doctor
   python scripts/dev.py verify
   ```

4. Independently review Task 6 before declaring it complete.
5. Task 7: wire the host app/container to the ViewModel, decision engine, real ports, and navigation result callbacks. Default real adapters must remain `Unsupported` until the Pleos SDK/permissions exist.
6. Task 8: whole-branch verification, documentation update, review, and final push/hash check.
7. Record host-app integration and actual Pleos-device verification separately. Neither has been proven yet.

## Non-negotiable product constraints

- Keep the decision engine pure and deterministic.
- Never substitute demo/synthetic data in a real build.
- Do not add an in-app map; the vehicle navigation app owns map rendering.
- Preserve the approved Podong identity, broad parked acting, calm driving avatar, one-shot production refuel gesture, cancellation/deduplication, and reduced-motion behavior.
- Keep the web and Android atlas, timings, and fixed per-clip viewports synchronized.
- Do not commit API keys, SDK binaries, `local.properties`, caches, or machine-specific paths.

## GitHub handoff proof

The source of truth is the remote branch `origin/integrate/podong-approved`. On the new PC, verify that `git rev-parse HEAD` matches `git rev-parse origin/integrate/podong-approved` after fetching. The final commit hash for this checkpoint is also reported in the session that pushed it.
