import json, pathlib, subprocess, xml.etree.ElementTree as ET
root = pathlib.Path(__file__).resolve().parent
results = {}
for phase in ("baseline", "candidate"):
    subprocess.run(["gradle", "-p", str(root / "harness"), "clean", "test", "-Pphase=" + phase, "--no-daemon"], check=True)
    cases = []
    for file in (root / "harness/build/test-results/test").glob("TEST-*.xml"):
        for case in ET.parse(file).getroot().findall("testcase"):
            cases.append({"name": case.attrib["name"], "passed": case.find("failure") is None and case.find("error") is None,
                          "failure": (case.find("failure").attrib.get("message", "") if case.find("failure") is not None else None)})
    assert len(cases) == 15, (phase, len(cases))
    results[phase] = {"passed": sum(c["passed"] for c in cases), "total": len(cases), "cases": cases}
report = {"scope": "isolated Kotlin components, synthetic catalogue; no Android WorkManager, Compose, real downloads or offline-navigation end-to-end proof",
          "baseline_source_sha": "baf6c06de2c3027e2ea7563e9f9e4dacafbbd147", "results": results}
(root / "results.json").write_text(json.dumps(report, indent=2) + "\n")
print(json.dumps(report))
assert results["baseline"]["passed"] == 6, "Baseline changed; investigate rather than force a score"
assert results["candidate"]["passed"] == 15
