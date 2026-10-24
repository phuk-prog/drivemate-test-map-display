# Batch B speeding — immutable public comparison

- Exact tested source SHA: `2f5441161aea247ef8dc5e6e75f2585ea144023f`.
- [Public CI 40065795005](https://github.com/phuk-prog/drivemate-test-map-display/actions/runs/40065795005): success.
- Same16cases, extracted metric-zero-margin original controller semantics: **7/16 before →16/16 candidate after**; no missing/skipped cases. See verification.json for every outcome and source hash.
- App reference: `f29c40582394817eb7396eb220cbc287f638a479`. Candidate policy hash `50db4e4e7ef48303b39af102bff368d221b390769b9291b82794ac19c52f714a`.

This denominator covers speeding-warning component requirements09.03/09.04 and sound-state dependency only. Android clock/location/output sinks were adapted, metric units and injected zero margin used; it is not the entire original app defaults or a device/TTS/live-road/legal test. No private app wiring, APK or category acceptance. Caller must establish trusted current limit and recent measured speed. Local execution/pre-upload command preflight UNAVAILABLE due failedexecutor; public CI is post-upload evidence, not retroactive preflight.

The initial harness guard failed because it assumed the first late legacy warning could not satisfy a repeat sample. Repeat tests were then strengthened to assert the initial warning and each repeat. Final7/16→16/16 supersedes historical8/16 outcomes. This docs-only evidence commit preserves exact tested source without triggering another unchanged test run.
