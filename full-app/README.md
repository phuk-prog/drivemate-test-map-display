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

**Result: before 4/5 → after 5/5** (`results/full-app-01.01.json`).

| Test | Before: unfixed app, [run 40087951766](https://github.com/phuk-prog/drivemate-app/actions/runs/40087951766) | After: [run 40090338519](https://github.com/phuk-prog/drivemate-app/actions/runs/40090338519), confirmed by [run 40091412313](https://github.com/phuk-prog/drivemate-app/actions/runs/40091412313) |
|---|---|---|
| Poor accuracy | pass, 3.5 s | pass, 3.4 s / 3.4 s |
| GPS lost | pass, 3.1 s | pass, 3.3 s / 3.0 s |
| Clears on recovery | pass, under 0.2 s | pass, 0.24 s / 0.02 s |
| 2 s glitch | pass, never shown | pass, never shown |
| Parked 80 s, accurate GPS | **fail: shown at 65.8 s, flickering** | **pass: never shown** |

The fix: while parked, fixes asked for every 15 s stay current for 20 s (was 2.5 s for the banner
and 8 s for source selection), and parked fixes are no longer batched. Navigation keeps 2.5 s.
The private runs are links for the owner; the numbers above are the public record.

Harness lessons, each now guarded: the banner is unreadable until the accessibility tree has been
walked once; the AOSP image crashes MapLibre's Vulkan renderer; the emulator launcher can freeze in
front of the app; the screen can sleep during long cases. A run where DriveMate is not on screen
fails as a harness fault, never as a pass.

**Timing fix (2026-10-24):** `bannerAppearsAfterGpsIsLost` timed the banner from the start of
the no-fix phase, which begins after the start-up screenshot, not from the last fix. Private
[run 40103590161](https://github.com/phuk-prog/drivemate-app/actions/runs/40103590161) then
read 2.638 s and failed, although the real gap since the last fix was longer by an unrecorded
amount. The test now records when the last fix reached both sources and measures from there,
for both the 3 s lower bound and the 7 s upper bound.

## Limits
- Both sources get identical fixes, so disagreement between them is not exercised.
- Fixes are synthetic; real GNSS noise, tunnels and multipath are not modelled.
- Browse mode only here. The navigating-mode notice and lane hiding (01.02) need a route and come next.
