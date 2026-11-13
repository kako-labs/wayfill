# Dual-Mode Companion and Background HUN Implementation

Status: implemented in the shared working tree as of 2026-11-12. This document describes source currently present in the checkout; it does not claim a commit, release, Pleos host integration, or vehicle-device verification.

## Scope and architecture

WAYFILL has two presentation modes backed by one decision and state pipeline:

1. The in-app companion renders the full parked experience and a calm compact moving widget.
2. The background surface publishes an Android high-priority navigation-category notification as the current HUN prototype while the app UI is hidden.

The HUN is not a second decision engine. Both surfaces consume the same application-owned `AssistRuntime`, `AssistViewModel`, `AssistViewState`, recommendation key, persistence store, and navigation effect stream.

```text
Pleos/data adapters
  -> AppCoordinator + pure FuelDecisionEngine/FuelDecisionUiMapper
  -> AssistRuntime (one process session shared by UI and foreground service)
  -> AssistViewModel (serialized reducer, persistence gate, effects)
  -> AssistViewState
       -> in-app companion when UI is visible
       -> backgroundAdvice projection when UI is hidden
            -> Android notification/HUN prototype
            -> immutable Guide now / Later PendingIntents
            -> RecommendationActionReceiver
            -> the same AssistViewModel
  -> AddWaypoint effect
  -> NavigationRepository
  -> correlated NavigationCompleted callback
```

Key implementation locations:

- Pure Assist contract and reducer: [`AssistContract.kt`](../feature/assist/src/main/kotlin/dev/fuelpilot/feature/assist/AssistContract.kt)
- Serialized owner and storage-before-exposure gate: [`AssistViewModel.kt`](../feature/assist/src/main/kotlin/dev/fuelpilot/feature/assist/AssistViewModel.kt)
- Shared UI/service session: [`AssistRuntime.kt`](../app/src/main/kotlin/dev/fuelpilot/AssistRuntime.kt)
- HUN eligibility and action revalidation: [`MonitoringPolicy.kt`](../app/src/main/kotlin/dev/fuelpilot/MonitoringPolicy.kt)
- Explicit opt-in, permissions, and persistent cooldown: [`MonitoringControl.kt`](../app/src/main/kotlin/dev/fuelpilot/MonitoringControl.kt)
- Foreground service and notifications: [`MonitoringService.kt`](../app/src/main/kotlin/dev/fuelpilot/MonitoringService.kt)
- Immutable action receiver: [`RecommendationActionReceiver.kt`](../app/src/main/kotlin/dev/fuelpilot/RecommendationActionReceiver.kt)

No in-app map was added. Route rendering and guidance remain the vehicle navigation application's responsibility.

## Motion and presentation safety rules

Monitoring start and recommendation use are deliberately separate decisions.

| Situation | Companion behavior | Background/HUN behavior | Navigation action |
| --- | --- | --- | --- |
| `Motion.Parked` | Full recommendation, analysis, settings, long-drive, refuel, and approved parked animation may be shown. | A current recommendation may be published while the app UI is hidden. | Current recommendation may be accepted. The existing in-app caller may omit the key because UI intents and presentation changes share the serialized owner. |
| `Motion.Moving` | Always resolves to the compact `Widget`; detailed screens, playful parked acting, and refuel celebration are suppressed. | A fresh current recommendation may be published while the app UI is hidden. | Acceptance requires the exact recommendation key and station ID. A request created while already moving survives repeated `Moving` snapshots. |
| `Motion.Unknown` | Compact safe fallback only; transient details, pending navigation, result, refuel UI, and celebration are cleared on transition. | No recommendation HUN is eligible. | Always rejected. |
| Parked to Moving transition | Immediately cancels the parked transient state and any pending request created before departure. | Old exposure is cleared; a later moving evaluation may produce a new current compact recommendation. | The coordinator observes pending removal and cancels the in-flight navigation coroutine. |
| Moving to Parked transition | A recommendation already delivered while moving does not automatically replay as a broad parked recommendation. It remains available through the compact state and parked details affordance. | The moving HUN is not converted into parked animation. | A moving-origin pending request remains correlated if it is still current. |
| Route or destination identity change | Clears transient state, current presentation, and recommendation exposure immediately. | Existing advice becomes ineligible. | Pending request is cancelled; late callback is ignored. |
| Refuel confirmation | Stable event ID is persisted before positive exposure. Parked may show one-shot celebration; moving consumes dedupe without celebration. | No advice while refuel UI or celebration is active. | Existing pending request is cleared. |

Reduced-motion behavior is unchanged: OS animator scale zero or the stored preference removes celebration/playful movement but keeps the character and message present. Celebration state and monotonic animation time are never persisted, so process restoration cannot replay historical acting.

## Exact recommendation acceptance rules

`AssistIntent.SelectStation` produces one `AssistEffect.AddWaypoint` only when every condition below is true:

- Assist restoration completed and the reducer is ready.
- Motion is `Parked` or `Moving`, never `Unknown`.
- There is no pending waypoint request.
- There is no active refuel confirmation or celebration.
- Presentation availability is exactly `READY`, not `STALE`, `UNAVAILABLE`, or `UNSUPPORTED`.
- Target screen is `Recommendation` or `Passing`.
- The presentation has a nonblank recommendation key.
- The selected station ID is nonblank and exactly matches the currently recommended station.
- A moving action supplies the exact current recommendation key.
- A parked keyed action also matches exactly; only the serialized legacy parked UI may omit the key.
- The request ID supplied by the owner is nonblank.

The request stores `requestId`, `stationId`, navigation mode, and expected route ID. A navigation result changes state only when both result request ID and station ID match the current pending request. Success is displayed only after that callback; issuing `AddWaypoint` is not treated as success. Route invalidation or pending removal cancels the coordinator coroutine, and late or mismatched callbacks are ignored.

## HUN publication and action rules

### Starting and maintaining background monitoring

- Start is allowed only from a visible Activity after an explicit user tap while parked.
- Location and notification permission plus enabled device location are required before foreground-service startup.
  On Automotive, `pleos.car.permission.FUSED_LOCATION` is a dangerous permission, so the visible Activity includes it
  in the runtime permission request. The permission callback starts monitoring only if the complete environment gate
  passes, and `MonitoringControl.start()` checks the same gate again before persisting opt-in or starting the service.
  Non-Automotive builds do not require the PLEOS permission.
- Durable opt-in (`requested`) is separate from whether the service/runtime is currently active.
- Boot or package replacement never silently starts the location foreground service. If the user had opted in, a notification asks them to reopen the app and resume.
- Permission or location-service loss stops monitoring, invalidates active runtime input, cancels recommendation advice, and requires a retry.
- App and service share one live subscription. Hiding the Activity does not create a second decision engine or ViewModel.

### Publishing advice

`backgroundAdvice` returns a HUN payload only when:

- state is ready;
- motion is parked or moving;
- there is no pending request, refuel state, or celebration;
- availability is `READY`;
- target is `Recommendation` or `Passing`;
- recommendation key and station ID are nonblank, no more than 512 characters, and contain no ISO control characters;
- station name remains nonblank after whitespace normalization and truncation to 80 characters.

The service additionally requires the app UI to be hidden. Advice is claimed in persistent `SharedPreferences` before `notify`; the per-recommendation cooldown is 15 minutes, capped at 128 stored records. A wall-clock rollback fails closed. Showing the app cancels the advice notification.

Each recommendation HUN also calls `setTimeoutAfter(RECOMMENDATION_HUN_TIMEOUT_MS)` with
`RECOMMENDATION_HUN_TIMEOUT_MS = 15_000L`, so Android automatically removes the displayed notification after 15
seconds if the user takes no action. This display timeout is separate from the 15-minute per-recommendation cooldown:
automatic removal does not dispatch `Guide now` or `Later`, and it does not make the same recommendation immediately
eligible for another HUN.

The current prototype uses a private-visibility, high-priority Android notification in the navigation category. It does not prove a Pleos-native HUN surface or permission to overlay the vehicle navigation application.

### Handling actions

Both action PendingIntents are immutable, explicit, and target a non-exported receiver. The receiver removes the displayed advice, parses the expected key/station identity, and then rechecks all of the following against current state:

- monitoring is still requested and active;
- location/notification permissions are still satisfied;
- device location is still enabled;
- app UI is still hidden;
- the current state is still HUN-eligible;
- current recommendation key and station ID exactly match the notification identity.

`Guide now` dispatches `SelectStation(stationId, recommendationKey)` into the shared reducer, which validates the identity again before producing `AddWaypoint`. `Later` writes a new cooldown timestamp for that exact current recommendation. Invalid, stale, unsupported, unknown-motion, mismatched, forged, duplicate-pending, refuel, or celebration actions are rejected.

Navigation-result replay is also fail-closed. While the current state retains a matching last
`WaypointResult.Succeeded`, `backgroundAdvice` returns no payload and the reducer rejects another `SelectStation`, so
the same current recommendation/action cannot publish or dispatch another navigation request. `WaypointResult.Failed`
and `WaypointResult.TimedOut` clear the correlated pending request but are not treated as success; if the recommendation
is still current and every eligibility/identity check still passes, the user may retry with a new request ID. That retry
does not bypass the HUN cooldown: it can occur through the still-current companion action, or through a later HUN only
after the recommendation becomes publishable again under the normal cooldown rules.

## Existing work reused

The dual-mode implementation extends existing production boundaries instead of duplicating them:

- Pure deterministic `FuelDecisionEngine` and `FuelDecisionUiMapper` remain the only recommendation source.
- `AppCoordinator` continues to classify Fresh/Stale/Unavailable/Unsupported inputs, enforce read and navigation timeouts, and translate effects through `NavigationRepository`.
- One application-owned `AssistRuntime` and `AssistViewModel` are shared across Activity and service lifetimes.
- Existing `AssistStoredState` and `PreferencesAssistStateStore` retain processed refuel IDs, recommendation exposure, route identity, user reserve, character motion, and reduced-motion preference. No persistence schema change was required for dual-mode acceptance.
- Existing storage-before-positive-exposure behavior now also covers moving recommendation delivery and its deferred waypoint effect.
- Existing request/station callback correlation, route invalidation, departure cancellation, event dedupe, storage failure behavior, and retryable navigation failures remain in force.
- The approved companion identity, calm moving avatar, permanent character presence beside messages, parked-only broad acting, and one-shot celebration behavior are preserved.

`AssistViewState.assistVisible` is the preferred semantic name because the surface may be a companion, compact recommendation, or HUN assist. `overlayVisible` remains a read-only compatibility property for existing callers.

## Validation matrix

“Covered in source” means a test exists in the working tree. It is not a claim that the test executed successfully in this session.

| Area | Expected validation | Automated coverage present | Outcome observed in this session |
| --- | --- | --- | --- |
| Moving delivery and acceptance | Compact `Widget`; matching keyed action emits one correlated `AddWaypoint` | `AssistViewModelTest.movingRecommendationIsCompactAndCanEmitOneCorrelatedWaypointEffect` | Not executed; Gradle stopped before Kotlin compilation. |
| Storage before moving exposure/effect | Recommendation and effect remain hidden/deferred until durable exposure save | `movingRecommendationAndWaypointEffectWaitForDurableExposure` | Not executed; Gradle stopped before Kotlin compilation. |
| Unsafe acceptance matrix | Unknown, stale, unavailable, unsupported, mismatched station/key, blank key, refuel/celebration, and duplicate pending are blocked | `reducerRejectsEveryUnsafeOrMismatchedRecommendationAcceptance` | Not executed; Gradle stopped before Kotlin compilation. |
| Route invalidation | Route/destination change cancels pending and ignores late callback | `routeOrDestinationChangeClearsExposureAndInvalidatesPending` | Test exists; not executed in this session. |
| Navigation correlation | Only matching request ID and station can finish; retry uses a new request ID | `retryUsesNewRequestIdAndOldOrWrongStationCallbackCannotCompleteIt` | Test exists; not executed in this session. |
| Recommendation delivery dedupe | Moving delivery persists once and does not replay broad parked acting | `movingRecommendationDeliveryIsPersistedOnceAndDoesNotReplayBroadParkedActing` plus existing exposure recreation tests | Not executed; Gradle stopped before Kotlin compilation. |
| HUN eligibility | Parked/moving accepted; Unknown, stale/unsupported, pending, and refuel rejected | `MonitoringPolicyTest.adviceIsCreatedForFreshParkedOrMovingRecommendationButNotUnknown` | Test exists; not executed in this session. |
| HUN action identity | Exact current key/station required; forged/control-character identity rejected | `MonitoringPolicyTest.actionRequiresExactCurrentRecommendationAndStationIdentity` | Test exists; not executed in this session. |
| HUN display timeout | Recommendation HUN uses `setTimeoutAfter(15_000)` independently of the 15-minute recommendation cooldown | `MonitoringPolicyTest.hunTimeoutIsFifteenSecondsAndIndependentFromRecommendationCooldown` | Test exists; not executed in this session. |
| Success suppression and retry | Last `Succeeded` suppresses the current HUN/action; `Failed`/`TimedOut` may retry with a new correlated request ID while other gates remain valid | `MonitoringPolicyTest.adviceIsCreatedForFreshParkedOrMovingRecommendationButNotUnknown` and `AssistViewModelTest.retryUsesNewRequestIdAndOldOrWrongStationCallbackCannotCompleteIt` | Tests exist; not executed in this session. |
| Cooldown | Exact 15-minute boundary; clock rollback and invalid time fail closed | `MonitoringPolicyTest.cooldownHasDeterministicBoundaryAndFailsClosedOnClockRollback` | Test exists; not executed in this session. |
| Monitoring start | Parked only | `MonitoringPolicyTest.foregroundMonitoringStartRequiresParkedMotion` | Test exists; not executed in this session. |
| Automotive FusedLocation permission gate | Runtime request includes `FUSED_LOCATION`; Automotive monitoring cannot start without it, while non-Automotive does not require it | `MonitoringPolicyTest.automotiveMonitoringRequiresFusedLocationPermission` and `nonAutomotiveMonitoringDoesNotRequireFusedLocationPermission` | Tests exist; not executed in this session. |
| Shared runtime | UI/service share one subscription; user stop pauses it | `AssistRuntimeTest.uiAndServiceShareOneLiveSubscriptionAndUserStopPausesIt` | Test exists; not executed in this session. |
| Restoration | Dedupe restores; celebration and pending effects do not replay | `AssistRuntimeTest.retryRestoresDedupButNeverReplaysCelebrationOrPendingEffects` and feature persistence tests | Test exists; not executed in this session. |
| Default production ports | Unsupported ports do not invent route, fuel, position, station, or navigation success | `AppDependenciesTest.defaultsUseUnsupportedPortsAndNeverInventRouteOrNumbers` | Test exists; not executed in this session. |
| Architecture boundary | Repository architecture verification | `verifyArchitecture` task | **PASS** in a separate execution. This is not evidence that the later full verify reached Kotlin compilation. |
| Toolchain discovery | JDK 17, Android SDK, wrapper | `python scripts/dev.py doctor` | Passed with `ANDROID_HOME` set for the process. |
| Full Android verification | Build, lint, unit tests, assembly | `python scripts/dev.py verify` | **BLOCKED** at `:app:checkDebugAarMetadata` while resolving `ai.pleos.playground:NaviHelper:3.0.5`: both Google and Pleos Maven requests failed TLS certificate validation with PKIX path-building errors. Kotlin compilation did not start; no Android pass count is claimed. |
| Feature unit tests and assembly | Assist reducer/ViewModel tests plus debug assembly | `:feature:assist:testDebugUnitTest :feature:assist:assembleDebug` | **BLOCKED** at `:data:energy:compileDebugKotlin` before tests by the Gradle transform-cache temporary-to-immutable workspace move error. No feature test or assembly pass is claimed. |

The TLS/PKIX dependency-resolution failure and the separate transform-cache move failure are recorded as
environment/tooling blockers, not passing or failing Kotlin test results.

## Implemented AAOS/PLEOS adapters and remaining device work

### Current production wiring and fail-closed boundary

Automotive builds now construct production adapters for:

- AAOS `RANGE_REMAINING` energy input;
- AAOS gear plus `CarUxRestrictions` motion mapping;
- PLEOS FusedLocation, with Automotive runtime permission request and start gating;
- PLEOS NaviHelper route reads, capability reporting, destination requests, and waypoint requests.

Each adapter still returns `UNSUPPORTED` or `UNAVAILABLE` when its feature, SDK, property, permission, callback, or
validated response is absent. The verified refuel-event stream is not implemented and remains empty. The station
gateway also does not yet supply the verified WGS84/road metrics and `NavigationTarget` needed to authorize an actual
recommendation → navigation command, so that flow remains fail-closed Unsupported. No path substitutes demo numbers,
synthetic events, or fake navigation success.

### PLEOS integration still required

- Execute the wired range, gear/UX, FusedLocation, and NaviHelper adapters on a PLEOS target and record permission,
  callback, timeout, cancellation, and error behavior.
- Provide stable refuel event IDs that remain identical across SDK redelivery and process recreation.
- Complete the gateway handoff for verified WGS84, road/route metrics, station fuel/business freshness, and
  `NavigationTarget`; do not bypass the current Unsupported gate.
- Validate real navigation capability reporting, route identity, waypoint capacity, set-destination/add-waypoint
  calls, correlated callbacks, timeouts, and cancellation/unregistration when pending is invalidated.
- Connect SDK disconnect/reconnect behavior without retaining the last parked/actionable snapshot.
- Confirm the host app owns exactly one runtime/store and does not create a second AssistViewModel for the HUN or Activity.

### Device and product verification still required

- A normal Android notification has not been verified as an approved Pleos-native HUN or as permitted above the vehicle navigation application.
- No actual Pleos device run has verified moving delivery, action latency, route handoff, callback success/failure, permission revocation, foreground-service restoration, boot resume, force-stop behavior, or OEM power management.
- No actual vehicle run has verified Parked/Moving/Unknown transitions, departure cancellation, route invalidation, refuel dedupe, or absence of historical celebration replay.
- Notification channel behavior, driver-distraction review, text truncation/localization, touch target policy, and compact/HUN visual presentation require Pleos UX approval.
- Reduced-motion mapping from Android animator scale to the Pleos environment remains device-unverified.
- Host integration, SDK integration, and device verification must be recorded separately; importing or building this module does not prove any of them.
