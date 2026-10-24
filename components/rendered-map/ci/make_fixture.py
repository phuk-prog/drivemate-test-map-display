#!/usr/bin/env python3
"""Pin two real, separately mapped carriageways of one UK dual carriageway from OpenStreetMap.

Princess Road (A5103), Manchester: OSM maps each direction as its own one-way way. The route
under test is drawn on the north-east-bound way; the south-west-bound way is the independent
"opposite carriageway" reference. OSM is observed geography (ODbL), not legal or lane truth.

Usage: make_fixture.py OUT.json        fetch and pin current versions
       make_fixture.py --check IN.json verify a pinned fixture's shape and provenance offline
"""
import json
import math
import sys
import urllib.request
import xml.etree.ElementTree as ET
from datetime import date

API = "https://api.openstreetmap.org/api/0.6/way/{}/full"
WAYS = {"own": 526771299, "opposite": 145483363}
UA = "DriveMate-public-road-evidence/1.0 (public ODbL render QA; no user journeys)"


def fetch(way_id):
    req = urllib.request.Request(API.format(way_id), headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        root = ET.fromstring(r.read(2_000_000))
    nodes = {n.get("id"): (float(n.get("lon")), float(n.get("lat"))) for n in root.findall("node")}
    way = root.find("way")
    tags = {t.get("k"): t.get("v") for t in way.findall("tag")}
    coords = [list(nodes[nd.get("ref")]) for nd in way.findall("nd")]
    return {"osm_type": "way", "osm_id": way_id, "osm_version": int(way.get("version")),
            "osm_timestamp": way.get("timestamp"), "tags": {k: tags.get(k) for k in
            ("name", "ref", "highway", "oneway", "lanes", "maxspeed")}, "coordinates": coords}


def bearing(a, b):
    lat1, lat2 = math.radians(a[1]), math.radians(b[1])
    dl = math.radians(b[0] - a[0])
    y = math.sin(dl) * math.cos(lat2)
    x = math.cos(lat1) * math.sin(lat2) - math.sin(lat1) * math.cos(lat2) * math.cos(dl)
    return (math.degrees(math.atan2(y, x)) + 360) % 360


def check(doc):
    assert doc["kind"] == "EXTERNALLY_SOURCED_OSM_UK_DUAL_CARRIAGEWAY", "wrong fixture kind"
    assert "OpenStreetMap" in doc["attribution"] and "ODbL" in doc["licence"]
    own, opp = doc["carriageways"]["own"], doc["carriageways"]["opposite"]
    assert own["osm_id"] == WAYS["own"] and opp["osm_id"] == WAYS["opposite"]
    for c in (own, opp):
        assert c["tags"]["ref"] == "A5103" and c["tags"]["oneway"] == "yes", "not a one-way A5103 carriageway"
        assert len(c["coordinates"]) >= 10
        assert all(-2.30 < x < -2.20 and 53.40 < y < 53.44 for x, y in c["coordinates"])
    # Travel directions must be roughly opposite, or this is not a pair of carriageways.
    d = abs(bearing(own["coordinates"][0], own["coordinates"][-1]) -
            bearing(opp["coordinates"][0], opp["coordinates"][-1])) % 360
    assert 150 <= d <= 210, "carriageways do not run in opposite directions: %.1f" % d


def main():
    if sys.argv[1:2] == ["--check"]:
        check(json.load(open(sys.argv[2], encoding="utf-8")))
        print("fixture ok")
        return
    doc = {"kind": "EXTERNALLY_SOURCED_OSM_UK_DUAL_CARRIAGEWAY",
           "place": "Princess Road (A5103), Manchester, Greater Manchester",
           "fetched": date.today().isoformat(), "source": API.format("{id}"),
           "attribution": "© OpenStreetMap contributors, https://www.openstreetmap.org/copyright",
           "licence": "ODbL 1.0",
           "evidence_limit": "Observed centreline geography only. Not legal turn, lane or sign truth.",
           "carriageways": {k: fetch(v) for k, v in WAYS.items()}}
    check(doc)
    with open(sys.argv[1], "w", encoding="utf-8") as f:
        json.dump(doc, f, indent=1)
        f.write("\n")
    print("pinned", {k: v["osm_version"] for k, v in doc["carriageways"].items()})


if __name__ == "__main__":
    main()
