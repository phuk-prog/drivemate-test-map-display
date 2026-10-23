#!/usr/bin/env python3
"""One unchanged 18-case suite on frozen old and current exact-app components."""
import hashlib,json,os,pathlib,re,shutil,subprocess,sys,tempfile,xml.etree.ElementTree as ET
root=pathlib.Path(__file__).resolve().parents[1]
src=pathlib.Path("src/main/kotlin/com/drivemate/app/data/PublishedRegionCatalog.kt")
old=root/"baseline/PublishedRegionCatalog.kt"
new=root/src
test_root=root/"src/test/kotlin/com/drivemate/app/data"
names=[]
for p in test_root.glob("*Test.kt"):
    names+=re.findall(r"@Test\s+fun\s+(\w+)\(",p.read_text())
assert len(names)==len(set(names))==18,(len(names),names)
def blob(p):
    raw=p.read_bytes()
    return hashlib.sha1(b"blob "+str(len(raw)).encode()+b"\0"+raw).hexdigest()
assert blob(old)=="2bbf4b3ee5b7a5f11a235775d717d4cdde2f0c0f","Frozen baseline source mismatch"
assert blob(new)=="f8343000d071bfc8802c0e6ce5f5867922378431","Candidate app source mismatch"
def run(source, label):
    with tempfile.TemporaryDirectory(prefix="dm-public-viewport-") as td:
        dest=pathlib.Path(td)
        for p in ("build.gradle.kts","settings.gradle.kts"):shutil.copy2(root/p,dest/p)
        target=dest/src;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
        shutil.copytree(root/"src/test",dest/"src/test")
        process=subprocess.run(["gradle","-p",str(dest),"test","--no-daemon","--max-workers=2"])
        found={}
        for xml in (dest/"build/test-results/test").glob("TEST-*.xml"):
            for e in ET.parse(xml).getroot().iter("testcase"):
                k=e.get("name")
                if k not in names:continue
                if k in found:raise AssertionError("duplicate test outcome: "+k)
                found[k]="skipped" if e.find("skipped") is not None else "failed" if e.find("failure") is not None or e.find("error") is not None else "passed"
        assert set(found)==set(names),(label,"missing/extra cases",set(names)-set(found))
        assert all(s!="skipped" for s in found.values()),(label,"skipped cases")
        return {"cases":found,"passed":sum(s=="passed" for s in found.values()),
                "expected":len(names),"gradle_exit":process.returncode,
                "source_blob":blob(source),"sha256":hashlib.sha256(source.read_bytes()).hexdigest()}
before=run(old,"original")
after=run(new,"candidate")
assert after["passed"]==18 and after["gradle_exit"]==0, "Candidate did not pass new source-backed contract"
assert before["passed"]<after["passed"],"No demonstrated before/after sensitivity"
result={"scope":"Public unchanged geometry-policy tests against exact baseline/candidate Kotlin component",
        "app_candidate_commit":"d93426df8c17634f917c90fe3fa53dad6f9dac88",
        "public_run_id":os.getenv("GITHUB_RUN_ID"),
        "before":before,"after":after,
        "expected_road_truth":"none; synthetic region rectangles only",
        "live_map_catalog_test":"https://github.com/phuk-prog/drivemate-test-map-display/actions/runs/40030682269",
        "actual_tiles_present_verified":False,"android_gpu_rendering_verified":False,
        "navigation_robot_verified":False,"category02_accepted":False,"apk_built":False}
(root/"results").mkdir(exist_ok=True)
(root/"results/viewport-selection.json").write_text(json.dumps(result,indent=2)+"\n")
print(json.dumps({"before":f'{before["passed"]}/18',"after":f'{after["passed"]}/18',
                  "changed_expectations":"largest available published viewport instead of smallest centre when no full coverage",
                  "integrated_android_gpu_verified":False,"batch_a_complete":False}))
