# Rendered map — route on own carriageway (02.01) and traffic colours (02.03)

Real MapLibre GPU renders on an Android emulator, judged pixel by pixel against independently
sourced UK road geometry. **Status: drafted, not yet run in CI.**

## What is tested

| Checklist | Test | Must |
|---|---|---|
| 02.01 | `pref02_01_dayTopDownZoom17RouteStaysOnOwnCarriageway` | pass |
| 02.01 | `pref02_01_nightDrivingTiltZoom18RouteStaysOnOwnCarriageway` | pass |
| 02.01 | `pref02_01_negativeControlRouteOnOppositeCarriagewayIsRejected` | judge rejects a route drawn on the wrong carriageway |
| 02.03 | `pref02_03_slowIsAmberHeavyIsRedOnlyOnTheirSections` | pass, and traffic stays off the opposite carriageway |
| 02.03 | `pref02_03_negativeControlSwappedTrafficColoursAreRejected` | judge rejects red-for-slow / amber-for-heavy |

## Where each part comes from

- **Road truth:** Princess Road (A5103), Manchester. OSM maps each direction as its own one-way
  way: north-east-bound [526771299](https://www.openstreetmap.org/way/526771299) (own) and
  south-west-bound [145483363](https://www.openstreetmap.org/way/145483363) (opposite), 11–45 m
  apart. Pinned in `fixtures/princess-road-a5103-osm.json` by `ci/make_fixture.py`, with the OSM
  versions and timestamps. © OpenStreetMap contributors, ODbL 1.0. Geography only: not legal lane,
  turn or sign truth.
- **System under test:** DriveMate's `RouteLayers.kt` (route halo, casing, fill, driven part and
  traffic band) with `RoutePalette.kt` and `Geo.kt`, published byte for byte from the private app
  commit in `source-manifest.json`. `ci/validate.py` fails if any of them is edited or another app
  file appears.
- **Judge:** `judge/RenderJudge.kt` knows nothing about the app. It sees pixels, the screen
  positions of the OSM centrelines and the checklist's words:
  - *Own carriageway:* ≥95% of own-centreline samples in view show route ink, and the route is at
    least 6 dp wide (a hairline cannot pass).
  - *Opposite carriageway visible "where possible":* wherever the opposite centreline is at least
    5 dp from the route centreline on screen (half a 6 dp legible line plus 2 dp), ≥95% of those
    samples still show the grey reference road. At least 80% of opposite samples in view must be
    testable, so a pass cannot be vacuous.
  - *Amber / red:* hue 30–50° / within 15° of red, strongly saturated. An engineering definition
    for this test, not a legal colour standard. ≥95% of samples inside each band (25 m clear of
    its ends) must match, and free route outside the bands must be neither.
- **Judge self-tests:** `judge-tests/RenderJudgeTest.kt`, 13 plain-JVM cases including 11 broken
  pictures (wrong carriageway, between carriageways, too wide, hairline, too close to test,
  off-screen, swapped/missing/spilling traffic, orange/yellow/pink look-alikes).

## Running

```
python3 components/rendered-map/ci/validate.py
(cd components/rendered-map/judge-jvm && gradle test)                        # no Android needed
(cd components/rendered-map/android && gradle connectedDebugAndroidTest)     # emulator or device
```

CI: `.github/workflows/rendered-map-public.yml` runs the judge job, then the emulator job (API 30,
x86_64, Pixel 6 profile, SwiftShader GPU), and uploads screenshots, per-test verdict JSON and
`results/rendered-map.json` with one score per checklist ID. A missing, skipped or extra test fails
the run.

## Evidence limits

- Isolated component, **not** the combined app: no DriveMate UK map style, camera controller,
  car arrow (02.04) or UI occlusion. It cannot satisfy the APK gate's `exact-combined-app` scope.
- Traffic bands are placed by the test. Matching real reports to the route
  (`TrafficRouteOverlay.sections`) is covered by the existing simulated-traffic logic tests, not here.
- One dual carriageway at two zooms. Other road layouts (slip roads, roundabouts, single
  carriageways) are not covered.
- Emulator SwiftShader rendering, not a physical phone GPU.
- No earlier rendered run of this suite exists, so there is no before/after score.
