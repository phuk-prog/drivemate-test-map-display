# Batch A — viewport-aware selection of *published* map regions

The app previously selected the smallest map region containing the viewport centre, even when a larger **already-published** regional archive would cover the whole screen. That can produce a map surrounded by blank space at city-overview scale. It is **one potential contributor** to the map appearance reported by the owner; diagnosing the specific installed file and its tiles is still needed.

This public module compiles the **exact** production file `PublishedRegionCatalog.kt` from app candidate SHA `6129a03caf2b77d72fb7ff77718bd8b220bfd4d1`, Git blob `2bbf4b3ee5b7a5f11a235775d717d4cdde2f0c0f`.

Twelve JUnit boundary/malformed input tests exercise centre-only baseline selection, viewport coverage selection, partial-published fallback, and missing/invalid location safety. Synthetic rectangles are **not** claims about UK road boundaries. These are isolated component tests, not the connected MapLibre renderer, the actual release catalog, validated UK map coverage or app integration.

No actual user screenshot, precise location, private app code, APK or API credentials are published. Current partial map publisher https://github.com/phuk-prog/DriveMate-Map must separately complete needed regions. App draft PR #10 integrates the new selection into the actual MapController, MapData and region-catalog wiring. Do **not** mark map Category 02 passed without Android rendered-map comparison and replay robot.

## Additional partial-viewport coverage fix (23 October 2026)

The actual public release catalogue [audit 40030682269](https://github.com/phuk-prog/drivemate-test-map-display/actions/runs/40030682269) found 19 published regions, explicitly partial UK coverage, and no single published region covering a generic broad Manchester overview. Merely selecting the smallest focus region therefore conceals more already-published geometry at broad zooms.

Candidate app `d93426df8c17634f917c90fe3fa53dad6f9dac88` preserves the **smallest fully covering** region when one exists; when none covers the entire requested viewport, it selects the **largest available overlap** that still contains the focus instead of falling back to the smallest centre region. No tile, route, or legal road fact is fabricated.

The previous selector is frozen verbatim under `baseline/`, the candidate is byte-for-byte the app source under `src/main/`, and a single 18-case public suite runs both to record the true before/after. Its changed wide-viewport expectation is intentionally new because the older preference hid roads on city-overview maps; the original 12-run is preserved at its earlier SHA. All renderer and full UK distribution gaps remain open.
