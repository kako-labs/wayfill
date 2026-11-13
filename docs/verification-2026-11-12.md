# Verification record · 2026-11-12

Scope: reproducible PLEOS/emulator evidence tooling and documentation only. No Android/Kotlin/web production code was
changed by this work. No PLEOS emulator, Devbox, or physical vehicle was attached or executed.

## Evidence matrix

| Capability | Source-covered | Local executed | PLEOS emulator executed | Physical device |
| --- | --- | --- | --- | --- |
| AAOS range and motion: `RANGE_REMAINING`, gear + `CarUxRestrictions`, P/D/Unknown mapping and cancellation | Yes; production adapters and Kotlin tests/source | Source/local test lane only | **Not run** | **Unverified** |
| PLEOS FusedLocation adapter | Yes; SDK adapter, permission/support/error mapping and repository tests | Source/local test lane only | **Not run** | **Unverified** |
| PLEOS NaviHelper route/destination/waypoint adapter | Yes; SDK adapter, permission/timeout/result mapping and repository tests | Source/local test lane only | **Not run** | **Unverified** |
| HUN `CATEGORY_NAVIGATION` and Guide now/Later actions | Yes; `MonitoringService.kt`, `RecommendationActionReceiver.kt`, related tests | Source/test lane only | **Not run** | **Unverified** |
| No-action/dismissal policy | Yes; policy/view-model tests | Source/test lane only | **Not run** | **Unverified** |
| Environment inventory/probe | Yes; `scripts/pleos_probe.py` and dedicated unit tests | Executed on host: adb discovered, no attached targets | **Not run** | **Unverified** |
| App visible versus navigation foreground | Procedure defined | Not executed without a target | **Not run** | **Unverified** |
| Permission revocation and restoration | Procedure/gate defined | Not executed without a dedicated target | **Not run** | **Unverified** |
| Car-service restart/recovery | AAOS/FusedLocation/NaviHelper adapters exist; reconnect behavior is source-unproven on PLEOS | Not executed without a dedicated target | **Not run** | **Unverified** |
| Production recommendation → NaviHelper execution / route invalidation | NaviHelper adapter exists, but the gateway does not supply verified WGS84/road metrics/`NavigationTarget`; flow fails closed Unsupported | Source/local gate coverage only | **Not run** | **Unverified** |
| Production refuel event | Debug source exists; production source is not connected | Debug is not promoted to production evidence | **Not run** | **Unverified** |
| Deterministic debug demo/film flow | Checklist and existing debug instrumentation paths | Not filmed in this work | **Not run** | Not applicable / **Unverified** |

## Executed in this work

These are host/local results only:

| Command | Result |
| --- | --- |
| `python -m py_compile scripts/pleos_probe.py scripts/pleos_e2e.py` | **PASS** |
| `python -m unittest discover -s scripts/tests -p 'test_pleos_*.py' -v` | **PASS**, 11 tests |
| `python -m unittest discover -s scripts/tests -q` | **PASS**, 23 tests |
| `python scripts/pleos_probe.py` | Expected fail-closed result: `BLOCKED_DEVICE_SELECTION`; adb found, zero ready targets |
| `python scripts/pleos_e2e.py` | **PASS** read-only status generation; no adb mutation |
| `python scripts/dev.py doctor` | First run blocked because SDK env was unset; **PASS** after deriving the SDK root from adb for the process only |
| `python scripts/dev.py verify` | **BLOCKED** during `:app:checkDebugAarMetadata`: TLS certificate validation failed while resolving `ai.pleos.playground:NaviHelper:3.0.5` from Google/Pleos Maven. No test or device-run claim is made from this command. |
| `git diff --check` on the owned files | **PASS** |

adb `1.0.41` / platform-tools `37.0.1` was discoverable on `PATH`; `adb devices -l` listed zero ready targets
(later checks saw offline emulator entries, which remain ineligible). The
committed record intentionally omits the machine-specific adb and SDK paths.

## Truth constraints

- A local JVM/unit/instrumentation pass is not a PLEOS-emulator pass.
- A debug synthetic demo pass is not a production adapter pass.
- An Android Automotive emulator pass is not a Devbox or physical-vehicle pass.
- An imported/buildable UI does not prove host application integration.
- Source presence and production wiring of AAOS/FusedLocation/NaviHelper adapters does not prove SDK availability,
  permission, callback behavior, reconnection, or successful runtime execution on PLEOS.
- Unsupported remains the required production result when an SDK or permission is unavailable.
- Recommendation → navigation must also remain Unsupported until the gateway supplies verified WGS84, verified road
  metrics, and a verified `NavigationTarget`; adapter presence does not authorize bypassing that contract.

## Remaining target gates

1. Connect a dedicated Pleos Connect emulator, save a read-only probe JSON, and review its user, car service,
   navigation resolver, permissions, and channels.
2. Execute the wired AAOS range/gear+UX, FusedLocation, and NaviHelper adapters and record permission/support/error paths.
3. Complete the gateway contract for verified WGS84, verified road metrics, and `NavigationTarget`; keep
   recommendation → navigation fail-closed until then.
4. Add explicitly gated scenario drivers only for documented inputs that exist on that emulator version.
5. Execute and record HUN/action/timeout, permission, restart, invalidation, and refuel scenarios separately.
6. Validate on Devbox/physical hardware; keep that evidence separate from emulator results.

Benchmark: [MOTION-CARE-BENCHMARK.md](MOTION-CARE-BENCHMARK.md). Reproduction kit:
[pleos-emulator/README.md](pleos-emulator/README.md).
