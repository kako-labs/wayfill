# Product Readiness Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans for the core work and bounded independent dispatch for provider and CI/submission tasks. Tests precede behavior changes; one final fresh review.

**Goal:** Complete the SDK-independent product gaps approved by the user.
**Architecture:** Keep core deterministic and assist state authoritative. Add an app shell, durable policy, application runtime/service, separate debug demo, verification/CI and isolated HTTP data boundary.
**Tech Stack:** Existing Kotlin/Compose/Gradle toolchain, Android35 instrumentation, Python standard-library gateway/tests and CI helpers.
**Spec:** docs/superpowers/specs/2026-11-11-product-readiness.md

## Global Constraints

Preserve approved artwork/timing. Real builds never fabricate data. SDK and credentials remain external dependencies. Work entirely on D. Do not add a map, secrets, SDK binaries or build outputs to Git. Keep old tags immutable.

## Review Focus

- Policy changes while moving/unknown are ignored; fresh durable policy reaches the engine without reviving old pending requests.
- UI, service and rotation share exactly one session; process/boot restore must not replay navigation or celebrations.
- Debug fixtures/store cannot leak into release/default dependencies.
- Unverified straight-line station data cannot be ranked as a route-safe candidate; missing gateway config fails closed.
- Permission refusal, source/storage failure and user stop do not silently restart background work or leave a stale Parked action.

### Task 1: Durable policy and complete app shell
**Files:** feature/assist stored state/reducer/store/tests; app shell/coordinator/tests.
**Interfaces:** minimumReserveKm durable Int; parked-only ReserveChanged; observable store policy; home/settings/assist navigation.
- [ ] RED tests policy bounds/parking/restore and engine consumption.
- [ ] Implement policy, home, accessible settings and honest status.
- [ ] GREEN existing+new unit tests and debug build; commit.

### Task 2: Verified provider boundary (independent)
**Files:** new data/poi gateway, new core discovery models if needed, server/, docs/data-provider.md and owned tests only.
**Interfaces:** raw real station catalog query/result with provenance; HTTPS gateway config; no fabricated road metrics.
- [ ] Research primary provider contract and write failing boundary/server tests.
- [ ] Implement client/server isolation, explicit unconfigured state, quota/cache and credential redaction.
- [ ] Run owned tests; return exact live-access blockers and commit only owned files.

### Task 3: Background runtime, recovery and navigation modes
**Files:** app runtime/service/receiver/host/permissions, data/navigation request contract, assist reducer, tests.
**Interfaces:** single session runtime; UI visible/monitoring enable state; safe retry/restart; SET_DESTINATION vs ADD_WAYPOINT.
- [ ] RED lifecycle/dedup/cancel/mode/capability tests.
- [ ] Implement shared ownership, foreground lifecycle, permission/boot recovery and mode dispatch.
- [ ] GREEN unit/build/lint and emulator probes; commit.

### Task 4: Debug native demo and Android screen tests
**Files:** app/src/debug, app/src/release variant bridge, app/src/androidTest, app UI semantics, build config.
**Interfaces:** explicit demo entry and separate persistent namespace; managed pixel2api35 device.
- [ ] RED instrumentation setup/policy/whole-flow/rotation/moving/duplicate tests.
- [ ] Implement labelled scenarios and release-null demo bridge, isolation checks.
- [ ] Run connected Android35 screen tests, release build and isolation check; commit.

### Task 5: CI and submission/portable delivery (independent)
**Files:** .github/workflows, scripts/ui_tests.py, scripts/export_source.py, docs/submission/ and own helpers/tests.
**Interfaces:** root verify + web/server checks; :app:pixel2api35DebugAndroidTest or connected equivalent; source-only ZIP.
- [ ] Implement CI/report/APK artifact upload and portable helpers.
- [ ] Draft evidence-labelled proposal, shot list, privacy and submission checklist.
- [ ] Validate helpers/workflow and commit only owned files; no invented CI/device results.

### Task 6: Final integration and handoff
**Files:** provider wiring in app, latest docs and review fixes only.
- [ ] Integrate bounded work; run doctor/verifyAll, web/server/policy/UI and release isolation.
- [ ] One independent whole-change review; TDD-fix material findings.
- [ ] Preserve exact limitations, artifacts/links and next-device instructions; push and verify remote hash.
