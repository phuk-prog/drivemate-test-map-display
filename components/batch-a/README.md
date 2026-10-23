# Batch A public component execution

This repository now owns public execution of selected real app code, without checking out the private application or generating an APK. `source-manifest.json` identifies exact files/fragments and hashes; exact extracted declarations use only the imports required for JVM execution. Baseline and GPS candidate are separate; common components are unchanged. This does not publish the combined app.

The frozen 55 tests retain their identities for comparison with the historical private baseline. Thirteen additional tests separately cover uncertainty boundaries, seeded recovery sequences, camera decisions while waiting for GPS, invalid gyro axes, lane scene data, complex-junction focus and moving scene geometry. The expanded 68-case score is reported alongside the original score, never substituted for it.

Run: `python3 components/batch-a/ci/run.py --fetch-roads` with Gradle 8.14.3 and Java 17. CI fetches one bounded independently attributed Stockport OSM snapshot and uses the same fixture for both phases. A missing source, missing/skipped case, compilation error or candidate diagnostic failure prevents success. Baseline assertion failures are expected and explicitly recorded. No fake geometry replaces unavailable real data.

`evidence/test-expectation-audit.json` classifies every expectation. Camera zoom caps, confidence thresholds and scene distances are implementation regression settings, not laws or newly approved user preferences. Highway Code rules 184–186 require interpreting actual signs/markings; generic exit position does not establish lane permissions. Source references distinguish guidance, legislation and technical specifications.

The lane diagram tests execute the real scene model and its changing geometry. They do not prove the Android UI shows it correctly or only at complex junctions. Camera decision tests do not simulate gestures in a real map. The single-colour palette diagnostic does not establish amber/red live traffic shading. Geometry audits do not establish legal permissions or map pixel alignment. Tests for avoided-road alternatives and dragging the route panel are still missing.

The candidate contains already proposed GPS fixes. Passing public component checks does not mean those fixes are merged into the private app, nor does it approve Batch A, legal compliance or road readiness. Sensor adapter/lifecycle, actual UI wiring/rendering, routing response integration and road-specific legal evidence remain open. Final app integration and APK generation stay in the private repository.

Generated glyph source preserves its third-party licence in `licenses/replay-lettering.txt`.

The separate public Android library runs the same six Robolectric sensor-registration/stop/restart tests against the entire original adapter file for each phase. This is Android simulation, not a physical device or a full navigation application. Neither public job assembles or publishes a combined-app APK.
