# FUEL PILOT Agent Guide

## Product architecture

- Work on a feature branch; do not commit directly to main.
- Keep the decision engine pure and deterministic.
- Real adapters must return Unsupported when their SDK or permission is unavailable. Never substitute synthetic data in a real build.
- Do not add an in-app map. Navigation rendering belongs to the vehicle navigation app.
- Do not commit API keys, SDK binaries, local.properties, caches, or machine-specific paths.
- Run `python scripts/dev.py doctor` and `python scripts/dev.py verify` before handoff.

## Approved UI baseline

Start with `START-HERE.md` and `docs/APPROVED-BASELINE.md`. The approved animation is the 16-frame expressive revision, code baseline `95d08776d71b284b929f7cd4d3416ef355406efe`, not the earlier 8-frame / 40-frame implementation.

- UI/design sessions: read `docs/UI-SESSION-HANDOFF.md`.
- App/code sessions: read `docs/APP-SESSION-HANDOFF.md` and `docs/ANDROID-INTEGRATION.md`.
- Cross-device immutable handoff: tag `podong-ui-approved-2026-11-10` and the GitHub Release with the same name.

Unless the user requests another design change, preserve the approved character identity, broad parked acting, calm driving avatar, and permanent presence beside messages. Keep event deduplication, moving/unknown cancellation, and reduced-motion behavior. Demo numbers are examples, not production defaults.

Web and Android share the atlas, frame timings, and fixed per-clip viewport. Update their data/resources together. The lab's repeat checkbox is preview-only; production refuel gestures are one-shot.

An imported UI build does not prove that the host application or an actual Pleos device has been integrated. Record host integration and device verification separately.

UI checks: `npm.cmd test`, `npm.cmd run verify`; in an Android SDK/JDK environment, `.\\gradlew.bat :feature:assist:testDebugUnitTest :feature:assist:assembleDebug --no-daemon --max-workers=1 --console=plain`. Keep machine-specific `local.properties`, build caches, and generated temporary files out of Git.
