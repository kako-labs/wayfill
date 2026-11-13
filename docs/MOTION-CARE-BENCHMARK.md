# Motion Care evidence-tooling benchmark

Inspected 2026-11-12 with authenticated, read-only `gh` access to the private repository
[`kako-labs/motion-care`](https://github.com/kako-labs/motion-care). The inspected `main` head was
`60ffd77ba3187d3b89a5cfefb3cca3ac37db9ee2`. This comparison describes repository evidence; it does not
transfer Motion Care's emulator or physical-device results to WAYFILL.

## Exact strengths and paths

| Strength | Motion Care evidence | WAYFILL decision |
| --- | --- | --- |
| One dated emulator ledger records environment, limitations, results, reproduction commands, logs, and screenshots | [`docs/pleos-emulator/README.md`](https://github.com/kako-labs/motion-care/blob/60ffd77ba3187d3b89a5cfefb3cca3ac37db9ee2/docs/pleos-emulator/README.md) | Adopt a smaller ledger in `docs/pleos-emulator/README.md`, with explicit evidence lanes. |
| Disruptive flows have dedicated scripts and bounded expected results | `docs/pleos-emulator/tools/recording_e2e.py`, `auto_record_e2e.py`, `car_service_restart_e2e.py`, `speed_permission_e2e.py` | Adopt the scenario boundaries and fail-closed preconditions. Do not copy commands until a WAYFILL adapter has the matching documented target/runtime input. |
| Filming is reproducible rather than improvised | [`docs/pleos-emulator/tools/film_takes.py`](https://github.com/kako-labs/motion-care/blob/60ffd77ba3187d3b89a5cfefb3cca3ac37db9ee2/docs/pleos-emulator/tools/film_takes.py) | Adopt a deterministic checklist and a restore/preflight concept. WAYFILL labels all synthetic debug footage. |
| Emulator evidence is separated from host tests and real-vehicle limits | `docs/testing.md`, `docs/verification.md`, and the emulator ledger's “환경과 한계” section | Adopt four lanes: source-covered, locally executed, PLEOS-emulator executed, physical-device unverified. |
| Environment behavior is probed before claims are made | `docs/pleos-emulator/environment-probe/`, `logs/environment_probe.log`, `logs/avd-config.ini` | Adopt a standard-library, APK-free, read-only ADB inventory in `scripts/pleos_probe.py`. |
| Evidence artifacts are named and retained | `docs/pleos-emulator/logs/`, `shots/`, and tool-specific summaries | Adopt JSON probe output and dated verification notes; do not commit host paths or raw sensitive notification dumps. |

Motion Care's ledger is especially good at recording negative evidence: no physical-device claim, inability to
verify map-route obstruction, emulator step-input limitations, car-service restarts under host load, and settings
task residue between runs. Those caveats are part of the benchmark, not defects to hide.

## Recent commits checked

| Commit | Date (UTC) | Exact paths/relevance |
| --- | --- | --- |
| [`60ffd77ba318`](https://github.com/kako-labs/motion-care/commit/60ffd77ba3187d3b89a5cfefb3cca3ac37db9ee2) `feat: Pleos 규격 앱 아이콘과 카드 아이콘으로 교체 (#42)` | 2026-11-12 05:04 | Updated the production manifest/service resources and added emulator screenshots plus an 18-line ledger update. Shows that visual claims were re-observed on the target surface. |
| [`ce2b79d3abef`](https://github.com/kako-labs/motion-care/commit/ce2b79d3abefa023ce324921ee15556ffa44084c) `docs: 영상 촬영 테이크를 재현하는 주행 도구 추가 (#41)` | 2026-11-11 17:28 | Added `docs/pleos-emulator/tools/film_takes.py` (243 lines) and linked it from the ledger. This is the direct model for deterministic takes. |
| [`f87975ff608c`](https://github.com/kako-labs/motion-care/commit/f87975ff608cfb32b027c4520ac2c59f05db4f4e) `feat: 영상용 3주치 운행 기록 생성 도구 추가 (#36)` | 2026-11-11 14:35 | Added `docs/pleos-emulator/tools/demo_history.py` (963 lines). Useful discipline, but too large and data-seeding-heavy for WAYFILL's smaller kit. |
| [`7ebd33484b1d`](https://github.com/kako-labs/motion-care/commit/7ebd33484b1d44c718d241489e58a18a0422622c) `docs: CI 대신 로컬 검증으로 병합하도록 문서 수정 (#39)` | 2026-11-11 16:38 | Updated `docs/testing.md`, `CONTRIBUTING.md`, and `CLAUDE.md` to state that CI was disabled due to usage. Demonstrates honest separation of configured CI from executed CI. |
| [`642b501c177f`](https://github.com/kako-labs/motion-care/commit/642b501c177fdbd464c95ecd8f444e66c8e28077) `fix: 앱을 막 띄운 홈의 실내 온도를 한 번 더 읽음 (#32)` | 2026-11-10 19:25 | Changed `app/MainHost.kt` to retry a missed first SDK read. Relevant to WAYFILL's wired AAOS/PLEOS adapters, whose recovery behavior still needs target execution. |

## Adopted, adapted, and blocked

Adopted now:

- exact target selection: one ready device or explicit `--serial`/`ANDROID_SERIAL`;
- read-only discovery from `PATH`, `PLEOS_ADB`, `ANDROID_HOME`, or `ANDROID_SDK_ROOT`, with no personal path in source;
- Automotive/PLEOS markers, current user, app package, car service, navigation resolver, permissions, services, and notification-channel inventory;
- bounded output, timeouts, machine-readable JSON, and no `dumpsys notification --noredact`;
- a deterministic demo/film checklist that labels the debug synthetic activity and never substitutes it for the
  production AAOS/PLEOS adapters;
- a verification matrix that never promotes source coverage to emulator or device execution.

Adapted rather than copied:

- Motion Care assumes user `10`, a fixed `ai.umos...` navigation component, and a known Virtual Console HTTP
  endpoint. WAYFILL discovers the user and navigation handler instead.
- Several Motion Care scripts mutate immediately after invocation. WAYFILL reserves `--apply` as the mandatory
  state-change latch; the current probe implements no mutation and reports `performed: false` even when requested.
- Motion Care sometimes reads app databases or seeds film history. WAYFILL does not seed production data or use
  a real-build fallback. Synthetic inputs remain inside the debug-only activity and instrumentation tests.

Implemented in source but still awaiting target/runtime evidence:

- AAOS `RANGE_REMAINING` plus gear/`CarUxRestrictions` acquisition and motion mapping;
- PLEOS FusedLocation acquisition;
- NaviHelper route reads, destination requests, and waypoint requests;
- permission, SDK-support, timeout, retry, cancellation, and Unsupported/Unavailable failure mapping for those adapters.

Still blocked at the current end-to-end boundary:

- production refuel-event input;
- recommendation-to-navigation execution: the gateway does not yet deliver the verified WGS84/road metrics and
  `NavigationTarget` required to authorize a real NaviHelper command, so this flow fails closed as Unsupported;
- a production recommendation capable of raising and timing out a HUN over navigation for the same gateway reason;
- car-service resubscription and NaviHelper route-invalidation execution on a PLEOS target;
- Devbox or physical-vehicle validation.

The public PLEOS pages confirm that the Pleos Connect Emulator provides the system UI/runtime and that the Virtual
Console simulates vehicle states, events, and properties. They also describe NaviHelper capabilities such as setting
destinations and adding/removing routes. They do not publicly document a stable shell/HTTP vehicle-injection contract,
so this kit does not invent one: [Resources & Emulator](https://developers.pleos.ai/playground/discover/resources),
[Developer Journey](https://developers.pleos.ai/playground/discover/developer-journey).
