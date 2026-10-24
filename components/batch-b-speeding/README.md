# Batch B speeding-warning component proof
Requirements 09.03/09.04, and sound dependency 10.23/10.24, from the approved app checklist: warn at the first measured speed above a known applicable limit; repeat at 60 seconds of continuous excess; Full speech, Alerts only tone, Muted no audio.

The same 16 scenarios execute against an extracted original controller method from private app `f29c40582394817eb7396eb220cbc287f638a479` and the candidate pure Kotlin policy. Baseline failures against the requested behavior are expected and recorded individually; they are not hidden as successful tests. The new candidate must pass every case; missing/duplicate/skipped cases fail the runner.

The baseline source excerpt is reference/LegacyOriginalMethod.txt. The adapter changes only Android dependencies and the qualified estimated-position check to deterministic equivalents. It uses metric units, zero configured speed margin and beepAlerts=false; its Format.isOverBy expression is copied from the original. Clock, Location and output are test adapters. No combined app is published.

This is component proof: it cannot establish real current road limits, GPS freshness, audible Android TTS delivery, UI red speed indicator, private controller wiring, or legal compliance. The candidate requires callers to supply recent measured speed and trusted current limit evidence; no values are fabricated. The 60-second interval is the user's preference, not UK law. The Highway Code rule124 requires observing applicable road speed limits: https://www.gov.uk/guidance/the-highway-code/general-rules-techniques-and-advice-for-all-drivers-and-riders-103-to-158 .

New implementation is NOT wired into the private app yet. Batch B remains unaccepted; no APK is produced here.

## Provenance and preflight

Local execution/pre-upload command preflight: **UNAVAILABLE**, executor failed. GitHub connector source/branch binding and source review were completed; post-upload public CI is separate evidence, not retroactive local preflight. The extracted controller method changes the qualified PositionKind expression to its equivalent provider==estimate check. Metric Format expression and stable Voice sound gate are deterministic adapters, not the entire Android runtime. Full default-app speedMargin is not exercised here (the baseline injects zero). Source references at exact app SHA:

- [warnIfSpeeding](https://github.com/phuk-prog/drivemate-app/blob/f29c40582394817eb7396eb220cbc287f638a479/app/src/main/java/com/drivemate/app/controller/NavigationSession.kt#L3449)
- [Format](https://github.com/phuk-prog/drivemate-app/blob/f29c40582394817eb7396eb220cbc287f638a479/app/src/main/java/com/drivemate/app/nav/Format.kt#L37)
- [PositionKind](https://github.com/phuk-prog/drivemate-app/blob/f29c40582394817eb7396eb220cbc287f638a479/app/src/main/java/com/drivemate/app/ui/DrivingDisplayPolicy.kt)
- [Voice stable-state gate](https://github.com/phuk-prog/drivemate-app/blob/f29c40582394817eb7396eb220cbc287f638a479/app/src/main/java/com/drivemate/app/nav/Voice.kt#L238)

The first CI attempt reproduced 8/16 baseline vs16/16 candidate but its overall harness guard wrongly expected repeatAt60000ms to fail. In the legacy method that sample is the first delayed warning, so it passes without proving actual repeat behavior. The guard now checks the reproduced immediate-warning failure and repeatsEveryFurtherMinute failure; test cases and production policy are unchanged. Missing/duplicate/skipped cases still fail the runner. Candidate invalid finite-input/clock-regression branches remain uncovered by this16case subset.

Final review additionally strengthened repeatAt60000ms and repeatsEveryFurtherMinute to assert the initial warning and every expected subsequent warning. This prevents a first late legacy warning being mistaken for a repeat. The final paired denominator stays16; earlier8/16 baseline score is historical and must not be reported as the final strengthened-suite result.
