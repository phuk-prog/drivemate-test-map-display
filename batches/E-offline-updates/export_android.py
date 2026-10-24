import json, pathlib, xml.etree.ElementTree as ET
root = pathlib.Path(__file__).resolve().parent
cases = []
for path in (root / "android-harness/build/test-results/testDebugUnitTest").glob("TEST-*.xml"):
    for case in ET.parse(path).getroot().findall("testcase"):
        cases.append({"class": case.get("classname"), "name": case.get("name"),
                      "passed": case.find("failure") is None and case.find("error") is None and case.find("skipped") is None})
report = {"scope": "Exact Android approval scheduler and Compose control; WorkManager real; download boundary fake; no actual HTTP/map/navigation proof",
          "passed": sum(c["passed"] for c in cases), "total": len(cases), "cases": cases}
(root / "android-results.json").write_text(json.dumps(report, indent=2) + "\n")
print(json.dumps(report))
assert len(cases) == 9, ("Missing or duplicate Android cases", len(cases))
assert all(c["passed"] for c in cases)
assert (root / "candidate/OfflineUpdatePolicy.kt").read_bytes() == (root / "android-sources/OfflineUpdatePolicy.kt").read_bytes()
