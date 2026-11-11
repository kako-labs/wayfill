# Approved UI Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Merge the approved Podong UI into the existing FUEL PILOT Android architecture and connect it to deterministic decision, navigation, and persistence contracts without shipping demo data.

**Architecture:** Preserve the approved web/UI history, move the native UI into the existing `:feature:assist` module, and keep `:core:model` pure. Extend the domain result so presentation never recomputes safety, introduce explicit Unsupported data ports, then connect everything through a request-ID-based ViewModel and persistent event-deduplication store.

**Tech Stack:** Kotlin 2.1.20, Android/Compose BOM 2025.04.01, AGP 8.10.1, coroutines/StateFlow, AndroidX Preferences DataStore, JUnit 4, Node test runner, Gradle 8.12, Python unittest.

**Spec:** `docs/superpowers/specs/2026-11-11-approved-ui-integration-design.md` and `docs/superpowers/specs/2026-11-11-approved-ui-integration-review-addendum.md`

## Global Constraints

- Work only on feature/integration branches; never commit directly to `main`.
- Keep `FuelDecisionEngine` pure, deterministic, and free of Android/UI dependencies.
- Preserve approved commit `95d0877`, delivery commit `03b2486`, and tag `podong-ui-approved-2026-11-10` as provenance.
- Preserve the approved 72-frame animation, permanent character presence, moving/unknown cancellation, and reduced-motion behavior.
- Never ship demo distance, fuel, station, location, or navigation success data in the Android execution path.
- Missing SDKs, permissions, and signals return explicit `Unsupported`, `Unavailable`, or `Stale` values.
- Do not add an in-app map; `FuelPilotUi.mapContent` remains host-supplied and empty until an approved host surface exists.
- Navigation success is accepted only for the currently pending request ID and station ID.
- Restore processed refuel IDs and preferences before subscribing to live vehicle/refuel events; never restore a celebration across process restart.
- Before handoff run project verification, push the integration branch, and compare local HEAD with the live remote hash.

## Review Focus

- An unsafe station after the last safe stop must be available for `Passing` copy without being promoted to a safe recommendation; Task 2 pins this in engine tests.
- Two rapid waypoint taps and an old callback from a prior retry must cause one current request and no false success; Task 6 pins this in ViewModel tests.
- `Stale`, `Unavailable`, and `Unsupported` must remain distinguishable and must not invent numbers; Tasks 3 and 5 pin this in contract/mapper tests.
- Process restart must retain refuel deduplication while dropping active celebration timing; Task 6 pins this with a real Preferences DataStore round trip.
- Moving or unknown vehicle state must cancel large motion and detailed surfaces immediately while retaining a calm character; Tasks 4 and 6 pin this in reducer/ViewModel tests.

---

## Execution Waves

- Wave 0: Task 1 on the integration branch.
- Wave 1 in separate Orca worktrees: Tasks 2, 3, and 4 in parallel; they own disjoint paths.
- Wave 2: Task 5 after Tasks 2 and 4; Task 6 after Tasks 3–5.
- Wave 3: Tasks 7 and 8 on the integration branch.
- Each worker commits only its owned files. The integrator reviews and cherry-picks one commit at a time, rerunning the task verification after every pick.

### Task 1: Preserve the approved histories and resolve the merge boundary

**Files:**
- Modify: `.gitignore`
- Modify: `AGENTS.md`
- Modify: `README.md`
- Add from UI branch: `web/**`, `tests/**`, `tools/**`, `reference/**`, UI handoff documents and assets
- Preserve temporarily from UI branch: `android/**`, `release-artifacts/**`

**Interfaces:**
- Consumes: code baseline `1ab7659`, remote UI delivery `03b2486`
- Produces: one merged integration commit containing both histories and an unchanged approved UI baseline

- [ ] **Step 1: Commit the approved design package and this plan**

Run: `git add docs/superpowers/specs/2026-11-11-approved-ui-integration-design.md docs/superpowers/specs/2026-11-11-approved-ui-integration-review-addendum.md docs/superpowers/plans/2026-11-11-approved-ui-integration.md && git commit -m "docs: define approved UI integration"`

Expected: one documentation commit on `integrate/podong-approved`.

- [ ] **Step 2: Merge the approved UI delivery history**

Run: `git merge --no-ff origin/ui/podong-delivery-20261110`

Expected: conflicts only in `.gitignore`, `AGENTS.md`, and `README.md`.

- [ ] **Step 3: Resolve the three conflicts by combining intent**

`.gitignore` is the union. `AGENTS.md` keeps both product architecture rules and approved UI rules. `README.md` leads with the product and links the approved demo/handoff below it.

- [ ] **Step 4: Verify the imported UI baseline**

Run: `npm.cmd test && npm.cmd run verify`

Expected: 17 Node tests pass and package verification reports matching native/web atlas, 5 clips, and 72 frames.

- [ ] **Step 5: Finish the merge commit**

Run: `git add .gitignore AGENTS.md README.md && git commit`

Expected: a merge commit with both parents and no unmerged paths.

### Task 2: Extend the pure decision result for passing-stage presentation

**Files:**
- Modify: `core/model/src/main/kotlin/dev/fuelpilot/model/FuelModels.kt`
- Modify: `core/model/src/main/kotlin/dev/fuelpilot/model/FuelDecisionEngine.kt`
- Modify: `core/model/src/main/kotlin/dev/fuelpilot/model/RecommendationReason.kt`
- Modify: `core/model/src/test/kotlin/dev/fuelpilot/model/FuelDecisionEngineTest.kt`
- Modify: `core/model/src/test/kotlin/dev/fuelpilot/model/RecommendationReasonTest.kt`

**Interfaces:**
- Consumes: existing `FuelDecisionInput`, route station candidates, and `AlertPolicy`
- Produces: `RecommendationStage { RECOMMEND, PASSING }`; `FuelDecision.Recommendation.nextAfterPrimary: FuelStationCandidate?`; `rangeAfterNextKm: Double?`; `stage: RecommendationStage`

- [ ] **Step 1: Write failing engine tests**

Add tests named `recommendationIncludesFirstRouteCandidateAfterLastSafeStop`, `unsafeNextCandidateIsNeverPromotedToPrimary`, `passingStageStartsOnlyInsidePassingThreshold`, and `unsupportedSignalsReturnMatchingUnavailableReasons`. Assert that the unsafe next candidate is present only in `nextAfterPrimary`, its negative remaining range is preserved, and no UI type appears in core.

- [ ] **Step 2: Run the focused tests and confirm failure**

Run: `.\gradlew.bat :core:model:test --tests "dev.fuelpilot.model.FuelDecisionEngineTest" --no-daemon --max-workers=1`

Expected: FAIL because the new result fields/stage do not exist.

- [ ] **Step 3: Add exact domain fields**

Add `SignalStatus.UNSUPPORTED`; matching `UnavailableReason` values for energy, location, and route; `AlertPolicy.passingDistanceKm: Double = 5.0` and `passingMinutes: Int = 5`; and the three recommendation fields listed in Interfaces.

- [ ] **Step 4: Extend route evaluation without presentation logic**

Select the first compatible, non-closed route candidate after `primary` as `nextAfterPrimary`, even when it is unsafe. Calculate `rangeAfterNextKm` from the evaluated range and route distance. Set `PASSING` only when primary distance or ETA is within the passing threshold.

- [ ] **Step 5: Fix and pin Korean recommendation text**

Replace any mojibake in `RecommendationReason.kt` with UTF-8 Korean copy and assert exact readable strings in `RecommendationReasonTest.kt` for last-safe, nearest, detour, and stale business information cases.

- [ ] **Step 6: Run core tests**

Run: `.\gradlew.bat :core:model:test --no-daemon --max-workers=1`

Expected: all core model tests pass.

- [ ] **Step 7: Commit**

Run: `git add core/model && git commit -m "feat: expose last-safe passing context"`

### Task 3: Add explicit data and navigation ports with Unsupported defaults

**Files:**
- Create: `data/energy/src/main/kotlin/dev/fuelpilot/data/energy/EnergyRepository.kt`
- Create: `data/location/src/main/kotlin/dev/fuelpilot/data/location/LocationRepository.kt`
- Create: `data/poi/src/main/kotlin/dev/fuelpilot/data/poi/StationRepository.kt`
- Create: `data/navigation/src/main/kotlin/dev/fuelpilot/data/navigation/NavigationRepository.kt`
- Create: matching `src/test/kotlin/**/**RepositoryTest.kt` files

**Interfaces:**
- Consumes: core route/station model types where appropriate
- Produces: repository interfaces and default implementations that return typed `Unsupported`; `WaypointRequest(requestId: String, stationId: String)`; `WaypointResult`

- [ ] **Step 1: Write failing contract tests**

Each module gets a test asserting its default repository returns `Unsupported` and never a placeholder numeric value. Navigation tests additionally assert that request ID and station ID round-trip in every result.

- [ ] **Step 2: Run data module tests and confirm failure**

Run: `.\gradlew.bat :data:energy:testDebugUnitTest :data:location:testDebugUnitTest :data:poi:testDebugUnitTest :data:navigation:testDebugUnitTest --no-daemon --max-workers=1`

Expected: FAIL because the port types do not exist.

- [ ] **Step 3: Implement the four minimal contracts**

Use sealed results with distinct `Available`, `Stale`, `Unavailable`, and `Unsupported` cases. Navigation also has `Succeeded`, `Failed`, `TimedOut`, and `Cancelled`; every terminal case carries request ID and station ID.

- [ ] **Step 4: Implement Unsupported defaults only**

Do not add mock stations, coordinates, ranges, or success callbacks. Real Pleos adapters remain a later SDK-backed implementation of these interfaces.

- [ ] **Step 5: Run the four module test suites**

Run the Step 2 command.

Expected: all data contract tests pass.

- [ ] **Step 6: Commit**

Run: `git add data && git commit -m "feat: define unsupported data ports"`

### Task 4: Move the approved native UI into `feature:assist`

**Files:**
- Modify: `feature/assist/build.gradle.kts`
- Create from approved source: `feature/assist/src/main/kotlin/dev/fuelpilot/ui/FuelPilotUi.kt`
- Create from approved source: `feature/assist/src/main/kotlin/dev/fuelpilot/ui/FuelUiState.kt`
- Create from approved source: `feature/assist/src/main/kotlin/dev/fuelpilot/ui/PodongAnimation.kt`
- Create from approved source: `feature/assist/src/main/kotlin/dev/fuelpilot/ui/PodongClips.kt`
- Create: `feature/assist/src/main/res/drawable-nodpi/podong_atlas.png`
- Create: approved compatibility pose resources under the same drawable directory
- Create from approved tests: `feature/assist/src/test/kotlin/dev/fuelpilot/ui/FuelUiStateTest.kt`
- Create from approved tests: `feature/assist/src/test/kotlin/dev/fuelpilot/ui/PodongAnimationTest.kt`
- Delete after successful migration: `android/**`, `release-artifacts/**`

**Interfaces:**
- Consumes: approved native module at delivery commit `03b2486`
- Produces: the same `FuelPilotUi`, `FuelUiState`, animation clips, resources, and reducer behavior from `:feature:assist`

- [ ] **Step 1: Copy approved tests first and run them failing**

Run: `.\gradlew.bat :feature:assist:testDebugUnitTest --no-daemon --max-workers=1`

Expected: FAIL because the approved UI classes/resources are not in this module.

- [ ] **Step 2: Move source and resources without redesigning**

Preserve package `dev.fuelpilot.ui`, atlas coordinates, clip timing, Korean copy, reduced-motion rules, and permanent character presence.

- [ ] **Step 3: Add only required module dependencies**

Add Compose foundation and any missing UI test dependency through `gradle/libs.versions.toml`; do not retain a nested Gradle build.

- [ ] **Step 4: Run native UI tests and assemble the module**

Run: `.\gradlew.bat :feature:assist:testDebugUnitTest :feature:assist:assembleDebug --no-daemon --max-workers=1`

Expected: 11 approved UI tests pass and the module assembles.

- [ ] **Step 5: Re-run web baseline after removing duplicate Android delivery files**

Run: `npm.cmd test && npm.cmd run verify`

Expected: 17 tests pass; package verification must be updated to compare web assets with `feature/assist` rather than `android/fuel-pilot-ui`.

- [ ] **Step 6: Commit**

Run: `git add feature gradle web tests tools android release-artifacts && git commit -m "feat: move approved UI into assist module"`

### Task 5: Map deterministic decisions into approved UI models

**Files:**
- Create: `feature/assist/src/main/kotlin/dev/fuelpilot/feature/assist/AssistPresentation.kt`
- Create: `feature/assist/src/main/kotlin/dev/fuelpilot/feature/assist/FuelDecisionUiMapper.kt`
- Create: `feature/assist/src/test/kotlin/dev/fuelpilot/feature/assist/FuelDecisionUiMapperTest.kt`

**Interfaces:**
- Consumes: `AssistPresentationInput(decision: FuelDecision, evaluatedInput: FuelDecisionInput, routeSnapshotId: String?)`
- Produces: `AssistPresentation(route: FuelRouteUi, targetScreen: Screen?, recommendationKey: String?, availability: AssistAvailability)` and `FuelDecisionUiMapper.map(input)`

- [ ] **Step 1: Write failing mapper tests**

Cover `NoAction` hiding the assist surface, `Monitoring` producing a CTA-free widget, `Recommendation` mapping primary/alternatives/next-after-passing and readable reason, `PASSING` selecting `Screen.Passing`, and stale/unavailable/unsupported producing null numbers with distinct availability.

- [ ] **Step 2: Run the focused tests and confirm failure**

Run: `.\gradlew.bat :feature:assist:testDebugUnitTest --tests "dev.fuelpilot.feature.assist.FuelDecisionUiMapperTest" --no-daemon --max-workers=1`

Expected: FAIL because mapper types do not exist.

- [ ] **Step 3: Implement the mapper with no safety recomputation**

Map only fields already present in `FuelDecision` and `FuelDecisionInput`. Generate recommendation key from route ID, recommendation mode, and primary station ID. Never rank, filter, or synthesize stations in this layer.

- [ ] **Step 4: Run feature tests**

Run: `.\gradlew.bat :feature:assist:testDebugUnitTest --no-daemon --max-workers=1`

Expected: mapper and approved UI tests pass.

- [ ] **Step 5: Commit**

Run: `git add feature/assist && git commit -m "feat: map fuel decisions to approved UI"`

### Task 6: Add request-ID effects, screen priority, and persistent deduplication

**Files:**
- Create: `feature/assist/src/main/kotlin/dev/fuelpilot/feature/assist/AssistContract.kt`
- Create: `feature/assist/src/main/kotlin/dev/fuelpilot/feature/assist/AssistViewModel.kt`
- Create: `feature/assist/src/main/kotlin/dev/fuelpilot/feature/assist/AssistStateStore.kt`
- Create: `feature/assist/src/main/kotlin/dev/fuelpilot/feature/assist/PreferencesAssistStateStore.kt`
- Modify: `feature/assist/src/main/kotlin/dev/fuelpilot/ui/FuelPilotUi.kt`
- Modify: `feature/assist/src/main/kotlin/dev/fuelpilot/ui/FuelUiState.kt`
- Modify: `gradle/libs.versions.toml`
- Create: `feature/assist/src/test/kotlin/dev/fuelpilot/feature/assist/AssistViewModelTest.kt`
- Create: `feature/assist/src/test/kotlin/dev/fuelpilot/feature/assist/PreferencesAssistStateStoreTest.kt`

**Interfaces:**
- Consumes: `AssistPresentation`, navigation `WaypointResult`, real motion/refuel events
- Produces: `AssistIntent`, `AssistEffect.AddWaypoint(requestId, stationId)`, `AssistViewState`, `AssistStateStore`, and a single authoritative `AssistViewModel`

- [ ] **Step 1: Write failing ViewModel tests**

Test exact priority and failure cases: rapid double tap emits one effect; retry uses a new request ID; an old callback cannot complete the new request; timeout/cancel/Unsupported return to retryable recommendation; route change clears recommendation exposure; Moving/Unknown cancels details and celebration; new refuel outranks analysis/settings.

- [ ] **Step 2: Write failing persistence tests**

Using a temporary Preferences DataStore, persist more than 256 processed IDs and entries older/newer than 90 days. Assert pruning, restore-before-event behavior, preference restoration, recommendation-key restoration, and that celebration/`startedAtMs` are absent after a new store/ViewModel is created.

- [ ] **Step 3: Run focused tests and confirm failure**

Run: `.\gradlew.bat :feature:assist:testDebugUnitTest --tests "dev.fuelpilot.feature.assist.*" --no-daemon --max-workers=1`

Expected: FAIL because contract, ViewModel, and store do not exist.

- [ ] **Step 4: Implement one-way intent/effect flow**

`FuelPilotUi` emits a station-selection intent only. `AssistViewModel` atomically sets pending request state and emits exactly one `AddWaypoint` effect. Only matching request/station results can transition to Added or retryable error.

- [ ] **Step 5: Implement the documented screen priority**

Apply the seven-level priority from the addendum in one reducer path. Keep pure transition logic testable separately from Android lifecycle collection.

- [ ] **Step 6: Implement Preferences DataStore persistence**

Persist processed refuel IDs with timestamps, motion preferences, route ID, and recommendation key. Cap at 256 and prune older than 90 days. Restore before exposing the ViewModel as ready for live event collection.

- [ ] **Step 7: Run all feature tests and lint**

Run: `.\gradlew.bat :feature:assist:testDebugUnitTest :feature:assist:lintDebug --no-daemon --max-workers=1`

Expected: all tests and lint pass.

- [ ] **Step 8: Commit**

Run: `git add feature/assist gradle/libs.versions.toml && git commit -m "feat: coordinate assist UI state and effects"`

### Task 7: Wire the app without fake runtime data

**Files:**
- Modify: `app/src/main/kotlin/dev/fuelpilot/MainActivity.kt`
- Create: `app/src/main/kotlin/dev/fuelpilot/FuelPilotApplication.kt`
- Create: `app/src/main/kotlin/dev/fuelpilot/AppDependencies.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/test/kotlin/dev/fuelpilot/AppDependenciesTest.kt`
- Modify: `README.md`
- Modify: `docs/handoff.md`
- Modify: `docs/SESSION-PROGRESS.md`

**Interfaces:**
- Consumes: default Unsupported repositories, `AssistViewModel`, `FuelPilotUi`
- Produces: an app entry point that renders the approved UI only from real/explicitly unavailable state and processes navigation effects through the repository contract

- [ ] **Step 1: Write a failing dependency test**

Assert the default app dependency container uses Unsupported repositories and contains no sample station, coordinate, range, or successful navigation result.

- [ ] **Step 2: Run the app unit test and confirm failure**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "dev.fuelpilot.AppDependenciesTest" --no-daemon --max-workers=1`

Expected: FAIL because the dependency container does not exist.

- [ ] **Step 3: Implement the application dependency container**

Construct default Unsupported ports and the Preferences DataStore-backed state store. Keep SDK-specific construction behind this boundary.

- [ ] **Step 4: Replace the placeholder activity**

Collect `AssistViewState`, render `FuelPilotUi` when the assist surface is visible, leave `mapContent` empty, dispatch UI intents to the ViewModel, and send navigation effects to `NavigationRepository`.

- [ ] **Step 5: Update product and handoff documentation**

Document what is integrated, what remains Unsupported, how to open the web baseline, and the exact commands/results from current verification. Do not claim Pleos device integration.

- [ ] **Step 6: Run app build and unit tests**

Run: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --max-workers=1`

Expected: unit tests pass and debug APK assembles with no demo data path.

- [ ] **Step 7: Commit**

Run: `git add app README.md docs && git commit -m "feat: connect approved assist UI to app"`

### Task 8: Whole-branch verification, independent review, and remote preservation

**Files:**
- Modify only if verification exposes a scoped defect; otherwise none
- Update: `docs/SESSION-PROGRESS.md` with final evidence

**Interfaces:**
- Consumes: all prior task commits
- Produces: reviewed, verified, and remotely preserved integration branch

- [ ] **Step 1: Run repository policy and environment checks**

Run: `python scripts/dev.py doctor` and `python -m unittest discover -s scripts/tests -v`

Expected: doctor reports JDK 17/Android SDK ready; Python policy tests pass.

- [ ] **Step 2: Run full Android verification with an isolated Gradle home if the shared cache is broken**

Run: `python scripts/dev.py verify`

If and only if the known cache move error recurs, rerun the same Gradle `verifyAll --no-daemon --max-workers=1` target with a repository-local temporary Gradle user home and record both results. Do not report success if plugin download or cache errors still prevent completion.

- [ ] **Step 3: Run web verification**

Run: `npm.cmd test && npm.cmd run verify`

Expected: 17 tests pass and package verification succeeds against the migrated native resources.

- [ ] **Step 4: Check Git and artifact hygiene**

Run: `git status --short`, `git diff --check`, and search tracked files for `local.properties`, machine-specific paths, caches, debug AARs, and demo constants in Android source.

Expected: clean tree; no machine files/caches/AARs; no demo runtime values.

- [ ] **Step 5: Launch a fresh Orca reviewer in this worktree**

Ask it to inspect the whole branch against both specs, the plan, and the base commit without editing. Resolve only evidence-backed findings and rerun affected tests.

- [ ] **Step 6: Record verification evidence and commit it**

Run: `git add docs/SESSION-PROGRESS.md && git commit -m "docs: record approved UI integration verification"`

- [ ] **Step 7: Push and verify the live remote hash**

Run: `git push -u origin integrate/podong-approved`, then `git ls-remote --heads origin refs/heads/integrate/podong-approved`.

Expected: the remote hash exactly equals `git rev-parse HEAD`. If push is blocked, report the branch as local-only and do not call the work complete.
