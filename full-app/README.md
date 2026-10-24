# Full-app tests — the real combined DriveMate app

Owner decision (2026-10-24): gates that need the whole app run on an emulator **inside the private
app repository**, using test definitions published here at a pinned commit. Only the run identity,
cleaned-up per-test results and their limits are published back; app source, APKs and personal
data stay private. This is the one exception to the public-only test rule, for tests whose scope
is `exact-combined-app`.

## How a run works
1. The private workflow `.github/workflows/full-app-gates.yml` reads the pinned commit of this
   repository from `.github/full-app-tests.lock`, checks it out, and copies `full-app/androidTest`
   into the app's `androidTest` sources and `components/rendered-map/fixtures` into its test assets.
2. It runs `connectedDebugAndroidTest` for these classes on an API 30 x86_64 **Google APIs**
   emulator. (The AOSP image crashed MapLibre's Vulkan renderer in SwiftShader, run 40086048743.)
3. `ci/summarize_full_app.py` writes one result per checklist ID with the app commit, the exact
   `app/src/main` tree SHA and this repository's test commit. A missing, skipped or failed test fails
   the run.

## 01.01 — "Position uncertain" after three seconds (`PositionUncertainBannerTest`)
Every fix goes to both of the app's location sources, Android's GPS test provider and Google
Play's fused location in mock mode, along Princess Road (A5103), Manchester, at 8 m/s.
The banner is read from the screen through accessibility (UiAutomator), as a driver sees it.

| Test | Must |
|---|---|
| `bannerAppearsThreeSecondsIntoContinuousPoorAccuracy` | 35 m fixes: banner no sooner than 3 s (100 ms tolerance), within a 1 s display budget after that |
| `bannerAppearsAfterGpsIsLost` | no fixes: banner no sooner than 3 s after the last fix, and by 7 s |
| `bannerClearsWhenPositionRecovers` | banner gone within 1.5 s of accurate fixes resuming |
| `shortGlitchUnderThreeSecondsNeverShowsBanner` | 2 s of 35 m fixes, then accurate: never shown |
| `parkedWithHealthyGpsNeverShowsBanner` | parked 80 s with accurate fixes every second: never shown |

The timing rules come from the checklist wording plus stated engineering budgets, not the app's
constants. The parked case targets a source finding: after 60 s stopped the app asks for a fix
only every 15 s, while it treats fixes older than 2.5 s as unreliable.

## Limits
- Both sources get identical fixes, so disagreement between them is not exercised.
- Fixes are synthetic; real GNSS noise, tunnels and multipath are not modelled.
- Browse mode only here. The navigating-mode notice and lane hiding (01.02) need a route and come next.
