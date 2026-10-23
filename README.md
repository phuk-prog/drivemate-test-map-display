# DriveMate Category 2 — public test contracts

Tests for checklist items **02.01–02.09** (map display, route line, vehicle arrow).

The **application remains private**: `phuk-prog/drivemate-app`. This repository contains synthetic, non-user fixtures, a Kotlin unit-test source file and a result-summary script only. The Kotlin file must be compiled and executed **against the actual app classes** in an authorised private GitHub Actions checkout. Standalone tests in this public repository cannot establish production behaviour.

- `contracts/category2.json`: all nine preferences, expected test identities and visual/real-data limitations.
- `tests/Category2MapDisplayContractTest.kt`: Kotlin logic tests (public tests; not production source).
- `ci/summarize.py`: validates the executed test identities, detects missing outcomes, and records a bounded source-integration check for traffic (without exporting app code).

Execution: A private app-only workflow pins the public commit, downloads these files into its private checkout, runs the actual Android/Kotlin JUnit task, and uploads **only** summary data. Preserve the baseline SHA and results before modifying app behaviour.

**Evidence boundaries:** synthetic coordinates demonstrate geometry calculations but cannot prove legal carriageway choice, live positioning, actual UK road geometry or screenshot output. Unit-tested camera targets are not a rendered-map check. Absent or unverified evidence remains visible, never counted as implemented.
