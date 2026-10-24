# Batch B speeding-warning component proof
Requirements 09.03/09.04, and sound dependency 10.23/10.24, from the approved app checklist: warn at the first measured speed above a known applicable limit; repeat at 60 seconds of continuous excess; Full speech, Alerts only tone, Muted no audio.

The same 16 scenarios execute against an extracted original controller method from private app `f29c40582394817eb7396eb220cbc287f638a479` and the candidate pure Kotlin policy. Baseline failures against the requested behavior are expected and recorded individually; they are not hidden as successful tests. The new candidate must pass every case; missing/duplicate/skipped cases fail the runner.

The baseline source excerpt is reference/LegacyOriginalMethod.txt. The adapter changes only Android dependencies and the qualified estimated-position check to deterministic equivalents. It uses metric units, zero configured speed margin and beepAlerts=false; its Format.isOverBy expression is copied from the original. Clock, Location and output are test adapters. No combined app is published.

This is component proof: it cannot establish real current road limits, GPS freshness, audible Android TTS delivery, UI red speed indicator, private controller wiring, or legal compliance. The candidate requires callers to supply recent measured speed and trusted current limit evidence; no values are fabricated. The 60-second interval is the user's preference, not UK law. The Highway Code rule124 requires observing applicable road speed limits: https://www.gov.uk/guidance/the-highway-code/general-rules-techniques-and-advice-for-all-drivers-and-riders-103-to-158 .

New implementation is NOT wired into the private app yet. Batch B remains unaccepted; no APK is produced here.
