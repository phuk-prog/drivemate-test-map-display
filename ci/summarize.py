#!/usr/bin/env python3
"""Category 2 baseline evidence. Run only in private app CI; output contains no app source."""
import argparse
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--app", required=True)
    parser.add_argument("--reports", required=True)
    parser.add_argument("--contracts", required=True)
    parser.add_argument("--app-sha", required=True)
    parser.add_argument("--public-sha", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    contracts = json.loads(Path(args.contracts).read_text())
    tests = {}
    for report in Path(args.reports).rglob("TEST-*.xml"):
        root = ET.parse(report).getroot()
        for tc in root.iter("testcase"):
            name = tc.get("name", "")
            if not name.startswith("pref02_"): continue
            status = "passed"
            if tc.find("failure") is not None: status = "failed"
            if tc.find("error") is not None: status = "error"
            if tc.find("skipped") is not None: status = "skipped"
            if name in tests: status = "duplicate"
            tests[name] = status
    expected = [n for row in contracts["items"] for n in row["tests"]]
    extra = sorted(set(tests) - set(expected))
    missing = sorted(set(expected) - set(tests))
    source = (Path(args.app) / "app/src/main/java/com/drivemate/app/map/MapController.kt").read_text()
    apply = source.split("private fun applyTraffic(s: Style)", 1)
    # A removal-only function is clear evidence that live traffic route shading is missing.
    block = apply[1].split("\n    /**", 1)[0] if len(apply) == 2 else ""
    traffic = ("missing" if not block or ("s.removeLayer(TRAFFIC_LAYER)" in block and
        "source.setGeoJson(" not in block)
        else "wired" if "source.setGeoJson(" in block and "trafficEnabled" in block
        else "inconclusive")
    result = {
      "category": "02", "app_sha": args.app_sha, "public_contract_sha": args.public_sha,
      "total_expected_tests": len(expected), "executed": len(tests), "missing": missing,
      "unexpected": extra, "tests": tests,
      "static_findings": {"traffic_layer_integration": traffic},
      "scope": "private-app Kotlin unit tests using synthetic keyless traffic fixtures and static integration inspection; no live provider, rendered route or physical-device claims",
      "traffic_test_mode": contracts.get("traffic_test_mode", "UNSPECIFIED"),
      "tomtom_live_api_verified": False,
      "tomtom_api_key_required_for_testing": False,
      "items": {}
    }
    for row in contracts["items"]:
        observed = [tests.get(name, "not_run") for name in row["tests"]]
        status = "logic_passed_not_visual_acceptance" if all(x == "passed" for x in observed) else "test_failure_or_missing"
        if row["id"] == "02.03" and traffic == "missing": status = "missing_traffic_shading"
        result["items"][row["id"]] = {"status":status,"test_outcomes":observed,
             "remaining":row["remaining"]}
    Path(args.output).parent.mkdir(parents=True, exist_ok=True)
    Path(args.output).write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps({
      "app_sha":args.app_sha,
      "tests_passed":sum(x == "passed" for x in tests.values()),
      "tests_expected":len(expected),
      "missing":missing,"unexpected":extra,
      "traffic_integration":traffic,
      "traffic_test_mode":contracts.get("traffic_test_mode"),
      "notice":"Synthetic, no-key TomTom simulations exercise real app parser/overlay logic only; live traffic and rendered road behaviour remain unverified."
    }, indent=2))
    return int(bool(missing or extra or any(v != "passed" for v in tests.values())))

if __name__ == "__main__":
    sys.exit(main())
