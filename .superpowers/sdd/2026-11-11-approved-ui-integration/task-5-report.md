# Task 5 Report

## Status

DONE

## Summary

- Added the fixed `AssistPresentationInput`, `AssistPresentation`, and `AssistAvailability` contract.
- Mapped `FuelDecision` into the approved `FuelRouteUi` and `Screen` models without changing approved UI code.
- Preserved engine-provided primary, alternative, and next-after-primary order and values without ranking, filtering, or safety recomputation.
- Kept unknown alternative remaining-range values null and preserved the engine-provided negative next-stop remaining range.
- Mapped stale, unavailable, and unsupported results to distinct availability values with all unavailable route numbers left null.
- Derived a delimiter-safe stable recommendation key only from route snapshot ID, recommendation mode, and primary station ID. A `RECOMMEND` to `PASSING` stage transition retains the same exposure key.
- Did not push.

## Follow-up Contract Correction

- Corrected the key contract after authoritative-plan review: recommendation stage is not a key input.
- Test-first red command: the focused mapper suite ran 7 tests and failed only `recommendationKeyChangesOnlyWithDocumentedInputs` with a `ComparisonFailure`, because the existing key changed between `RECOMMEND` and `PASSING` (`BUILD FAILED in 1m 37s`).
- Focused green command: 7 mapper tests passed; `BUILD SUCCESSFUL in 2m 2s`.
- Full feature green command: 3 suites, 18 tests, 0 failures, 0 errors, 0 skipped; `BUILD SUCCESSFUL in 1m 25s`.
- The corrected test asserts stage changes keep the key equal while route snapshot ID, recommendation mode, and primary station ID changes still change it.
- Correction commit: `3531c5f43c56db839dba3231d6829c0880a3d875` (only mapper source and mapper test).

## Red Evidence

Tests were written before production code. The focused command used the warmed isolated Gradle home at `<isolated-gradle-home>`, the process-local Android SDK, and process-local Kotlin flags that avoid this machine's incremental-cache write failure:

```powershell
.\gradlew.bat :feature:assist:testDebugUnitTest --tests "dev.fuelpilot.feature.assist.FuelDecisionUiMapperTest" --no-daemon --max-workers=1 --offline --no-watch-fs --console=plain '-Dkotlin.compiler.execution.strategy=in-process' '-Pkotlin.incremental=false' '-Pkotlin.incremental.useClasspathSnapshot=false'
```

- Exit: `1`.
- Result: `compileDebugUnitTestKotlin` failed on the intentionally absent `FuelDecisionUiMapper`, `AssistPresentationInput`, and `AssistAvailability` types, followed by the expected unresolved presentation properties.
- Earlier focused attempts did not count as the TDD red result: one lacked process-local Android SDK discovery, and one reached Kotlin compilation but hit the machine's `write-protected media` incremental classpath-snapshot error. Neither changed the shared Gradle cache.

## Green Evidence

The focused command above was rerun after implementation:

- Exit: `0`.
- Result: `BUILD SUCCESSFUL in 3m 16s`; all 7 mapper tests passed.

The full feature command used the same isolated Gradle home, SDK, and Kotlin flags:

```powershell
.\gradlew.bat :feature:assist:testDebugUnitTest --no-daemon --max-workers=1 --offline --no-watch-fs --console=plain '-Dkotlin.compiler.execution.strategy=in-process' '-Pkotlin.incremental=false' '-Pkotlin.incremental.useClasspathSnapshot=false'
```

- Exit: `0`.
- Result: `BUILD SUCCESSFUL in 2m 7s`.
- Test XML: 3 suites, 18 tests, 0 failures, 0 errors, 0 skipped (11 approved UI tests plus 7 mapper tests).

Additional checks:

- `git diff --check`: passed.
- `python scripts/dev.py doctor` with process-local SDK: `Ready: JDK 17, Android SDK, and Gradle wrapper.`
- `python scripts/dev.py verify` used the isolated Wave 1 Gradle home and reached `:app:checkDebugAarMetadata`, then exited `1` on the known Windows `Could not move temporary workspace ... to immutable location` transform-cache failure. The cache was not repaired, and the shared Gradle cache was not mutated. This environment result is not reported as a whole-repository verification pass.

## Files

- `feature/assist/src/main/kotlin/dev/fuelpilot/feature/assist/AssistPresentation.kt`
- `feature/assist/src/main/kotlin/dev/fuelpilot/feature/assist/FuelDecisionUiMapper.kt`
- `feature/assist/src/test/kotlin/dev/fuelpilot/feature/assist/FuelDecisionUiMapperTest.kt`

## Self-review

- Confirmed the implementation commit contains only the three scoped files.
- Confirmed `NoAction` has no target screen and `Monitoring` uses the calm widget while retaining last-safe station information.
- Confirmed recommendation mapping preserves primary first, then alternatives in engine order, with the engine's next-after-primary candidate mapped separately.
- Confirmed no candidate is sorted, filtered, promoted, synthesized, or checked for reachability in the mapper.
- Confirmed only the engine-provided `rangeAfterStopKm` and `rangeAfterNextKm` become station remaining-range values; alternatives remain null.
- Confirmed `PASSING` maps to `Screen.Passing` and `RECOMMEND` maps to `Screen.Recommendation`.
- Confirmed readable reason copy comes from the existing pure `RecommendationReason` contract.
- Confirmed recommendation exposure identity uses exactly route snapshot ID, recommendation mode, and primary station ID; stage affects the target screen but not the key.
- Confirmed unavailable results expose no numbers or stations and preserve stale/unavailable/unsupported distinctions.
- Confirmed approved `dev.fuelpilot.ui`, core decision logic, data ports, app wiring, and documentation were not edited.
- Confirmed no API keys, SDK binaries, local properties, caches, machine-specific product paths, or generated build outputs were committed.

## Commit Hashes

- Implementation commit: `76010c1cc3d5a711b00856ed87f7d8235175e6dc`
- Contract correction commit: `3531c5f43c56db839dba3231d6829c0880a3d875`

## Integration Review

- Integrated commits: `a274498` and `58da78a` on `integrate/podong-approved`.
- Independent read-only review: `SPEC PASS`; `QUALITY APPROVED`; no findings.
- Integration-branch test XML: 3 suites, 18 tests, 0 failures, 0 errors, 0 skipped.
