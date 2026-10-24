#!/usr/bin/env python3
"""Turn the emulator runs into one bounded result per checklist ID and phase.

The same tests run against the app before (baseline) and after (candidate) its latest change.
Every expected test must run in both phases. The candidate must pass all of them. A baseline
failure is a recorded before-score, not a CI failure, except a negative control: those must be
rejected in both phases, or the judge itself is broken. Verdict JSONs written by the tests are
attached so counts, not just pass/fail, are public.

Usage: summarize.py OUT RUN_URL SHA baseline=JUNIT_DIR:OUTPUT_DIR candidate=JUNIT_DIR:OUTPUT_DIR
"""
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROUTE = "com.drivemate.render.RenderedRouteTest"
ARROW = "com.drivemate.render.CarArrowTest"
EXPECTED = {
    (ROUTE, "pref02_01_dayTopDownZoom17RouteStaysOnOwnCarriageway"): "02.01",
    (ROUTE, "pref02_01_nightDrivingTiltZoom18RouteStaysOnOwnCarriageway"): "02.01",
    (ROUTE, "pref02_01_negativeControlRouteOnOppositeCarriagewayIsRejected"): "02.01",
    (ROUTE, "pref02_03_slowIsAmberHeavyIsRedOnlyOnTheirSections"): "02.03",
    (ROUTE, "pref02_03_negativeControlSwappedTrafficColoursAreRejected"): "02.03",
    (ARROW, "pref02_04_northUpZoom16ArrowOnCarriagewayAlongRoadLaneToCarriagewayWide"): "02.04",
    (ARROW, "pref02_04_northUpZoom17ArrowOnCarriagewayAlongRoadLaneToCarriagewayWide"): "02.04",
    (ARROW, "pref02_04_northUpZoom18ArrowOnCarriagewayAlongRoadLaneToCarriagewayWide"): "02.04",
    (ARROW, "pref02_04_drivingViewZoom17ArrowPointsUpTheRoad"): "02.04",
    (ARROW, "pref02_04_zoom16SizeSurvivesAStyleReload"): "02.04",
    (ARROW, "pref02_04_negativeControlArrowFacingAgainstTrafficIsRejected"): "02.04",
    (ARROW, "pref02_04_negativeControlArrowOffTheCentrelineIsRejected"): "02.04",
}


def phase_results(junit_dir, output_dir):
    results = {}
    for f in Path(junit_dir).rglob("*.xml"):
        for case in ET.parse(f).getroot().iter("testcase"):
            if case.get("classname") not in (ROUTE, ARROW):
                continue
            status = "passed"
            if case.find("failure") is not None or case.find("error") is not None:
                status = "failed"
            elif case.find("skipped") is not None:
                status = "skipped"
            results[(case.get("classname"), case.get("name"))] = status
    verdicts = {}
    for f in Path(output_dir).rglob("*.json"):
        try:
            v = json.loads(f.read_text(encoding="utf-8"))
            verdicts[v["test"]] = v
        except (ValueError, KeyError):
            continue
    return results, verdicts


def main(out, run_url, sha, *phases):
    doc = {"suite": "components/rendered-map", "run_url": run_url, "public_head_sha": sha,
           "scope": "isolated-component",
           "evidence_limits": [
               "Renders DriveMate's published RouteLayers and CarMarker only, not the combined app, its UK map style or its camera.",
               "OSM centrelines are observed geography, not legal or lane truth; lane width is the DMRB CD 127 standard 3.65 m, not a measured width.",
               "Traffic bands and arrow fixes are placed by the test; route matching of real reports and GPS snapping are not exercised here.",
               "Emulator SwiftShader GPU, not a physical phone GPU.",
           ],
           "phases": {}, "problems": []}
    for arg in phases:
        phase, dirs = arg.split("=", 1)
        junit_dir, output_dir = dirs.split(":", 1)
        results, verdicts = phase_results(junit_dir, output_dir)
        problems = [f"{phase}: missing {t}" for (c, t) in EXPECTED if (c, t) not in results]
        problems += [f"{phase}: skipped {t}" for (c, t), s in results.items() if s == "skipped"]
        problems += [f"{phase}: unexpected {t}" for (c, t) in results if (c, t) not in EXPECTED]
        for (c, t), s in results.items():
            if s == "failed" and (phase == "candidate" or "negativeControl" in t):
                problems.append(f"{phase}: {t} failed")
        per_item = {}
        for (c, t), item in EXPECTED.items():
            e = per_item.setdefault(item, {"passed": 0, "total": 0, "tests": []})
            e["total"] += 1
            e["passed"] += results.get((c, t)) == "passed"
            e["tests"].append({"name": t, "status": results.get((c, t), "missing"),
                               "verdict": verdicts.get(t)})
        doc["phases"][phase] = per_item
        doc["problems"] += problems
    doc["pass"] = not doc["problems"]
    Path(out).parent.mkdir(parents=True, exist_ok=True)
    Path(out).write_text(json.dumps(doc, indent=1) + "\n", encoding="utf-8")
    items = sorted({i for p in doc["phases"].values() for i in p})
    for item in items:
        row = [f"{ph} {doc['phases'][ph][item]['passed']}/{doc['phases'][ph][item]['total']}" for ph in doc["phases"]]
        print(f"{item}: " + " → ".join(row))
    if doc["problems"]:
        print("PROBLEMS:", "; ".join(doc["problems"]))
        sys.exit(1)


if __name__ == "__main__":
    main(*sys.argv[1:])
