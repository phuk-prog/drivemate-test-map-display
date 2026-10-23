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
