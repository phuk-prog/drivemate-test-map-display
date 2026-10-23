# Batch A public test results — 2026-10-23

Verified [public CI run 40021527920](https://github.com/phuk-prog/drivemate-test-map-display/actions/runs/40021527920), exact commit `a4d5a3b13743692c003a7af95c88ff4bd589e40f`. All four jobs succeeded. No private app checkout, combined application, APK generation or release in these jobs.

| Check set | Before | Candidate after |
|---|---:|---:|
| Original GPS checks (unchanged set) | 10/19 | 19/19 |
| Expanded GPS comparison (same 27 cases) | 11/27 | 27/27 |
| Android sensor adapter (same six cases) | 4/6 | 6/6 |
| Additional GPS boundary/flow verification | Not run on original main | 32/32 |
| Map helper/external-geometry checks | 22/22 | 22/22 |
| Lane/immersive component checks | 14/14 | 14/14 |
| Off-route/rerouting helper checks | 5/5 | 5/5 |
| New draggable panel: native Compose interactions | No comparable executed baseline | 5/5 |

The original GPS 19 checks are contained in the expanded 27; do not double-count them. Candidate checks total 111 distinct cases: GPS 65 (27 + 6 + 32), map 22, lanes 14, routing helpers 5, route panel 5. Do not pool the incomplete before suite into a misleading before/after percentage. Assertions, missing/skipped cases, sources and revision hashes are retained in the four JSON reports.

The 32 verification cases include invalid/out-of-order/future positions, longitude wrap, provider selection, uncertainty/recovery sequences and policy interaction. Android adapter tests use Robolectric and mocked registration, not a physical drive. Route-panel tests exercise actual Compose drag and accessibility actions in Robolectric native graphics, plus bounds and invalid inputs.

[Draft private integration PR #8](https://github.com/phuk-prog/drivemate-app/pull/8), head `5a83d9a5fa6150a6f8a6f2c6d31b407f7d6b5688`, carries the same tested GPS files and draggable-panel source into the app preview. The panel source SHA-256 is `6ef3e82481317215424858484da1d00d3cc6d24267948dfdc06df8ef73f90a55`. This branch adds no private test workflow and is not merged or released. Older candidate PRs remain identifiable; no score is attributed to production main.

Public test execution replaces the previous private arrangement. The four private testing workflows were removed from PR branches #4–#7; app APK generation remains private. Canonical [testing policy](../../TESTING_POLICY.md) and [grouping contract](../../contracts/grouped-public-tests.json) reflect the user's instruction.

Every expectation is classified against approved user preferences, official Android specifications, relevant Great Britain Highway Code/legislation, or explicitly identified implementation settings. Road-specific signs, restrictions and legal lane permissions remain unverified; OSM geometry alone is not legal truth. Numeric camera/confidence defaults are regression settings, not UK law. Palette-helper success does not establish live traffic shading or actual map rendering. Detailed scene-model checks do not establish correct whole-app lane-diagram timing or placement.

**Batch A is not accepted or complete.** Outstanding: the faster avoided-road alternative and its fallback preference (question pending), final integration/render/replay and camera/traffic evidence, road-specific legal corroboration and full private APK assembly/release checks. The route panel is integrated in the draft branch; actual combined-app/map integration has not been executed. No requirement is signed off from component scores alone.
