# Task 5 Brief — Map deterministic decisions into approved UI models

## Scope

Create only:

- `feature/assist/src/main/kotlin/dev/fuelpilot/feature/assist/AssistPresentation.kt`
- `feature/assist/src/main/kotlin/dev/fuelpilot/feature/assist/FuelDecisionUiMapper.kt`
- `feature/assist/src/test/kotlin/dev/fuelpilot/feature/assist/FuelDecisionUiMapperTest.kt`

Do not edit approved `dev.fuelpilot.ui` behavior, the core decision engine, data ports, app wiring, or documentation.

## Contract

- Consume `AssistPresentationInput(decision: FuelDecision, evaluatedInput: FuelDecisionInput, routeSnapshotId: String?)`.
- Produce `AssistPresentation(route: FuelRouteUi, targetScreen: Screen?, recommendationKey: String?, availability: AssistAvailability)`.
- Expose `FuelDecisionUiMapper.map(input)`.
- Map only fields already present in `FuelDecision` and `FuelDecisionInput`.
- Never rank, filter, recompute safety, or synthesize a station in the presentation layer.
- Derive a stable recommendation key from route ID, recommendation mode/stage, and primary station ID.
- Keep stale, unavailable, and unsupported signals distinct; unknown numeric values remain null.

## Required tests

Write tests first and cover:

1. `NoAction` hides the assist surface.
2. `Monitoring` produces a CTA-free widget.
3. `Recommendation` maps primary, alternatives, next-after-passing, and readable reason.
4. `PASSING` selects `Screen.Passing`.
5. Stale, unavailable, and unsupported inputs produce null numbers with distinct availability.
6. Recommendation key changes only when its documented inputs change.
7. Mapper output proves no presentation-layer re-ranking or fake values.

## Verification

Run:

```powershell
.\gradlew.bat :feature:assist:testDebugUnitTest --tests dev.fuelpilot.feature.assist.FuelDecisionUiMapperTest --no-daemon --max-workers=1
.\gradlew.bat :feature:assist:testDebugUnitTest --no-daemon --max-workers=1
git diff --check
```

If the shared Gradle transform cache fails, do not mutate it. Report the exact failure and use a task-local isolated cache only if already-resolved artifacts are sufficient.

## Delivery

- Commit only the three scoped files with message `feat: map fuel decisions to approved UI`.
- Write `.superpowers/sdd/2026-11-11-approved-ui-integration/task-5-report.md` with red/green evidence, self-review, and commit hash.
- End with exactly `DONE` or `NOT_DONE`.
