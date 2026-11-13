# Task 2 — Extend the pure decision result for passing-stage presentation

Base: `0f8d1da4c0e7d5a6c973a00b1a3b5c7bd36f7caf`

Read Task 2 in `docs/superpowers/plans/2026-11-11-approved-ui-integration.md` and the approved design/addenda. Modify only `core/model/**`.

Required interfaces:
- `RecommendationStage { RECOMMEND, PASSING }`
- `FuelDecision.Recommendation.nextAfterPrimary: FuelStationCandidate?`
- `rangeAfterNextKm: Double?`
- `stage: RecommendationStage`
- `SignalStatus.UNSUPPORTED` and matching typed unavailable reasons
- `AlertPolicy.passingDistanceKm = 5.0`, `passingMinutes = 5`

TDD is mandatory: add the four named failing tests first, capture the red result, implement the minimum domain change, and make all `:core:model` tests green. Preserve unsafe next-candidate range; keep core deterministic and free of Android/UI types. Repair and pin readable UTF-8 Korean recommendation copy. Commit with `feat: expose last-safe passing context`. Do not push or use subagents. Write `.superpowers/sdd/2026-11-11-approved-ui-integration/task-2-report.md` with status, red/green evidence, files, self-review, and commit hash; force-add only that report if ignored.
