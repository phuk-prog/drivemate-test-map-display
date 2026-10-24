import pathlib,subprocess,json,xml.etree.ElementTree as ET,os
root=pathlib.Path(__file__).resolve().parents[1]
p=subprocess.run(["gradle","-p",str(root),"testDebugUnitTest","--no-daemon","--max-workers=3"])
cases={}
for f in (root/"build/test-results/testDebugUnitTest").glob("TEST-*.xml"):
 for t in ET.parse(f).getroot().findall("testcase"):
  cases[t.get("name")]="failed" if t.find("failure") is not None or t.find("error") is not None else "skipped" if t.find("skipped") is not None else "passed"
r={"requirement":"17.01","scope":"Actual isolated Compose tutorial in Robolectric native graphics; no combined-app launch or APK","before":{"runtime_score":None,"status":"No prior tutorial source exists; baseline absence observed by source inspection only"},"after":{"total":len(cases),"passed":sum(x=="passed" for x in cases.values()),"cases":cases},"run_id":os.getenv("GITHUB_RUN_ID"),"private_candidate":"f34e18283a42ef8919b9e2b1bf5e166c98721850","app_integration_verified":False,"apk_generated":False}
(root/"results").mkdir(exist_ok=True);(root/"results/tutorial.json").write_text(json.dumps(r,indent=2)+"\n");print(json.dumps(r))
if p.returncode or len(cases)!=4 or any(x!="passed" for x in cases.values()): raise SystemExit("Missing/failed tutorial runtime case")
