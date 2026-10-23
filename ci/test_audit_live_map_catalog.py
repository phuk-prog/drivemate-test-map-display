#!/usr/bin/env python3
"""Synthetic negative controls are geometry tests, NOT actual regional UK coverage."""
import importlib.util
from pathlib import Path
p=Path(__file__).resolve().with_name("audit_live_map_catalog.py")
spec=importlib.util.spec_from_file_location("audit_live",p)
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
S="a"*64
def region(id,box):
 return {"id":id,"name":id,"bounds":box,
   "manifest":{"tag":"map-data-region-"+id+"-r123","sha256":S,"bytes":500},
   "map":{"tag":"map-data-region-"+id+"-r123","sha256":S,"bytes":30000,
          "name":"drivemate-v1.pmtiles","layout":"drivemate-v1"}}
c={"schema":1,"coverage":"partial","regions":{"manchester":region("manchester",[-2.7,53.1,-2.0,53.8])}}
r=m.audit(c)
assert r["published_region_count"]==1
assert r["general_manchester_overview"]["fully_contained_by_one_region"]
assert r["reference_city_metadata"]["Manchester"]["inside_published_region_metadata"]
assert not r["reference_city_metadata"]["Norwich"]["inside_published_region_metadata"]
assert not r["batch_a_category02_accepted"] and not r["gpu_rendered_map_verified"]
c["regions"]["manchester"]["bounds"]=[-2.5,53.3,-2.2,53.6]
assert not m.audit(c)["general_manchester_overview"]["fully_contained_by_one_region"]
c["regions"]["manchester"]["bounds"]=[-2.5,53.3,-2.2,53.3]
try:m.audit(c);raise AssertionError("Invalid bound accepted")
except ValueError:pass
c["regions"]["manchester"]=region("manchester",[-2.7,53.1,-2.0,53.8])
c["regions"]["manchester"]["map"]["sha256"]="bad"
try:m.audit(c);raise AssertionError("Invalid SHA accepted")
except ValueError:pass
c["regions"]["manchester"]["map"]["sha256"]=S
c["coverage"]="complete"
r=m.audit(c)
assert r["catalog_complete"],"The publisher field, not sample sites, supplies completeness claim"
assert not r["whole_uk_real_road_verification"]
print("PASS: 8 published-catalog synthetic positive/negative conditions; no UK map completion claim")
