# Readiness and submission checklist

Status starts **pending**. Check a box only after attaching evidence for the exact submission revision. A green historical run does not cover new changes. No device/CI/provider execution is claimed by this draft.

## Reproducible verification

- [ ] Record branch, full commit SHA and clean/dirty status. Preserve old approved tags unchanged.
- [ ] `python scripts/dev.py doctor` passes with JDK17 and Android SDK35.
- [x] `python scripts/dev.py verify` passes: root verifyAll includes build, lint, units and architecture checks. (2026-11-14, see evidence log)
- [ ] `python -m unittest discover -s scripts/tests -v` passes.
- [x] `python -m unittest discover -s server/tests -v` passes. (2026-11-14: 42 tests, local and the deployed gateway host)
- [ ] `npm test` and `npm run verify` pass.
- [ ] `python scripts/ui_tests.py` passes on managed pixel2api35, or `python scripts/ui_tests.py --connected` passes on a recorded Android35 device/emulator.
- [x] `:app:assembleRelease` passes and `python scripts/check_demo_isolation.py --apk app/build/outputs/apk/release/app-release-unsigned.apk` passes. (2026-11-14, run on the signed `apk/KA&KO_WAYFILL_release.apk`)
- [ ] CI run link and retained report/APK artifact identifiers match the submission SHA. Check individual job outcomes; an uploaded report alone does not prove a pass.
- [ ] Verify source ZIP contents, SHA256 and build instructions on a second machine/extracted directory. Never include local.properties or credentials.

## Native behavior and external limits

- [ ] Home/settings remain reachable without an assist overlay; missing values are honest.
- [ ] Parked reserve changes (20–100 in steps of 10, default 50) persist and reach the engine; moving/unknown changes are rejected.
- [ ] Reduced motion, duplicate refuel events, departure cancellation and no replay after rotation/process recovery are observed.
- [ ] Debug demo is visibly labelled, uses isolated state and cannot leak fixtures into normal runtime or release.
- [ ] Monitoring start/stop, location/notification refusal, permission revocation, timeout and background lifecycle probes have logs.
- [ ] Boot/update requests explicit resumption; no forbidden foreground-service start is assumed.
- [ ] Provider missing-key, malformed response, stale data, timeout, cache and quota behavior has test evidence.
- [ ] Missing verified coordinates/road metrics/SDK capability stays Unsupported/Unavailable.
- [ ] Real provider approval, server deployment, SDK licensing and physical vehicle validation are either evidenced or explicitly listed as blocked external work.

## Approval, attribution and keys

- [x] Submission identity approved: WAYFILL, KA&KO, team leader, 2 members, general division (2026-11-13).
- [ ] Artwork use and submission/publication scope approved by the owner.
- [ ] Confirm provider terms, source attribution wording, cache retention and display requirements before live use. Do not infer permission from an API sample.
- [ ] Confirm vehicle SDK distribution and demo-recording permissions; no SDK binary in source delivery.
- [ ] Inventory third-party dependencies/licenses and the AI-generated companion assets; retain applicable notices and [asset QA notes](../../web/assets/animation/QA-NOTES.md).
- [ ] No real person names, addresses, vehicle identifiers, private notifications, provider keys or internal endpoints appear in screenshots, logs or archive.
- [ ] Review the complete tracked tree and source archive for secrets; filename filtering is not a comprehensive secret scanner.
- [ ] Keep production keys only in server-side secret configuration; never in Android BuildConfig, resources, web assets, Git or submission ZIP.
- [x] Record APK SHA256, package/version, signing status and installation instructions. CI release APK is unsigned; production signing/distribution is a separate controlled step. (Evidence log below; install steps in the root README.)
- [ ] Review proposal/demo narration against actual evidence; no invented outcomes or completed-device claims.

## Evidence log template

| Claim/check | Exact command or procedure | Commit / APK SHA256 | Environment / date | Result / report link | Remaining blocker |
| --- | --- | --- | --- | --- | --- |
| Full verify | `ANDROID_HOME=... python3 scripts/dev.py verify` | code at `45c49ae` | macOS, JDK 17, 2026-11-14 | pass (BUILD SUCCESSFUL, verifyAll) | none |
| Gateway server tests | `python3 -m unittest discover -s server/tests` | `a4af995` | local Python 3.14 and gateway host Python 3.12, 2026-11-14 | pass, 42 tests | none |
| Submission APK | `./gradlew :app:assembleRelease`, copied to `apk/KA&KO_WAYFILL_release.apk` | SHA256 `7aa5af253f959ab1193839aaf8ef3aeca5948d68fb2079375b06156a69418292` | 2026-11-14 | pass: `dev.fuelpilot` 1.0.0 (versionCode 3), signed CN=WAYFILL, not debuggable, gateway `https://wayfill.quickkit.net` | none |
| Demo isolation | `python3 scripts/check_demo_isolation.py --apk "apk/KA&KO_WAYFILL_release.apk"` | same APK | 2026-11-14 | pass: no demo package, launcher or synthetic marker | none |
| Release APK on the emulator | fresh install, 모니터링 시작 → allow location, car information, notifications; 설정 › 휘발유; range 60 km on the Virtual Console; 바로 안내 | same APK | Pleos Connect emulator v2.1.0, 2560×1440, 2026-11-14 | pass: nearby recommendation from the live gateway (Opinet call counted on the server), navigation started to the station, no crash in the crash buffer | real vehicle not tested; the electric emulator has no fuel-level signal, so refuel detection is not observable |
| Demo video | real app recorded on the emulator, refuel scenes from the debug demo mode (labelled) | debug build of the same code | 2026-11-14 | frame sheets checked; narrated cut whisper check 18/18 | see `demo-video/README.md` |

Use **pass**, **fail**, **blocked**, or **not run** with the observed reason. Preserve failed evidence and explain any rerun. Do not substitute a planned checklist for a verification result.

