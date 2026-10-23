# DriveMate Category 2 — public test contracts

Tests for checklist items **02.01–02.09**, including simulated TomTom incident parsing and route-line colouring.

The Android application remains **private** in `phuk-prog/drivemate-app`. This public repository holds **only reusable tests, synthetic fixtures, contracts and bounded results**. Tests compile and execute against the real application classes **inside the private app's GitHub Actions run**. No production source or private app build is copied here.

## TomTom testing — simulation only

- `fixtures/tomtom-v5-simulated-traffic.json` is **invented**: no TomTom account, API response, credential, phone location or real journey is used.
- `tests/Category2TomTomLiveTrafficTest.kt` and `tests/Category2SimulatedTrafficFixtureTest.kt` call the real production **JSON parser and overlay policy**, passing simulated input directly without networking.
- Red heavy congestion, amber moderate congestion, wrong-direction traffic, adjacent roads, expired/future events, incomplete geometry and malformed records are explicitly tested.
- The private workflow fetches the **pinned public fixture/test commit** and runs Kotlin JUnit and lint against the private app without passing secrets. It uploads only the short result classifier JSON, not keys, route histories, app source or raw API responses.
- The TomTom API key is **never required or permitted in this public repository**. A user can configure their own key in the installed app if they want real live traffic. This testing does not validate live TomTom authentication or availability.

## Files

- `contracts/category2.json`: all nine preferences and exact expected test method names.
- `tests/Category2MapDisplayContractTest.kt`: camera and geometry logic.
- `tests/Category2TomTomLiveTrafficTest.kt`: synthetic incident parser and overlay tests.
- `tests/Category2SimulatedTrafficFixtureTest.kt`: declared synthetic example matrix.
- `fixtures/tomtom-v5-simulated-traffic.json`: published fictitious inputs.
- `ci/summarize.py`: validates executed test identities, classifies traffic source wiring and labels evidence limits.

**Evidence limits:** passing simulated tests establishes only the behaviours exercised in production classes. It **does not establish current live traffic conditions**, live API connectivity, map-rendered traffic colour, correctly chosen UK carriageways, legal road truth or physical-device camera smoothness. Those require separate appropriate evidence and remain unverified until performed. No Android APK release or merge follows merely from these tests.

## Independent UK-road evidence and Android renderer

Category 2 is no longer accepted from self-referential app expectations. The governing record is [the UK road-evidence contract](contracts/uk-independent-evidence.json), referencing the official Highway Code (Rules 160, 180 and 184–190, and Rule 264) and Department for Transport road-sign/marking guidance.

- [Public OSM road fetcher](ci/fetch_independent_uk_roads.py) downloads real named roundabout geometry for Roscoe's Roundabout from a bounded official OSM API extract (Overpass fallback) when private CI runs. It records OSM way identity and ODbL attribution; it **fails closed** when no source is available.
- [Real-source road tests](tests/Category2IndependentUkRoadGeometryTest.kt) assert the independent source and check production geometry drift, negative control, and evidence limits.
- [Android map-rendering robot](androidTest/Category2RenderedMapRobotTest.kt) runs on a GitHub Android emulator, creates the independently sourced road line, adds actual private production MapController route/traffic layers and queries the native renderer and GPU snapshot.
- Private CI is **required to pass all 40 Kotlin tests, two emulator map-renderer tests, and Android lint**. No TomTom credential is required or passed.
- **Legal and geographical facts are separate:** OSM is independent *geography*, not independently certified current signs or allowed turns. For road-specific legal manoeuvre claims (especially lane arrows, roundabout exits and restricted turns), official current signs/traffic orders and corroboration must be checked separately before the test can certify that manoeuvre.
- **Evidence limitation:** a native render robot with an offline test style proves production overlay drawing, but not the real app's full UK map style, phone screen occlusion in navigation, or legality at every exit. Do not mark untested driving requirements verified.

Source attribution: © OpenStreetMap contributors, https://www.openstreetmap.org/copyright, ODbL 1.0.
