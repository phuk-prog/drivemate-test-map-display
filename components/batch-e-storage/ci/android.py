import pathlib,subprocess,xml.etree.ElementTree as ET,json,os
root=pathlib.Path(__file__).resolve().parents[1]
run=subprocess.run(["gradle","-p",str(root/"android-harness"),"testDebugUnitTest","--no-daemon","--max-workers=2"],capture_output=True,text=True)
print(run.stdout[-24000:]);print(run.stderr[-16000:])
cases={}
for f in (root/"android-harness/build/test-results/testDebugUnitTest").glob("TEST-*.xml"):
 for c in ET.parse(f).getroot().findall("testcase"):
  cases[c.get("classname")+"#"+c.get("name")]="failed" if c.find("failure") is not None or c.find("error") is not None else "skipped" if c.find("skipped") is not None else "passed"
out={"scope":"Actual RegionalMaps Worker and OfflineMaps panel/service compilation; external publisher, native maps and session are explicit synthetic boundaries. No rendered UI acceptance.","run_id":os.environ.get("GITHUB_RUN_ID"),"cases":cases,"total":len(cases),"passed":sum(v=="passed" for v in cases.values()),"accepted":False}
(root/"results").mkdir(exist_ok=True);(root/"results/android.json").write_text(json.dumps(out,indent=2)+"\n")
print(json.dumps(out,indent=2))
raise SystemExit(0 if run.returncode==0 and len(cases)==7 and all(v=="passed" for v in cases.values()) else 1)
