#!/usr/bin/env python3
"""Fetch independent, attributable real road geometry from OSM for private app tests.
Source: OpenStreetMap contributors (ODbL). No synthetic road may count as this check.
This is observational centreline evidence, NOT legal proof of a particular movement.
"""
import json
import sys
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from datetime import date
from pathlib import Path

QUERY = '[out:json][timeout:24];way["name"="Roscoe\'s Roundabout"]["junction"~"roundabout|circular"];out geom;'
ENDPOINTS = [
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass.nchc.org.tw/api/interpreter",
]

def get():
    errors = []
    # A bounded official OSM API extract is far smaller and more reliable than
    # searching the entire world by street name on shared Overpass servers.
    # Location externally corroborated (53.397805,-2.200441), streetlist.co.uk.
    bbox = "-2.2040,53.3960,-2.1970,53.4000"
    primary = "https://api.openstreetmap.org/api/0.6/map?bbox=" + bbox
    try:
        req = urllib.request.Request(primary, headers={
            "User-Agent": "DriveMate-public-road-evidence-ci/1.0 (public ODbL road QA)"})
        with urllib.request.urlopen(req, timeout=25) as response:
            root = ET.fromstring(response.read(4_000_000))
        nodes = {n.attrib["id"]: {"lat": float(n.attrib["lat"]), "lon": float(n.attrib["lon"])}
                 for n in root.findall("node")}
        elements = []
        for way in root.findall("way"):
            tags = {t.attrib["k"]: t.attrib["v"] for t in way.findall("tag")}
            if tags.get("junction") not in ("circular", "roundabout") or tags.get("name") != "Roscoe's Roundabout":
                continue
            geometry = [nodes[nd.attrib["ref"]] for nd in way.findall("nd") if nd.attrib["ref"] in nodes]
            if len(geometry) >= 3:
                elements.append({"type": "way", "id": int(way.attrib["id"]),
                    "version": int(way.attrib.get("version", "0")),
                    "timestamp": way.attrib.get("timestamp"),
                    "tags": tags, "geometry": geometry})
        if elements:
            return {"elements": elements}, primary
        errors.append("OSM main API: no named roundabout ways in verified small bounding box")
    except Exception as e:
        errors.append("OSM main API: " + type(e).__name__ + " " + str(e)[:90])
    for host in ENDPOINTS:
        try:
            data = urllib.parse.urlencode({"data": QUERY}).encode()
            req = urllib.request.Request(host, data=data, headers={"User-Agent": "DriveMate-public-road-evidence-ci/1.0 (OpenStreetMap ODbL; no user journeys)"})
            with urllib.request.urlopen(req, timeout=34) as response:
                value = json.loads(response.read(2_000_000))
            if value.get("elements"):
                return value, host
            errors.append(host + " returned no matching roads")
        except Exception as e:
            errors.append(host + ": " + type(e).__name__ + " " + str(e)[:90])
    raise RuntimeError("Independent real-road source unavailable; no fabricated replacement: " + "; ".join(errors))

def main():
    raw, host = get()
    roads = []
    for way in raw["elements"]:
        tags = way.get("tags", {})
        geometry = way.get("geometry", [])
        if way.get("type") != "way" or tags.get("junction") not in ("roundabout", "circular"):
            continue
        if tags.get("name") != "Roscoe's Roundabout" or len(geometry) < 3:
            continue
        coords = [[p["lon"], p["lat"]] for p in geometry]
        if not all(-2.5 < x < -1.5 and 53.0 < y < 54.0 for x,y in coords):
            continue
        roads.append({"osm_type": "way", "osm_id": way["id"], "osm_version": way.get("version"),
                      "osm_timestamp": way.get("timestamp"), "junction": tags["junction"], "name": tags["name"],
                      "coordinates": coords})
    if not roads:
        raise RuntimeError("No current named real Roscoe's roundabout ways; this road check is UNVERIFIED")
    result = {
        "kind": "EXTERNALLY_SOURCED_OSM_UK_ROAD_GEOMETRY",
        "source": "OpenStreetMap contributors",
        "licence": "Open Database License (ODbL) 1.0",
        "source_url": "https://www.openstreetmap.org/copyright",
        "query": QUERY,
        "retrieved_from": host,
        "retrieved_on": date.today().isoformat(),
        "area": "Roscoe's Roundabout, Stockport, Greater Manchester, England",
        "not_legal_truth": "OSM geometry is community-maintained; official signs, lanes and lawful permitted turns require independent corroboration.",
        "ways": roads,
    }
    target = Path(sys.argv[1] if len(sys.argv) > 1 else "app/src/test/resources/independent-osm-roads.json")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps({"result": "real OSM roads downloaded", "ways": len(roads),
                      "nodes": sum(len(r["coordinates"]) for r in roads),
                      "ids": [r["osm_id"] for r in roads], "source": host}))

if __name__ == "__main__":
    main()
