# Portable source and verification

Requirements: Git for export; Python3.10+ for helpers; Node for web checks; JDK17, Android SDK35 and the checked-in Gradle wrapper for Android. Dependency downloads require network access on a fresh machine. Android SDK paths belong in environment variables or a local untracked local.properties. No credentials or vehicle SDK are bundled.

## Export a reviewed revision

Run from the repository root after committing the reviewed source:

```text
python scripts/export_source.py ../fuel-pilot-source.zip --ref HEAD
```

The default reads committed bytes using git archive. It never collects untracked files or local edits. It filters Git metadata, caches, build outputs, local.properties, .superpowers, environment files, common credentials/private-key files and distributable binaries. The Gradle wrapper JAR remains because it is a required build bootstrapper, not a vehicle SDK. Git export-ignore attributes also apply. Source symlinks are omitted rather than followed. Review the printed count and actual archive contents before sharing.

The exporter overrides host `core.autocrlf`/`core.eol` when making the archive, while preserving explicit repository attributes. POSIX wrappers remain LF even when exported on Windows; Windows batch wrappers follow `*.bat eol=crlf`. The `--working-tree` option intentionally copies local bytes, so it does not promise normalized line endings.

If an already committed source path needs an explicitly reviewed local edit in a temporary archive:

```text
python scripts/export_source.py ../fuel-pilot-working-source.zip --ref HEAD --working-tree
```

This overlays current bytes only at paths present in the selected revision. Deleted paths are omitted. New files, even staged additions absent from that revision, are excluded: review and commit them before a complete export. Record the selected ref and that the archive contains working edits; it is not a reproducible committed snapshot. Untracked credential files are never discovered by a recursive copy. The helper does not modify Git's index or commits.

Filename filters and a private-key marker guard do not detect every possible secret. Review tracked files, revision contents, archive listing and extracted contents for keys and internal/personal data. Do not publish a source archive solely because the helper succeeded. Do not use the working-tree flag to package an unreviewed session.

## Verify the extracted copy

A source ZIP deliberately has no .git directory, Android SDK, local properties, Gradle caches, provider credentials, APK/AAR or installed dependencies. Exporting again requires a Git checkout; building/testing the extracted source does not.

```text
python scripts/dev.py doctor
python -m unittest discover -s scripts/tests -v
python -m unittest discover -s server/tests -v
npm test
npm run verify
python scripts/dev.py verify
python scripts/ui_tests.py
```

The managed device task is `:app:pixel2api35DebugAndroidTest` and needs Android35 emulator support with virtualization. Alternatively start an Android35 emulator/device and use `python scripts/ui_tests.py --connected`, which runs `:app:connectedDebugAndroidTest`. Neither helper treats a missing SDK/emulator as a pass; Gradle's nonzero result is returned to the caller. The connected form can run on other API levels, so record/confirm API35 when claiming Android35 coverage.

Build release with `./gradlew :app:assembleRelease` on Unix or `.\gradlew.bat :app:assembleRelease` on Windows, then run:

```text
python scripts/check_demo_isolation.py --apk app/build/outputs/apk/release/app-release-unsigned.apk
```

CI workflow [verify.yml](../../.github/workflows/verify.yml) runs verification and a separate KVM-enabled Android35 managed-device job. Reports and review APK/source artifacts upload even when a preceding check fails; inspect job outcomes. APKs are review artifacts, and release APKs are unsigned. Their existence alone does not establish production signing, installation, vehicle compatibility, live provider access or successful tests.

Record SHA256 with `Get-FileHash ../fuel-pilot-source.zip -Algorithm SHA256` on PowerShell or `sha256sum ../fuel-pilot-source.zip` on Linux. Retain the full source revision, APK hash and report links in the [readiness evidence log](readiness-checklist.md).
