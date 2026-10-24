#!/usr/bin/env python3
"""Turn the emulator run into one bounded result per checklist ID.

Every expected test must have run and passed; a missing, skipped or extra test fails the run.
Verdict JSONs written by the tests are attached so counts, not just pass/fail, are public.
"""
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

EXPECTED = {
    "pref02_01_dayTopDownZoom17RouteStaysOnOwnCarriageway": "02.01",
    "pref02_01_nightDrivingTiltZoom18RouteStaysOnOwnCarriageway": "02.01",
    "pref02_01_negativeControlRouteOnOppositeCarriagewayIsRejected": "02.01",
    "pref02_03_slowIsAmberHeavyIsRedOnlyOnTheirSections": "02.03",
    "pref02_03_negativeControlSwappedTrafficColoursAreRejected": "02.03",
}


def main(junit_dir, output_dir, out, run_url, public_sha):
    results = {}
    for f in Path(junit_dir).rglob("*.xml"):
        for case in ET.parse(f).getroot().iter("testcase"):
            if case.get("classname") != "com.drivemate.render.RenderedRouteTest":
                continue
            status = "passed"
            if case.find("failure") is not None or case.find("error") is not None:
                status = "failed"
            elif case.find("skipped") is not None:
                status = "skipped"
            results[case.get("name")] = status
    verdicts = {}
    for f in Path(output_dir).rglob("*.json"):
        try:
            v = json.loads(f.read_text(encoding="utf-8"))
            verdicts[v["test"]] = v
        except (ValueError, KeyError):
            continue
    problems = [f"missing {t}" for t in EXPECTED if t not in results]
    problems += [f"unexpected {t}" for t in results if t not in EXPECTED]
    problems += [f"{t} {s}" for t, s in results.items() if s != "passed"]
    per_item = {}
    for test, item in EXPECTED.items():
        e = per_item.setdefault(item, {"passed": 0, "total": 0, "tests": []})
        e["total"] += 1
        e["passed"] += results.get(test) == "passed"
        e["tests"].append({"name": test, "status": results.get(test, "missing"), "verdict": verdicts.get(test)})
    doc = {
        "suite": "components/rendered-map", "run_url": run_url, "public_head_sha": public_sha,
        "scope": "isolated-component",
        "evidence_limits": [
            "Renders DriveMate's published RouteLayers only, not the combined app, its UK map style or its camera.",
            "OSM centrelines are observed geography, not legal or lane truth.",
            "Traffic bands are placed by the test; TrafficRouteOverlay matching of real reports is not exercised here.",
            "Emulator SwiftShader GPU, not a physical phone GPU.",
        ],
        "checklist": per_item, "problems": problems, "pass": not problems,
    }
    Path(out).parent.mkdir(parents=True, exist_ok=True)
    Path(out).write_text(json.dumps(doc, indent=1) + "\n", encoding="utf-8")
    for item, e in per_item.items():
        print(f"{item}: {e['passed']}/{e['total']}")
    if problems:
        print("PROBLEMS:", "; ".join(problems))
        sys.exit(1)


if __name__ == "__main__":
    main(*sys.argv[1:6])
