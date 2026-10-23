#!/usr/bin/env python3
"""Read the LIVE PUBLIC catalog of verified regional uploads.

Bounding boxes show candidate archive coverage only, never that roads are
rendered, legally usable or complete within them. No user GPS is transmitted.
"""
import json, math, pathlib, urllib.request, sys, datetime
CATALOG_URL="https://github.com/phuk-prog/DriveMate-Map/releases/download/map-regions/catalog.json"
OUT=pathlib.Path(__file__).resolve().parents[1]/"results/public-catalog-audit.json"
# A publicly chosen representative city-centre/zoomed-out trial, not a user's location.
TRIAL={"name":"Example Manchester city overview","centre_lat":53.4774,"centre_lon":-2.2309,
       "viewport":[-2.45,53.2,-2.0,53.6]}
def inside(bounds, lat, lon):
    return bounds[0]<=lon<=bounds[2] and bounds[1]<=lat<=bounds[3]
def contains(bounds, view):
    return bounds[0]<=view[0] and bounds[1]<=view[1] and bounds[2]>=view[2] and bounds[3]>=view[3]
def main():
    report={"checked_utc":datetime.datetime.now(datetime.timezone.utc).isoformat(),
      "catalog_url":CATALOG_URL,"scope":"published region bounding-box metadata only",
      "trial":TRIAL,"app_map_render_verified":False,"physical_uk_road_legality_proven":False,
      "all_uk_coverage_verified":False,"batch_a_accepted":False}
    try:
        req=urllib.request.Request(CATALOG_URL,headers={"User-Agent":"DriveMate-Public-Catalog-Check/1"})
        with urllib.request.urlopen(req,timeout=30) as response:
            data=response.read(2_000_001)
        if len(data)>2_000_000:raise ValueError("Catalog exceeds safe size")
        catalog=json.loads(data)
        if catalog.get("schema")!=1:raise ValueError("Unsupported region catalog schema")
        regs=catalog["regions"]
        if not isinstance(regs,dict) or len(regs)>512:raise ValueError("Invalid region set")
        parsed=[]
        for ident,region in regs.items():
            bounds=region["bounds"]
            if len(bounds)!=4 or any(not isinstance(v,(int,float)) or not math.isfinite(v) for v in bounds) or bounds[0]>=bounds[2] or bounds[1]>=bounds[3]:
                raise ValueError("Invalid bounds for "+ident)
            parsed.append((ident,bounds))
        lat,lon=TRIAL["centre_lat"],TRIAL["centre_lon"]
        centre=[(k,b) for k,b in parsed if inside(b,lat,lon)]
        centre.sort(key=lambda pair:(pair[1][2]-pair[1][0])*(pair[1][3]-pair[1][1]))
        covering=[(k,b) for k,b in centre if contains(b,TRIAL["viewport"])]
        report.update({"status":"verified_catalog_metadata","declared_coverage":catalog["coverage"],
          "published_region_count":len(parsed),
          "published_region_ids":[k for k,_ in parsed],
          "smallest_region_at_sample_centre":centre[0][0] if centre else None,
          "already_published_regions_covering_entire_example_viewport":[k for k,_ in covering],
          "example_viewport_fully_covered_by_single_region":bool(covering),
          "metadata_missing_viewport_coverage":not bool(covering),
          "note":"A published bounds rectangle is not proof of all underlying tiles, road signs or coverage away from this sampled viewport."})
    except Exception as ex:
        report.update({"status":"catalog_not_verified","error_type":type(ex).__name__,"error":"Live catalog metadata unavailable or malformed."})
    OUT.parent.mkdir(exist_ok=True)
    OUT.write_text(json.dumps(report,indent=2)+"\n")
    print(json.dumps(report))
    return 0 if report["status"]=="verified_catalog_metadata" else 1
if __name__=="__main__":sys.exit(main())
