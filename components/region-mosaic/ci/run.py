#!/usr/bin/env python3
"""Region mosaic and optional region download: exact app files, old vs new.

before: the old app's region selection (main 10a2e97) draws one region; the six driving-overview
        views are checked for blank published areas.
after:  the new app files (mosaic + download rules) run the same six views plus 14 component tests.
Every app file must be byte-identical to the private app commit in source-manifest.json.
"""
import hashlib, json, os, pathlib, re, shutil, subprocess, tempfile, xml.etree.ElementTree as ET

root = pathlib.Path(__file__).resolve().parents[1]
pkg = pathlib.Path("com/drivemate/app/data")
manifest = json.loads((root / "source-manifest.json").read_text())

def blob(p):
    raw = p.read_bytes()
    return hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest()

for name, sha in manifest["candidate"].items():
    assert blob(root / "src/main/kotlin" / pkg / name) == sha, "candidate source mismatch: " + name
assert blob(root / "baseline/PublishedRegionCatalog.kt") == manifest["baseline"]["PublishedRegionCatalog.kt"], "baseline mismatch"

def names(paths):
    found = []
    for p in paths:
        found += re.findall(r"@Test\s+fun\s+(\w+)\(", p.read_text())
    return found

coverage = root / "coverage/PublishedViewDrawnTest.kt"
component_tests = sorted((root / "src/test/kotlin" / pkg).glob("*Test.kt"))
coverage_names = names([coverage])
component_names = names(component_tests)
assert len(coverage_names) == 6 and len(component_names) == 14, (coverage_names, component_names)

def run(label, main_files, shim, tests):
    with tempfile.TemporaryDirectory(prefix="dm-region-mosaic-") as td:
        dest = pathlib.Path(td)
        for p in ("build.gradle.kts", "settings.gradle.kts"):
            shutil.copy2(root / p, dest / p)
        main = dest / "src/main/kotlin" / pkg
        test = dest / "src/test/kotlin" / pkg
        main.mkdir(parents=True); test.mkdir(parents=True)
        for f in main_files:
            shutil.copy2(f, main / f.name)
        shutil.copy2(shim, test / "Drawn.kt")
        for f in tests:
            shutil.copy2(f, test / f.name)
        shutil.copytree(root / "src/test/resources", dest / "src/test/resources")
        process = subprocess.run(["gradle", "-p", str(dest), "test", "--no-daemon", "--max-workers=2", "--continue"],
                                 capture_output=True, text=True)
        print(process.stdout[-6000:]); print(process.stderr[-3000:])
        views = [l for l in process.stdout.splitlines() if "VIEW " in l]
        cases, notes = {}, {}
        for xml in (dest / "build/test-results/test").glob("TEST-*.xml"):
            tree = ET.parse(xml).getroot()
            for line in (tree.findtext("system-out") or "").splitlines():
                if line.startswith("VIEW "):
                    views.append(line)
            for e in tree.iter("testcase"):
                bad = e.find("failure") if e.find("failure") is not None else e.find("error")
                cases[e.get("name")] = "skipped" if e.find("skipped") is not None else "failed" if bad is not None else "passed"
                if bad is not None:
                    notes[e.get("name")] = (bad.get("message") or "")[:300]
        expected = names(tests)
        assert set(cases) == set(expected), (label, "missing/extra", set(expected) ^ set(cases))
        return {"cases": cases, "failures": notes, "views": sorted(set(views)),
                "passed": sum(v == "passed" for v in cases.values()), "total": len(expected),
                "gradle_exit": process.returncode}

data = root / "src/main/kotlin" / pkg
before = run("before", [root / "baseline/PublishedRegionCatalog.kt"], root / "ci/baseline/Drawn.kt", [coverage])
after = run("after", sorted(data.glob("*.kt")), root / "ci/candidate/Drawn.kt", [coverage] + component_tests)
cov_after = sum(after["cases"][n] == "passed" for n in coverage_names)
result = {
    "suite": "region-mosaic", "checklist": ["02.x map overview: no blank published areas", "optional region download rules"],
    "scope": "exact app components (JVM); MapLibre rendering and MapData wiring are covered by the private full-app run",
    "app_commit": manifest["app_commit"], "public_run_id": os.getenv("GITHUB_RUN_ID"),
    "public_head_sha": os.getenv("GITHUB_SHA"),
    "before": {"overview_views": f'{before["passed"]}/6', **before},
    "after": {"overview_views": f"{cov_after}/6", "all": f'{after["passed"]}/{after["total"]}', **after},
    "limits": [
        "Catalogue fixed at 2026-10-24 (46 regions); regions published later are not included.",
        "Coverage is checked on region bounds, not on tiles actually drawn on screen.",
        "Drawn.kt restates MapData's one-line neighbour request; MapData needs Android.",
    ],
}
(root / "results").mkdir(exist_ok=True)
(root / "results/region-mosaic.json").write_text(json.dumps(result, indent=1) + "\n")
print(json.dumps({"before": result["before"]["overview_views"], "after_views": result["after"]["overview_views"],
                  "after_all": result["after"]["all"]}))
assert after["passed"] == after["total"] and after["gradle_exit"] == 0, "new app files did not pass every test"
