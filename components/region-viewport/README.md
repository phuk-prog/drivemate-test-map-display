# Batch A — viewport-aware selection of *published* map regions

The app previously selected the smallest map region containing the viewport centre, even when a larger **already-published** regional archive would cover the whole screen. That can produce a map surrounded by blank space at city-overview scale. It is **one potential contributor** to the map appearance reported by the owner; diagnosing the specific installed file and its tiles is still needed.

This public module compiles the **exact** production file `PublishedRegionCatalog.kt` from app candidate SHA `6129a03caf2b77d72fb7ff77718bd8b220bfd4d1`, Git blob `2bbf4b3ee5b7a5f11a235775d717d4cdde2f0c0f`.

Twelve JUnit boundary/malformed input tests exercise centre-only baseline selection, viewport coverage selection, partial-published fallback, and missing/invalid location safety. Synthetic rectangles are **not** claims about UK road boundaries. These are isolated component tests, not the connected MapLibre renderer, the actual release catalog, validated UK map coverage or app integration.

No actual user screenshot, precise location, private app code, APK or API credentials are published. Current partial map publisher https://github.com/phuk-prog/DriveMate-Map must separately complete needed regions. App draft PR #10 integrates the new selection into the actual MapController, MapData and region-catalog wiring. Do **not** mark map Category 02 passed without Android rendered-map comparison and replay robot.
