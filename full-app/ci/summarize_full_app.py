#!/usr/bin/env python3
"""Summarise a private full-app emulator run of the public full-app tests.

The tests run against the exact combined DriveMate app inside its private repository. Only this
summary, the per-test timelines and their run identity are published; no app source, APK or
personal data. Every expected test must run; any failure fails the summary.

Usage: summarize_full_app.py JUNIT_DIR OUTPUT_DIR OUT RUN_URL APP_COMMIT APP_TREE PUBLIC_TESTS_SHA
"""
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

CLASS = "com.drivemate.app.fullapp.PositionUncertainBannerTest"
EXPECTED = {
    "pref01_01_bannerAppearsThreeSecondsIntoContinuousPoorAccuracy": "01.01",
    "pref01_01_bannerAppearsAfterGpsIsLost": "01.01",
    "pref01_01_bannerClearsWhenPositionRecovers": "01.01",
    "pref01_01_shortGlitchUnderThreeSecondsNeverShowsBanner": "01.01",
    "pref01_01_parkedWithHealthyGpsNeverShowsBanner": "01.01",
}


def main(junit_dir, output_dir, out, run_url, app_commit, app_tree, public_sha):
    results, messages = {}, {}
    for f in Path(junit_dir).rglob("*.xml"):
        for case in ET.parse(f).getroot().iter("testcase"):
            if case.get("classname") != CLASS:
                continue
            bad = case.find("failure") if case.find("failure") is not None else case.find("error")
            status = "failed" if bad is not None else ("skipped" if case.find("skipped") is not None else "passed")
            results[case.get("name")] = status
            if bad is not None:
                messages[case.get("name")] = (bad.get("message") or bad.text or "")[:600]
    timelines = {}
    for f in Path(output_dir).rglob("*.json"):
        try:
            v = json.loads(f.read_text(encoding="utf-8"))
            if v.get("test") in EXPECTED:
                timelines[v["test"]] = v["timeline"]
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
        e["tests"].append({"name": test, "status": results.get(test, "missing"),
                           "failure": messages.get(test), "timeline": timelines.get(test)})
    doc = {
        "suite": "full-app", "scope": "exact-combined-app", "runner": "private-app-repository",
        "run_url": run_url, "app_commit": app_commit, "android_source_tree_sha": app_tree,
        "public_tests_sha": public_sha,
        "environment": "Android emulator API 30 x86_64, AOSP image without Google Play services; GPS via test provider",
        "evidence_limits": [
            "Google Play fused location is not exercised (no Play services on the image).",
            "Fixes are synthetic along an OSM centreline; real GNSS noise and multipath are not modelled.",
            "Emulator, not a physical phone.",
        ],
        "checklist": per_item, "problems": problems, "pass": not problems,
    }
    Path(out).parent.mkdir(parents=True, exist_ok=True)
    Path(out).write_text(json.dumps(doc, indent=1) + "\n", encoding="utf-8")
    for item, e in per_item.items():
        print(f"{item}: {e['passed']}/{e['total']}")
        for t in e["tests"]:
            print(f"  {t['status']:7} {t['name']}" + (f"  — {t['failure'][:200]}" if t["failure"] else ""))
    if problems:
        print("PROBLEMS:", "; ".join(problems))
        sys.exit(1)


if __name__ == "__main__":
    main(*sys.argv[1:8])
