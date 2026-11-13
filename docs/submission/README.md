# Submission draft — evidence must be attached before delivery

Confirmed submission identity: **WAYFILL · KA&KO · a team leader and 2 members in total · general division**. Personal contact details and signatures stay outside this repository.

This folder prepares a reviewable WAYFILL submission. It does not certify CI, emulator, provider, or vehicle execution. Fill in the checklist with actual logs and commit identifiers after integration.

- [Proposal and scope](proposal.md)
- [Native demo shot list](demo-shotlist.md)
- [Readiness and submission checklist](readiness-checklist.md)
- [Privacy and data handling](privacy.md)
- [Portable source and verification](portable-source.md)

Ruling for this bounded delivery task: source archives use committed files by default. An explicit working-tree overlay can export edits to committed paths, but newly added files must be reviewed and committed first. This prevents accidental collection of untracked credentials and makes a normal archive reproducible by revision.

Evidence labels used throughout: **Implemented/source** means review source at the eventual submission commit; **Verified** requires a named command/run artifact; **Demo fixture** means synthetic input deliberately restricted to a labelled debug session; **Pending external validation** requires an SDK, credentials, permissions, physical device, or actual CI run. Planned behavior is never a measured result.
