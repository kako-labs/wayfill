# WAYFILL proposal — draft

WAYFILL helps a driver understand when fuel may be insufficient for the trip and choose a refuelling stop with a small, persistent companion. The intended order is reason, candidate station, action. Navigation rendering belongs to the vehicle navigation app; WAYFILL does not add an in-app map.

## Product scope and evidence

| Area | Submission claim allowed after source review | Evidence still required |
| --- | --- | --- |
| Decision logic | Deterministic fuel/reserve decisions and explicit unavailable states | Unit-test logs at submission commit |
| Native UI | Compose home, assist and settings; approved warm graphite/gold design and 72-frame companion atlas | Actual screenshots and Android35 instrumentation report |
| Driving safeguards | Parked-only policy changes, calm moving/unknown presentation, duplicate-event suppression | Policy tests and real lifecycle/motion probes |
| Native demo | Explicit debug fixture scenarios exercise the engine/coordinator/UI; fixture state is isolated from normal operation | Debug recording plus release isolation report |
| Background monitoring | Application-owned runtime and opt-in monitoring/recovery behavior are implementation targets | Permission, notification, timeout, process-death and boot probes |
| Provider boundary | Key-free Android gateway boundary; credentials kept on the server; unconfigured access stays explicit | Server/client tests; deployment and approved real-provider access remain external |
| Navigation integration | Destination and waypoint requests report capability limits and correlated outcomes | Real vehicle SDK/device validation remains external |

This table is an evidence plan, not an assertion that the in-progress integration has passed. Use the readiness checklist to record exact results. Historical UI approval is documented in [the approved baseline](../APPROVED-BASELINE.md); it does not establish current product or vehicle verification.

## What the demo can show

Show why a stop is suggested, the candidate action, safe refusal when motion or data is unknown, honest navigation failure, confirmation only after a correlated success, and one-time refuel acknowledgement that stops on departure. A debug fixture can demonstrate these flows without representing the fixture as live fuel, location, price or navigation data.

## Boundaries and next validation

Missing vehicle SDK or permission yields Unsupported. Missing credentials, trusted coordinates, verified road distances or along-route reachability yields Unsupported/Unavailable. A raw station catalog or straight-line distance does not establish that a driver can reach a station on the route. Live-provider deployment and credentials, real vehicle fuel/location inputs, navigation integration and physical-device validation remain external work until separately evidenced.

No savings, range-accuracy, distraction reduction, adoption, safety or emissions metric is claimed. Those outcomes need a defined study and measured results. Submission text must not imply certification or a completed production launch.
