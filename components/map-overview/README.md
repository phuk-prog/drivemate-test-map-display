# Batch A map overview visual-defect candidate

Evidence from the owner's uploaded map screenshot on 23 October 2026: most of the viewport is blank while a smaller Manchester area contains roads; navigation arrow appears oversized; hazard/camera markers overlap and obscure map labels. **The personal screenshot is not published here.** These observations are user-provided visual evidence, not a proven root-cause analysis or survey of legally correct road geometry.

## Frozen baseline vs proposed improvement

| Issue | Original app main snapshot | Candidate |
|---|---|---|
| Direction arrow | 44 dp drawable, scales 1.1×–1.7× | 32 dp drawable, 0.8×–1.25× for arrow |
| Hazard marker overlap | `iconAllowOverlap(true)`, `iconIgnorePlacement(true)` | Both false; symbols respect collision rules |
| Zoomed-out marker density | Warning icons displayed at all zoom levels | Warning icon symbol layer begins at zoom 11 |
| Map extent | No viewport warning for partial region selection | Warning when current regional-source bounds do not cover visible viewport |

**Important:** This does not manufacture missing UK map coverage; missing tile data cannot be fixed with marker styling. The exact cause of blank tiles requires verifying installed PMTiles bounds, current published-region coverage, map source status and a GPU-rendered emulator screenshot. Hiding some browsing pins is not an assertion that safety notifications may be suppressed; route-alert and spoken warning logic is unchanged. Exact sizes/zoom levels are proposed UI implementation choices, not a prior explicit driver-selected threshold.

**Public test scope:** This isolated module compiles and executes the exact new `MapOverviewPolicy.kt` from private integration candidate `phuk-prog/drivemate-app@00b540ffae77d1248049c0373c2bc87c1860ba48`; Git blob for helper `f1f1561e3c7f20db294fa9280c67c04a0e1d8768`. The app's MapController, MapData, UI and vector drawable are not publicly compiled here. Pure Kotlin tests verify display-policy bounds, safer scale values, symbol collision semantics, boundary/invalid cases. The integration branch changes the real app, but rendering/build acceptance is **not established by these pure tests**. No private app source is published beyond this standalone reusable policy component.

**Next gates:** exact Android-app compile and lint; MapLibre screenshot/layout on a working emulator; baseline-vs-after visual geometry and actual partial-map archive coverage diagnosis; mandatory replay and navigation robot. No APK release or full Category 02 acceptance.
