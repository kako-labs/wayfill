# Readiness and submission checklist

Status starts **pending**. Check a box only after attaching evidence for the exact submission revision. A green historical run does not cover new changes. No device/CI/provider execution is claimed by this draft.

## Reproducible verification

- [ ] Record branch, full commit SHA and clean/dirty status. Preserve old approved tags unchanged.
- [ ] `python scripts/dev.py doctor` passes with JDK17 and Android SDK35.
- [ ] `python scripts/dev.py verify` passes: root verifyAll includes build, lint, units and architecture checks.
- [ ] `python -m unittest discover -s scripts/tests -v` passes.
- [ ] `python -m unittest discover -s server/tests -v` passes.
- [ ] `npm test` and `npm run verify` pass.
- [ ] `python scripts/ui_tests.py` passes on managed pixel2api35, or `python scripts/ui_tests.py --connected` passes on a recorded Android35 device/emulator.
- [ ] `:app:assembleRelease` passes and `python scripts/check_demo_isolation.py --apk app/build/outputs/apk/release/app-release-unsigned.apk` passes.
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

- [x] Submission identity approved: WAYFILL, KA&KO, leader 송호현, 2 members, general division (2026-11-13).
- [ ] Artwork use and submission/publication scope approved by the owner.
- [ ] Confirm provider terms, source attribution wording, cache retention and display requirements before live use. Do not infer permission from an API sample.
- [ ] Confirm vehicle SDK distribution and demo-recording permissions; no SDK binary in source delivery.
- [ ] Inventory third-party dependencies/licenses and the AI-generated companion assets; retain applicable notices and [asset QA notes](../../web/assets/animation/QA-NOTES.md).
- [ ] No real person names, addresses, vehicle identifiers, private notifications, provider keys or internal endpoints appear in screenshots, logs or archive.
- [ ] Review the complete tracked tree and source archive for secrets; filename filtering is not a comprehensive secret scanner.
- [ ] Keep production keys only in server-side secret configuration; never in Android BuildConfig, resources, web assets, Git or submission ZIP.
- [ ] Record APK SHA256, package/version, signing status and installation instructions. CI release APK is unsigned; production signing/distribution is a separate controlled step.
- [ ] Review proposal/demo narration against actual evidence; no invented outcomes or completed-device claims.

## Evidence log template

| Claim/check | Exact command or procedure | Commit / APK SHA256 | Environment / date | Result / report link | Remaining blocker |
| --- | --- | --- | --- | --- | --- |
| Example entry (replace) | command or device action | pending | pending | pending | pending |

Use **pass**, **fail**, **blocked**, or **not run** with the observed reason. Preserve failed evidence and explain any rerun. Do not substitute a planned checklist for a verification result.

