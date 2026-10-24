# Rendered map — route on own carriageway (02.01), traffic colours (02.03), car arrow (02.04)

Real MapLibre GPU renders on an Android emulator, judged pixel by pixel against independently
sourced UK road geometry.

**First run:** [run 40071137577](https://github.com/phuk-prog/drivemate-test-map-display/actions/runs/40071137577)
at branch head `0443fcbdcfba60ed3b967e934482179b6e2e1159` (Pixel 6 profile, 1080×2274 px, API 30):
**02.01 3/3, 02.03 2/2**. 02.01 samples: day z17 own 61/61 inked, opposite visible 60/60, route
24.4 dp; night tilted z18 own 55/55, opposite 56/56, route 27.8 dp. 02.03 samples: amber 9/9,
free 7/7, red 17/17. Both rendered negative controls were rejected (wrong carriageway: own 0/61,
opposite 0/60; swapped colours: amber 0/9, red 0/17). Saved summary in `results/rendered-map.json`;
its `public_head_sha` (`696b65d…`) is GitHub's temporary PR merge commit of that head into main
`05284e5`. Later runs record the branch head. No earlier run of this suite exists, so there is no
before/after score.

## Car arrow (02.04) — before/after

Owner's definition (2026-10-24) of "proportional to the map": at driving zooms 16–18 the arrow
covers **between one lane (3.65 m, the DMRB CD 127 standard lane width) and its whole carriageway**
(OSM `lanes` × 3.65 m; Princess Road north-east-bound has 3 lanes, so 10.95 m). It must also sit
within 1 m of its carriageway centreline and point within 10° of the road's direction of travel.

The arrow is DriveMate's `ic_car_arrow` drawn by MapLibre's location component through the app's
`CarMarker.kt`, in GPS render mode as when navigating. The judge finds it by comparing renders
with and without it, then takes the arrow's mirror axis as its direction and the tip as its front.
The same seven tests run twice: `-Pphase=baseline` (app `d8d6012`, `CarMarker` moved out of
`MapController` unchanged) and `-Pphase=candidate` (app `7c045a8`, the 02.04 change).

| Test | Checks |
|---|---|
| `pref02_04_northUpZoom16/17/18…` | north-up camera, so the arrow must turn to the road's ~26° bearing; size at each driving zoom |
| `pref02_04_drivingViewZoom17ArrowPointsUpTheRoad` | camera along the road: the arrow points up the screen |
| `pref02_04_zoom16SizeSurvivesAStyleReload` | a new map style (night mode, new map), then the app's re-show |
| `pref02_04_negativeControl…AgainstTraffic` / `…OffTheCentreline` | judge rejects a reversed arrow and one 5 m towards the central reservation |

Expected from the source: the baseline arrow is about 15.4 m wide at zoom 16 (wider than the
carriageway), 7.8 m at zoom 17 and 4.0 m at zoom 18. The candidate is 8.1 m at all three zooms
here (exact 8 m at 54°N). Outside zooms 16–18 it keeps its end size: about 11 dp below zoom 16 and
46 dp above zoom 18.


| Checklist | Test | Must |
|---|---|---|
| 02.01 | `pref02_01_dayTopDownZoom17RouteStaysOnOwnCarriageway` | pass |
| 02.01 | `pref02_01_nightDrivingTiltZoom18RouteStaysOnOwnCarriageway` | pass |
| 02.01 | `pref02_01_negativeControlRouteOnOppositeCarriagewayIsRejected` | judge rejects a route drawn on the wrong carriageway |
| 02.03 | `pref02_03_slowIsAmberHeavyIsRedOnlyOnTheirSections` | pass, and traffic stays off the opposite carriageway |
| 02.03 | `pref02_03_negativeControlSwappedTrafficColoursAreRejected` | judge rejects red-for-slow / amber-for-heavy |
| 02.04 | `CarArrowTest`, seven cases, both phases | see above |

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
- **Judge self-tests:** `judge-tests/RenderJudgeTest.kt` and `ArrowJudgeTest.kt`, 24 plain-JVM cases. The route judge has 13, including 11 broken
  pictures (wrong carriageway, between carriageways, too wide, hairline, too close to test,
  off-screen, swapped/missing/spilling traffic, orange/yellow/pink look-alikes). The arrow judge has 11,
  including reversed, turned, off-centre, too wide, too narrow, missing and lopsided arrows.

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
  arrow positioning by GPS/route snapping, or UI occlusion. It cannot satisfy the APK gate's `exact-combined-app` scope.
- Traffic bands are placed by the test. Matching real reports to the route
  (`TrafficRouteOverlay.sections`) is covered by the existing simulated-traffic logic tests, not here.
- One dual carriageway at two zooms. Other road layouts (slip roads, roundabouts, single
  carriageways) are not covered.
- Emulator SwiftShader rendering, not a physical phone GPU.
- No earlier rendered run of this suite exists, so there is no before/after score.
