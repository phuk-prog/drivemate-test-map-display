# Batch B guidance comparison — 24 October 2026

Public CI [40065679236](https://github.com/phuk-prog/drivemate-test-map-display/actions/runs/40065679236), exact tested commit `a8670eeacf14b201493ec415230e354f9bcfd10e`.

Same expanded 20-case suite: **baseline 13/20 → candidate 20/20**. Wording cases: **8/15 → 15/15**. Existing Now-cue policy: **5/5 → 5/5**. No missing or skipped cases. The historical Category 03 8/8 diagnostic suite is different and is not this denominator. Source hashes and every outcome appear in comparison.json.

Seven owner-preference mismatches were corrected in the public candidate wording component: explicit numeric lane counts, positive naming of usable edge lanes, and “Use the right 3 lanes to continue straight”. This public candidate is not yet integrated into the private app. The public baseline Wording.kt and DrivingDisplayPolicy.kt match app source `f29c40582394817eb7396eb220cbc287f638a479`; dependencies are existing public individual components. Published Models.kt is a subset, not the complete private app model file.

Before upload, connector source/binding/diff/workflow and independent review were completed. Local syntax/test execution was UNAVAILABLE because the execution environment failed. The later CI result is separate exact-SHA post-upload proof.

No rendered-card, actual arrow-visibility transition, full-controller presentation, Android Auto host, live provider or legal road-marking proof is supplied. 03.01, 03.02 whole UI and 16.01 remain unverified; Batch B is not accepted. No private tests, APK or combined app published.
