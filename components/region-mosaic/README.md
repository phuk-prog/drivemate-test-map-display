# Region mosaic and optional region download

**Owner report (2026-10-24):** at an overview zoom over Manchester the map showed one square,
with blank map all around it. The app drew only one published region at a time, and picked the
small "manchester" region.

**Change (private app, exact files below):**
- The map streams every published region the view touches, from GitHub, not just one. Up to six
  extra regions are added, largest share of the view first; a region inside one already drawn is skipped.
- The existing optional download (Offline downloads → Download map) now fetches every published
  region on Wi-Fi, each checked against its published SHA-256, and only changed regions again.
  A downloaded region is drawn from the phone; an older copy stays in use until its replacement
  is complete.

## What runs
`ci/run.py` checks that each app file here is byte-identical to the private app commit in
`source-manifest.json`, then runs:

| Phase | Code | Tests |
|---|---|---|
| before | `baseline/PublishedRegionCatalog.kt` (app `main` 10a2e97), draws the selected region only | 6 overview views |
| after | `src/main` (catalogue, `RegionMosaic`, `RegionPackFiles`), draws the region plus its neighbours | 6 overview views + 14 component tests |

Overview views (each sampled at 21 × 21 points): the owner's Manchester screenshot, M62
Manchester–Liverpool, Leeds–Bradford, central London, Bristol–Bath, and the England–Scotland
border at Carlisle. A view passes only if **every** sample point that some published region covers
is drawn. The catalogue is the real published one, fetched 2026-10-24 (46 regions).

Component tests: neighbours for the screenshot view (Greater Manchester plus Cheshire and
Lancashire, not the "manchester" region inside it), the six-region cap and ordering, the
"zoom in / not published" notice, the style copy order (neighbour layers under the main ones,
casings kept), and the download rules (which file to draw, a current file beats an older one,
"manchester" never takes the "greater-manchester" file, old files deleted only after their
replacement is complete, every published tag recognised).

## Limits
- Coverage is judged on region bounds, not on tiles actually drawn; the private full-app run is
  where the real map is drawn.
- `ci/candidate/Drawn.kt` restates the one line in `MapData` that asks for neighbours, because
  `MapData` needs Android.
- Regions published after 2026-10-24 are not in the fixture.

## Result
Recorded in `results/region-mosaic.json` after the first run.
