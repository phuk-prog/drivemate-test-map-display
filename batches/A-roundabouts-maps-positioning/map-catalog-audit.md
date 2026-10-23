# Batch A — published UK regional map coverage audit

Reads the `map-regions` release's `catalog.json` from [the public DriveMate map publisher](https://github.com/phuk-prog/DriveMate-Map). Tests that every published region has a bounded, schema-compatible rectangle, an asset identity and valid SHA/byte metadata. Compares broad **public-reference** UK city points and a **generic** Greater Manchester overview against the catalogue's declared bounds. There are no user coordinates, screenshot images, private secrets, API keys or combined app code here.

A region list with `coverage: partial` cannot be called whole-UK. A region containing a generic map viewport does not prove its **PMTiles actually contain those tiles** or that Android MapLibre rendered them. A map-data gap is independent of GPS and drawn lane/speed legal truth. Publisher coverage and renderer verification are separate acceptance gates.

Run `python3 ci/test_audit_live_map_catalog.py` for independent positive/negative synthetic controls, then `python3 ci/audit_live_map_catalog.py --out results/live-published-map-regions.json` for actual released metadata. Public workflow produces an artifact with exact evidence. No APK built or released.
