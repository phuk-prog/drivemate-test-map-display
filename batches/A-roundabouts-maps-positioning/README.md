# Batch A initial shared baseline

The live app register selects categories 01, 02, 04 and 05 (22 exact requirements). This directory reuses public GPS and map contracts in one private Gradle run, adds lane/rerouting policy diagnostics, and preserves separate category and checklist-item outcomes.

The baseline is the current app main, excluding unmerged GPS PR #4 and map PR #5. Passing candidate results from those PRs are not relabelled as production behaviour. No production source changes, APK assembly or release are part of this baseline.

All inputs are synthetic except attributed external OSM geometry fetched by the existing public source loader. The inherited external-road tests check a geometry-audit calculation and negative controls; they do not prove that the navigation engine chose the legally correct road or that MapLibre drew it correctly. Highway Code and official DfT references remain authoritative general rules; actual local signs and restrictions remain unknown until independently corroborated.

`contracts/checklist.json` preserves all 22 requirements. Empty runtime-case lists mean untested, not passed. Diagram rendering, drag gestures, legal alternative selection and navigation robot/replay acceptance remain distinct gaps. Traffic-palette diagnostics do not prove traffic shading.

Run inside an authorized private checkout:

```
python3 batches/A-roundabouts-maps-positioning/ci/run.py --app /private/app --app-sha BASE_SHA --public-sha PUBLIC_SHA --fetch-roads
```

The command exits successfully when the baseline execution is complete and well formed, even when diagnostic assertions fail. `batch_accepted` stays false. Inspect category failures and all per-item findings separately. Missing/duplicate/unexpected/skipped cases, compilation failures and missing/failed lint invalidate execution. Raw logs stay local; only sanitized outcomes and attributed shareable road geometry are public.
