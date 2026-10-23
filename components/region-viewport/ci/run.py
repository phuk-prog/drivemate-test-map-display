#!/usr/bin/env python3
"""Execute exact public copy of the production regional selector, with no APK."""
import hashlib,json,os,pathlib,re,shutil,subprocess,sys,xml.etree.ElementTree as ET
root=pathlib.Path(__file__).resolve().parents[1]
source=root/"src/main/kotlin/com/drivemate/app/data/PublishedRegionCatalog.kt"
test=root/"src/test/kotlin/com/drivemate/app/data/PublishedRegionViewportTest.kt"
expected=re.findall(r"@Test\s+fun\s+(\w+)\(",test.read_text())
assert len(expected)==len(set(expected))==12
out=root/"build/test-results/test"
shutil.rmtree(out,ignore_errors=True)
p=subprocess.run(["gradle","-p",str(root),"test","--no-daemon","--max-workers=2"])
cases={}
for f in out.glob("TEST-*.xml"):
 for x in ET.parse(f).getroot().iter("testcase"):
  name=x.get("name")
  if name in expected:
   if name in cases:raise AssertionError("Duplicate test "+name)
   cases[name]="skipped" if x.find("skipped") is not None else "failed" if x.find("failure") is not None or x.find("error") is not None else "passed"
failures=[x for x in expected if cases.get(x)!="passed"]
res={"type":"Batch A public regional viewport selection, exact app Kotlin source","run_id":os.getenv("GITHUB_RUN_ID"),
"source_git_blob":"2bbf4b3ee5b7a5f11a235775d717d4cdde2f0c0f",
"source_sha256":hashlib.sha256(source.read_bytes()).hexdigest(),
"cases":cases,"expected":12,"passed":12-len(failures),"missing":list(set(expected)-set(cases)),
"compiler_exit":p.returncode,"real_uk_region_coverage_proven":False,
"app_rendering_proven":False,"android_robot_proven":False,"category02_accepted":False,"apk_generated":False}
(root/"results").mkdir(exist_ok=True)
(root/"results/viewport-selection.json").write_text(json.dumps(res,indent=2)+"\n")
print(json.dumps(res))
sys.exit(0 if p.returncode==0 and not failures and len(cases)==12 else 1)
