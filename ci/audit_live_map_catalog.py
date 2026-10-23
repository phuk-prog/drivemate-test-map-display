#!/usr/bin/env python3
"""Audit only PUBLISHED regional map coverage. No GPS location or app credentials.

Live catalog metadata is a publisher claim, NOT proof tiles actually render or
that a turn, lane, sign or speed limit is legally correct.
"""
import argparse
import json
from pathlib import Path
import re
import sys
import time
from urllib.request import Request, urlopen

RELEASE="https://api.github.com/repos/phuk-prog/DriveMate-Map/releases/tags/map-regions"
UA="DriveMate-public-map-catalog-audit"
MAX_BYTES=2*1024*1024
SHA=re.compile(r"^[0-9a-f]{64}$")
TAG=re.compile(r"^map-data-region-[A-Za-z0-9._-]{1,110}$")
# Reference cities are public landmarks, never inferred from personal locations.
PUBLIC_REFERENCE_CITIES={
    "Manchester":(53.4808,-2.2426),
    "Cardiff":(51.4816,-3.1791),
    "Edinburgh":(55.9533,-3.1883),
    "Belfast":(54.5973,-5.9301),
    "Birmingham":(52.4862,-1.8904),
    "Norwich":(52.6309,1.2974),
    "Exeter":(50.7184,-3.5339)
}
MANCHESTER_OVERVIEW=(-2.60,53.20,-2.05,53.70) # Generic published regional audit bbox, not user's screenshot bounds.

def read_url(url):
    req=Request(url,headers={"User-Agent":UA,"Accept":"application/json"})
    with urlopen(req,timeout=30) as resp:
        data=resp.read(MAX_BYTES+1)
        if len(data)>MAX_BYTES: raise ValueError("Catalog exceeds bounded size")
        return json.loads(data)

def validate(catalog):
    if catalog.get("schema")!=1 or catalog.get("coverage") not in ("partial","complete"):
        raise ValueError("Unexpected publisher catalogue schema or coverage")
    entries=catalog.get("regions")
    if not isinstance(entries,dict) or len(entries)>512:
        raise ValueError("Unbounded or malformed publisher regions")
    results={}
    for rid,entry in entries.items():
        if not re.fullmatch(r"[a-z0-9][a-z0-9-]{0,79}",rid) or entry.get("id")!=rid:
            raise ValueError("Invalid region identity")
        bounds=entry.get("bounds")
        if not isinstance(bounds,list) or len(bounds)!=4 or any(type(x) not in (int,float) for x in bounds):
            raise ValueError("Invalid region bounds")
        w,s,e,n=bounds
        if not(-180<=w<e<=180 and -85.1<=s<n<=85.1):
            raise ValueError("Invalid geographic extent")
        for key in ("manifest","map"):
            asset=entry.get(key)
            if not isinstance(asset,dict) or not SHA.fullmatch(str(asset.get("sha256",""))) or not TAG.fullmatch(str(asset.get("tag",""))):
                raise ValueError("Invalid release manifest/hash")
            if type(asset.get("bytes")) is not int or not (0<asset["bytes"]<=1_950_000_000):
                raise ValueError("Invalid asset size")
        if entry["map"].get("name")!="drivemate-v1.pmtiles":
            raise ValueError("Map archive name does not match app")
        results[rid]={"name":entry.get("name",rid),"bounds":bounds,
            "map_bytes":entry["map"]["bytes"],"map_tag":entry["map"]["tag"]}
    return results

def contains(bounds,lat,lon):
    return bounds[0]<=lon<=bounds[2] and bounds[1]<=lat<=bounds[3]

def covers_bbox(bounds,box):
    return bounds[0]<=box[0] and bounds[1]<=box[1] and bounds[2]>=box[2] and bounds[3]>=box[3]

def audit(catalog,release_metadata=None):
    regions=validate(catalog)
    cities={}
    for name,(lat,lon) in PUBLIC_REFERENCE_CITIES.items():
        matches=[id for id,r in regions.items() if contains(r["bounds"],lat,lon)]
        cities[name]={"inside_published_region_metadata":bool(matches),"regions":matches,
            "tiles_actually_verified":False}
    bounds=MANCHESTER_OVERVIEW
    containing=[id for id,r in regions.items() if covers_bbox(r["bounds"],bounds)]
    overlap=[id for id,r in regions.items()
       if not(r["bounds"][2]<bounds[0] or r["bounds"][0]>bounds[2]
           or r["bounds"][3]<bounds[1] or r["bounds"][1]>bounds[3])]
    return {"schema":1,"scope":"Public publisher catalog METADATA only; not map rendering or legal road evidence",
        "publisher":"phuk-prog/DriveMate-Map","release_tag":"map-regions",
        "catalog_coverage_claim":catalog["coverage"],
        "catalog_complete":catalog["coverage"]=="complete",
        "published_region_count":len(regions),
        "published_region_ids":sorted(regions),
        "reference_city_metadata":cities,
        "general_manchester_overview":{"bbox":"generic Greater Manchester area; not a user's view/GPS",
            "fully_contained_by_one_region":bool(containing),
            "fully_containing_regions":containing,
            "overlapping_regions":overlap},
        "no_public_personal_route":True,"actual_tile_bytes_verified":False,
        "gpu_rendered_map_verified":False,"whole_uk_real_road_verification":False,
        "batch_a_category02_accepted":False,
        "evidence_limit":"Bounding rectangles and catalog flags do not confirm actual PMTiles tile presence, legally correct turns or rendered Android map.",
        "release_metadata":release_metadata or {}}

def download():
    release=read_url(RELEASE)
    assets=[a for a in release.get("assets",[]) if a.get("name")=="catalog.json"]
    if len(assets)!=1:raise ValueError("Expected exactly one catalog.json release asset")
    asset=assets[0]
    url=asset.get("browser_download_url","")
    if not url.startswith("https://github.com/phuk-prog/drivemate-map/releases/download/map-regions/"):
        raise ValueError("Unexpected publisher download address")
    catalog=read_url(url)
    return catalog,{"release_asset_updated_at":asset.get("updated_at"),
       "asset_size_bytes":asset.get("size"),"catalog_url":url}

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--catalog-file",type=Path)
    p.add_argument("--out",type=Path,required=True)
    a=p.parse_args()
    if a.catalog_file:catalog=json.loads(a.catalog_file.read_text());metadata={"fixture":True}
    else:catalog,metadata=download()
    report=audit(catalog,metadata)
    a.out.parent.mkdir(parents=True,exist_ok=True)
    a.out.write_text(json.dumps(report,indent=2,sort_keys=True)+"\n")
    print(json.dumps({"published_region_count":report["published_region_count"],
        "coverage":report["catalog_coverage_claim"],
        "manchester_overview_covered_by_one_region":report["general_manchester_overview"]["fully_contained_by_one_region"],
        "missing_reference_cities":[x for x,y in report["reference_city_metadata"].items() if not y["inside_published_region_metadata"]],
        "actual_tiles_tested":False,"batch_a_accepted":False}))
if __name__=="__main__":main()
