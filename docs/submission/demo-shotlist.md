# Native demo shot list — capture plan

Record the actual native Android debug build, preserving the visible demo label. Record commit, APK hash, Android/API level and device/emulator identifier in the evidence log. Use synthetic fixture labels and neutral station labels; avoid personal names, real destinations, notifications or API keys. Do not use the web preview as evidence of native integration.

| Shot | Action | What to capture / expected evidence |
| --- | --- | --- |
| 1 | Open normal home before starting demo | Honest missing/unavailable fields and reachable settings; no fabricated live values |
| 2 | Enter the explicitly labelled debug demo | Visible fixture label and distinct session/store; describe all numbers as examples |
| 3 | Load recommendation | Reason → candidate → available actions; companion remains beside message |
| 4 | Load passing and nearby scenarios | Appropriate engine output; avoid implying verified road access from raw catalog data |
| 5 | Load NoAction | No unnecessary refuelling recommendation |
| 6 | Load stale/unavailable data | Explicit uncertainty; no confident fabricated candidate |
| 7 | Trigger failed navigation, then a successful fixture outcome | Failure stays visible; confirmation only follows correlated success; describe SDK result as simulated |
| 8 | Refuel, resend same event, then depart | One celebration; duplicate does not replay; moving cancels broad motion and leaves calm companion |
| 9 | Re-park and rotate/reopen | No old celebration/navigation replay; settings persist only in the appropriate store |
| 10 | Try reserve/motion changes parked, moving and unknown | 20–100 km reserve in 10 km steps; default 50; moving/unknown reject changes |
| 11 | Leave demo and show normal home | Demo data does not become normal session data |
| 12 | Inspect release APK separately | No debug demo entry/implementation; attach automated isolation result |

Only record shots whose implemented controls exist in the final build. If a scenario cannot be captured, mark it blocked or missing in the evidence log. A planned shot is not proof of a passed test.

Separate follow-up recording: explicit monitoring start/stop; location/notification refusal; permission revocation; background/foreground and rotation; process restoration; timeout; boot/update resume notice. Perform these on a supported device/emulator with logs, and record any external SDK or OS restriction rather than editing it out.
