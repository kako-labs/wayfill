# FUEL PILOT continuation entry

This repository contains the user's approved UI and a reusable Compose UI module. Start with `START-HERE.md` and `docs/APPROVED-BASELINE.md`. The approved animation is the 16-frame expressive revision, code baseline `95d08776d71b284b929f7cd4d3416ef355406efe`, not the earlier 8-frame / 40-frame implementation.

- UI/design sessions: read `docs/UI-SESSION-HANDOFF.md`.
- App/code sessions: read `docs/APP-SESSION-HANDOFF.md` and `docs/ANDROID-INTEGRATION.md`.
- Cross-device immutable handoff: tag `podong-ui-approved-2026-11-10`, GitHub Release with the same name.

Unless the user requests another design change, preserve the approved character identity, broad parked acting, calm driving avatar and permanent presence beside messages. Keep event deduplication, moving/unknown cancellation and reduced-motion behavior. Demo numbers are examples, not production defaults.

Web and Android share the atlas, frame timings and fixed per-clip viewport. Update their data/resources together. The lab's repeat checkbox is preview-only; production refuel gestures are one-shot.

The host application's vehicle, navigation, recommendation and persistence code is not present here. Do not claim that an existing company app or an actual Pleos device has been integrated merely because this library builds. Record host integration and device verification separately.

Checks: `npm test`, `npm run verify`; in an Android SDK/JDK environment, `android/gradlew.bat -p android :fuel-pilot-ui:testDebugUnitTest :fuel-pilot-ui:assembleDebug --console=plain`. Keep machine-specific `android/local.properties`, build caches and generated temporary files out of Git. On this PC work under D.
