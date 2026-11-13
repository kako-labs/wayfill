# Task 2 Report

## Status

DONE

## Summary

- Extended the pure decision result with `RecommendationStage`, passing context, and typed unsupported signal outcomes.
- Preserved the first compatible, non-closed route candidate after the last safe stop without promoting it to `primary` or `alternatives`.
- Preserved negative `rangeAfterNextKm` values so presentation can explain an unsafe next stop without recomputing safety.
- Added deterministic 5 km / 5 minute passing thresholds through `AlertPolicy`.
- Kept the existing readable UTF-8 Korean recommendation copy and made its interpolation explicit; the existing exact-string tests cover last-safe, nearest, detour, and stale-business-information copy.
- Did not push.

## Red Evidence

- Tests were added before production changes with the exact required names:
  - `recommendationIncludesFirstRouteCandidateAfterLastSafeStop`
  - `unsafeNextCandidateIsNeverPromotedToPrimary`
  - `passingStageStartsOnlyInsidePassingThreshold`
  - `unsupportedSignalsReturnMatchingUnavailableReasons`
- Command: `.\gradlew.bat :core:model:test --tests "dev.fuelpilot.model.FuelDecisionEngineTest" --no-daemon --max-workers=1`
- Exit: `1`
- Result: `compileTestKotlin` failed on the intentionally missing `nextAfterPrimary`, `rangeAfterNextKm`, `RecommendationStage`, `stage`, `SignalStatus.UNSUPPORTED`, and the three typed unsupported reasons.

## Green Evidence

- Focused command: `.\gradlew.bat :core:model:test --tests "dev.fuelpilot.model.FuelDecisionEngineTest" --no-daemon --max-workers=1`
  - Exit: `0`
  - Result: `BUILD SUCCESSFUL`; all 10 engine tests passed.
- Full core command: `.\gradlew.bat :core:model:test --no-daemon --max-workers=1`
  - Exit: `0`
  - Result: `BUILD SUCCESSFUL`; 14 tests passed in total (10 engine, 4 recommendation-copy), with 0 failures, 0 errors, and 0 skipped.
- `python scripts/dev.py doctor`
  - Initial exit `1` because no Android SDK environment variable was set.
  - Rerun with process-local `ANDROID_SDK_ROOT` pointing to the installed standard SDK: exit `0`, `Ready: JDK 17, Android SDK, and Gradle wrapper.`
- `python scripts/dev.py verify`
  - Exit `1` at `:app:checkDebugAarMetadata` due to the documented shared Gradle-cache error: temporary transform workspaces could not move to immutable locations.
  - The approved-plan fallback `verifyAll --no-daemon --max-workers=1` with a repository-local isolated Gradle home could not download Gradle because the JDK rejected the remote certificate chain (`PKIX path building failed`). This environment limitation is not reported as a repository verification pass. Its generated zero-byte wrapper artifacts were removed.

## Files

- `core/model/src/main/kotlin/dev/fuelpilot/model/FuelModels.kt`
- `core/model/src/main/kotlin/dev/fuelpilot/model/FuelDecisionEngine.kt`
- `core/model/src/main/kotlin/dev/fuelpilot/model/RecommendationReason.kt`
- `core/model/src/test/kotlin/dev/fuelpilot/model/FuelDecisionEngineTest.kt`
- `.superpowers/sdd/2026-11-11-approved-ui-integration/task-2-report.md` (ignored task artifact, force-added separately)

`RecommendationReasonTest.kt` required no change because its four existing assertions already pin the exact readable Korean strings required by Task 2.

## Self-review

- Confirmed the product commit changes only `core/model/**`.
- Confirmed `git diff --check` and the staged diff check pass.
- Confirmed no Android, Compose, or `dev.fuelpilot.ui` reference appears under `core/model/src`.
- Confirmed route candidates are filtered for compatibility, effective non-closed status, and route distance before selecting the first candidate strictly after `primary`.
- Confirmed unsafe candidates remain excluded from safe alternatives and can retain a negative evaluated range.
- Confirmed `PASSING` begins inclusively at either 5.0 km or 5 minutes; candidates outside both thresholds remain `RECOMMEND` while inside the broader alert window.
- Confirmed `UNSUPPORTED` maps separately for energy, location, and route.
- Confirmed no API keys, SDK binaries, machine-specific paths, caches, Android/UI code, or unrelated files were added to the product commit.

## Commit Hash

- Product commit: `f0e8f3be7d7e3acd131234453cacf1220168462e`
