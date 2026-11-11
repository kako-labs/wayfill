# Task 3 — Add explicit data and navigation ports with Unsupported defaults

Base: `0f8d1da4c0e7d5a6c973a00b1a3b5c7bd36f7caf`

Read Task 3 in `docs/superpowers/plans/2026-11-11-approved-ui-integration.md` and the approved design/addenda. Modify only `data/**` and required module build files inside those modules.

Create energy, location, POI/station, and navigation repository contracts plus tests. Use typed sealed outcomes with distinct `Available`, `Stale`, `Unavailable`, and `Unsupported`. Navigation defines `WaypointRequest(requestId, stationId)` and terminal `Succeeded`, `Failed`, `TimedOut`, `Cancelled`; every terminal result must carry both IDs. Default implementations return only `Unsupported`; never add fake stations, coordinates, ranges, numeric placeholders, or success callbacks.

TDD is mandatory: contract tests first, capture expected red compilation/test result, implement minimum contracts/defaults, then run all four module test suites from the plan green. Commit with `feat: define unsupported data ports`. Do not push or use subagents. Write `.superpowers/sdd/2026-11-11-approved-ui-integration/task-3-report.md` with status, red/green evidence, files, self-review, and commit hash; force-add only that report if ignored.
