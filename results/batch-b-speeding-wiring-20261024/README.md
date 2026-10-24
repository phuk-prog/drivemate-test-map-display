# Batch B speeding method wiring proof

[Public CI40071945057](https://github.com/phuk-prog/drivemate-test-map-display/actions/runs/40071945057) passed18/18 at source`0da16361d57ecdb9da771208277565395b0996ea`, no missing/skipped cases. The actual proposed private warnIfSpeeding method runs unchanged with deterministic clock/location/settings/formatter/output adapters and exact tested policy. Method SHA256`67f81dd14cf9b949254bb3c5bf17f7ff831ca12bfff440e7383a73b29e891e6b`; policy SHA256`50db4e4e7ef48303b39af102bff368d221b390769b9291b82794ac19c52f714a`. Full per-case outcomes are in verification.json.

Private candidate staged separately on base9f01c0a1f7154861941eee57fb52a471917b7ce5, head`6bc24cfece9fe03240e6e7e1bb814106058b3ba6`. The exact method/policy match the public-tested source; source review verified newtrip/stop reset insertions and reroute continuity. These insertion callbacks are not runtime-proven by the isolated method fixture.

Important limits: tests prove output dispatch to sinks, not real Android Voice playback. Existing VoiceQueue can suppress a repeated identical alert within30seconds after recovery; switching Full→Alerts-only can retain old alert speech. Those delivery dependencies remain unresolved. The speed UI still has its old display tolerance; speedMargin settings/statistics do not alter this exact-limit warning policy. Upstream shownLimit provenance is unchanged and unproven; category09.13 remains open.

Before7/16→16/16 is separate reduced metric-zero-margin policy comparison, not a baseline for these18newcases. Local/pre-upload executionUNAVAILABLE due failedexecutor. Public CI is post-upload evidence. No private tests, app compilation/APK, main merge or full-category acceptance.
