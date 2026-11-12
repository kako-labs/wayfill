# Fuel Pilot PLEOS/emulator reproduction kit

This is a truthful, smaller evidence kit for Fuel Pilot. It inventories a connected Android Automotive/Pleos target
without changing it, defines bounded scenario gates, and separates what source/tests establish from what an emulator
or physical vehicle establishes. It does not provide a map and does not substitute synthetic data for a real adapter.

## Safety contract

- `scripts/pleos_probe.py` is read-only: no install, launch, force-stop, clear, grant, revoke, service restart, input,
  or vehicle-signal command exists in it.
- `scripts/pleos_e2e.py` prints a read-only capability/status matrix by default. Mutating scenarios require `--apply`;
  debug instrumentation additionally requires `--ack-debug-synthetic`, and physical targets require a separate flag.
- The default invocation is status only. `--apply` is present as the mandatory latch for future state-changing probes,
  but this revision intentionally performs no mutation; JSON always records `apply.performed: false`.
- A target is selected only when exactly one ready device exists, or with `--serial`/`ANDROID_SERIAL`. Offline,
  unauthorized, missing, and ambiguous targets fail closed.
- Host paths are omitted by default. Add `--include-host-paths` only for a local diagnostic that will not be committed.
- Raw unredacted notifications, API keys, SDK binaries, emulator images, local properties, and host paths are not evidence artifacts.

## Run the inventory

```powershell
# Read-only JSON to stdout.
python scripts/pleos_probe.py

# Select an exact target and save a review artifact.
python scripts/pleos_probe.py --serial <adb-serial> --output artifacts/pleos-probe.json

# Explicit future-mutation latch. In this revision it still performs no mutation.
python scripts/pleos_probe.py --serial <adb-serial> --apply

# Read-only scenario capability/status matrix.
python scripts/pleos_e2e.py

# Examples: only on a dedicated target, after reviewing the status matrix.
python scripts/pleos_e2e.py --scenario app-visible-nav-foreground --serial <adb-serial> --apply
python scripts/pleos_e2e.py --scenario permission-revocation --serial <adb-serial> --apply --restore-permission
python scripts/pleos_e2e.py --scenario refuel-event --serial <adb-serial> --apply --ack-debug-synthetic
```

ADB is discovered in this order: `--adb`, `PLEOS_ADB`, `PATH`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`. The probe records:

- adb version and attached target states;
- product/build/SDK, emulator detection, Automotive feature, and PLEOS/UMOS package markers;
- current and available Android users;
- registered car/vehicle services and a bounded `car_service` summary;
- the activity resolved for a `geo:` navigation intent and current focus;
- Fuel Pilot install/version, requested/runtime permission lines, running service evidence, and notification-channel lines.

Exit `0` means one target was observed. Exit `2` means adb was not found. Exit `3` means no unique ready target could
be selected. `OBSERVED` is inventory, not proof that a scenario passed.

## Current production integration boundary

Automotive builds now wire real adapters rather than defaulting every production port to Unsupported:

- AAOS `RANGE_REMAINING` through `AaosVehiclePropertySource`/`AaosRangeEnergyRepository`;
- AAOS gear plus `CarUxRestrictions` through `AaosVehicleEvents`, mapping restricted/non-P states to Moving and
  missing/unsupported/unknown restrictions to Unknown;
- PLEOS FusedLocation through `PleosFusedLocationPort`/`PleosFusedLocationRepository`;
- PLEOS NaviHelper route reads, destination requests, and waypoint requests through
  `PleosNaviHelperPort`/`PleosNavigationRepository`.

Those adapters still return Unsupported or Unavailable when the Automotive feature, SDK, property, permission,
callback, or valid response is absent. More importantly, the station gateway does not yet deliver the verified
WGS84/road metrics and `NavigationTarget` required for a recommendation to authorize a real NaviHelper command.
Therefore actual recommendation → navigation remains deliberately fail-closed as Unsupported. No PLEOS emulator,
Devbox, or physical-device execution has validated these adapters yet.

## Scenario gate matrix

| Scenario | Current executable input | Evidence allowed now | Gate to full PLEOS E2E |
| --- | --- | --- | --- |
| P / D / Unknown | Production AAOS gear + `CarUxRestrictions` adapter exists; debug P/D controls also exist | Source/local adapter and mapping tests; any debug capture must say synthetic | Execute actual P/D/unknown signals on a dedicated PLEOS target |
| App visible / navigation foreground | Android focus can be observed; navigation handler is discovered | Read-only focus inventory; manual target capture | Dedicated bounded driver after a target is available |
| HUN `CATEGORY_NAVIGATION`, two actions | Production notification code and two actions exist; gateway cannot yet produce the verified recommendation/target chain | Source/local test only | Verified gateway inputs, then a real recommendation while monitoring with app backgrounded |
| No action, timeout, dismissal | Policy is source/local-test covered | Source/local test only | Real HUN on target plus a defined timeout observation window |
| Permission revocation | Android can revoke it, but the probe does not mutate | Plan/source only in this kit | Dedicated emulator, `--apply`, exact permission, before/after/restore evidence |
| Car/service restart | AAOS/FusedLocation/NaviHelper adapters exist, but reconnect behavior has not run on PLEOS | Plan/source only | Emulator-only restart acknowledgement plus adapter recovery assertions |
| Route invalidation | Reducer/coordinator and NaviHelper adapter exist; gateway does not yet supply an executable verified target | Source/local test only | Verified `NavigationTarget`, real active route, and documented invalidation input |
| Refuel event | Debug-only synthetic source exists | Clearly labeled debug test only | Verified production refuel source with stable event ID |

Do not mark a blocked row as “emulator passed” merely because its reducer or debug demo passed.
`pleos_e2e.py` returns `BLOCKED` for the source-only/blocked rows even when `--apply` is supplied.

## Bounded target procedure

When a dedicated PLEOS target is available, use this order. Stop at the first failed precondition.

1. Save `pleos_probe.py` JSON and record app commit/APK SHA-256, target serial alias, build fingerprint, current user,
   resolved navigation component, permissions, channels, and car-service availability.
2. Confirm the build type. Production evidence must not show `FUEL_PILOT_SYNTHETIC_ONLY`; debug evidence must show it.
3. Confirm whether this build's gateway supplies verified WGS84, verified road metrics, and a verified
   `NavigationTarget`. If not, expect recommendation → navigation to remain Unsupported and do not bypass the gate.
4. Use the official Pleos Connect Virtual Console for vehicle state only when the installed console version exposes
   the required input. Do not infer or replay Motion Care's private endpoint contract.
5. Run one scenario at a time with a fixed timeout. Capture before/input/after timestamps, focus, notification state,
   app/service state, screenshot/video filename, and operator observation.
6. Restore permission/service/vehicle state explicitly. Re-run the read-only probe and record restoration.
7. Record `PASS` only for directly observed assertions. Otherwise use `BLOCKED`, `NOT_RUN`, or `OBSERVED_NOT_ASSERTED`.

Suggested bounds once drivers exist: 30 seconds for focus and HUN appearance, the product-defined no-action timeout
plus 10 seconds, 45 seconds for permission shutdown, 60 seconds for service recovery, and 30 seconds for route
invalidation. A timeout is a failure or blocker, never an invitation to wait indefinitely.

## Deterministic demo and film checklist

Debug demo mode is not production evidence.

- [ ] Record repository commit and debug APK SHA-256.
- [ ] Run local unit/instrumentation tests before filming; do not claim a target run from them.
- [ ] Start the debug-only demo through the app's visible debug entry or instrumentation, never by adding demo data to
      production adapters.
- [ ] Keep the banner `FUEL_PILOT_SYNTHETIC_ONLY · 합성 데이터 시연 · 실제 차량 아님` visible at the beginning of
      every take and in the slate/title.
- [ ] Reset only the debug demo store when a clean take is required. Do not clear production/user data on a shared target.
- [ ] Fixed take order: Unknown production fail-closed slate → debug Parked recommendation → Moving cancellation →
      Parked again → one refuel event → duplicate refuel suppression → navigation success/failure → no-action scenario.
- [ ] For navigation foreground footage, use the resolved vehicle navigation app. Fuel Pilot remains a companion surface;
      it does not render an in-app map.
- [ ] Record exact expected UI text/action before each take; abort if the selected build differs.
- [ ] One-shot refuel gestures stay one-shot. Do not use the web lab's repeat checkbox as production behavior.
- [ ] End with an explicit card: “synthetic debug demo; PLEOS emulator integration and physical vehicle unverified” unless
      a dated verification record proves otherwise.

## Evidence record shape

Each run should identify `source_commit`, `apk_sha256`, `probe_json`, `scenario`, `started_at_utc`, `ended_at_utc`,
`input_source`, `expected`, `observed`, `result`, `artifacts`, `restoration`, and `limitations`. Keep host integration,
PLEOS-emulator execution, Devbox, and physical-device evidence as separate rows.

Official scope references: [PLEOS Resources & Emulator](https://developers.pleos.ai/playground/discover/resources) and
[Developer Journey](https://developers.pleos.ai/playground/discover/developer-journey). Benchmark rationale and the
Motion Care paths/commits are in [MOTION-CARE-BENCHMARK.md](../MOTION-CARE-BENCHMARK.md).
