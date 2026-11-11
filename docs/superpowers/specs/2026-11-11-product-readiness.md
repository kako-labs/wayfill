# FUEL PILOT product readiness

The user approved completing all gaps identified against Motion Care. Preserve the approved 72-frame mascot and warm graphite/gold UI. Continue on integrate/podong-approved from f7b576f; no main merge.

## Deliverables

1. A persistent app home with status, last known context and reachable settings even when the assist overlay is hidden. Show missing values honestly. Parked-only motion preference and reserve changes; reserve 20–100 km in 10 km steps, default50, durable and used by the engine. Unknown/moving cannot change them.
2. An explicitly labelled debug-only native demo with a separate store and fixture source. Production never substitutes synthetic input. Exercise recommendation, passing, NoAction, nearby, stale/unavailable, navigation failure/success, refuel/dedup and departure through real engine/coordinator/UI. Release must exclude demo implementation.
3. Compose instrumentation tests on Android35, CI build/lint/unit/web/policy/UI checks with reports and APKs. Source-only portable package and accurate proposal/demo shot list/readiness/privacy docs.
4. One application-owned assist runtime, reused by UI and foreground monitoring service. Explicit user start/stop, location/notification permission handling, persistent opt-in, sticky process restoration without replaying effects; boot/update asks the user to resume instead of violating Android foreground-start restrictions. On timeout/permission failure stop and expose status. Recovery recreates a session from durable state, cancels the old one, and does not replay pending requests or celebrations.
5. Actual-data provider path: a key-free Android HTTPS gateway client, server-side provider credentials, strict schema/timeouts/cache/quota handling. Raw catalog/straight-line distances cannot become verified road/along-route reachability. Missing credentials, verified coordinates, road metrics or SDK capabilities stay explicit Unsupported/Unavailable. No backend deployment, external credentials or vehicle SDK are assumed. Ask about existing provider while independent work proceeds.
6. Distinct new-destination vs existing-route waypoint requests, correlated outcomes, capability limits and tests. Missing SDK remains Unsupported.

## Verification and limits

TDD for behavior. Run whole verifyAll, web tests, server/policy tests, Android instrumentation, release-demo-isolation checks and emulator lifecycle/notification/background probes where possible. One independent final review. Commit/push and verify remote hash; record exact external blocks instead of calling live provider or real vehicle verified. Changes to old stable tags are forbidden.

Official references: Android Compose testing https://developer.android.com/develop/ui/compose/testing ; Android foreground limits https://developer.android.com/develop/background-work/services/fgs/timeout ; Opinet API access/key/call limits https://www.opinet.co.kr/user/custapi/custApiInfo.do . Provider-specific request formats require primary documentation, not guessed projections.
