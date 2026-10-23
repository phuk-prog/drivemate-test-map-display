# Batch A — verified current-main baseline

Private CI [40010900185](https://github.com/phuk-prog/drivemate-app/actions/runs/40010900185), exact head `da28a6d90347dddfb45ca8f7faca454b8cdab2c7`, uses public tests `468e8255514717b3b292f95f9adf87e0caab5125`. App subtree `77e85ec5d30c99a3f88d922a046311081f688f4d` matches production main `e669dc67df86f0310d47bf0f648ad341db55055f`. This branch adds test infrastructure only; it excludes unmerged candidate fixes in PRs #4 and #5.

| Category | Before: passed/total | After |
|---|---:|---|
| 01 GPS | 10/19 | Not run |
| 02 Map | 22/22 | Not run |
| 04 Lanes/immersive | 9/9 | Not run |
| 05 Routing | 5/5 | Not run |

All 55 cases executed; no missing or skipped cases. Lint passed. The green baseline job records nine GPS failures; it is not acceptance. Local map score was 18/22 because external geometry was unavailable. CI obtained that data; 22/22 is not an app-code improvement.

The report retains all 22 checklist requirements. Five items lack runtime cases: 01.03, 04.02, 04.03, 05.01, 05.02. Passing helper tests does not establish actual UI/controller integration; main traffic route shading is not wired or verified. Detailed lane diagrams, rendered marker/route placement, real navigation replay and independently corroborated active sign/turn permissions remain unverified. OSM provides attributed road geometry, not legal-sign truth. The road audit also compares sourced polylines with themselves and a shifted negative control; it does not check the app's rendered route.

No category signed off; no production changes; no APK built or released. Next: fill missing integration coverage and test proposed fixes against this frozen baseline, reporting each category separately.
