import json,pathlib,subprocess,xml.etree.ElementTree as ET,hashlib,os,shutil
root=pathlib.Path(__file__).resolve().parents[1]
out={"scope":"Actual individual production components; synthetic file/network boundaries. UI and combined app unverified.","source_app_sha":"baf6c06de2c3027e2ea7563e9f9e4dacafbbd147","public_commit":os.environ.get("GITHUB_SHA"),"run_id":os.environ.get("GITHUB_RUN_ID"),"results":{},"accepted":False}
valid=True
for phase in ("baseline","candidate"):
 xml=root/"harness/build/test-results/test";shutil.rmtree(xml,ignore_errors=True)
 run=subprocess.run(["gradle","-p",str(root/"harness"),"clean","test","-Pphase="+phase,"--no-daemon","--max-workers=2","--tests","*DownloadStoragePreferencesTest"],capture_output=True,text=True)
 cases={}
 for f in xml.glob("TEST-*.xml"):
  for c in ET.parse(f).getroot().findall("testcase"):
   cases[c.get("name")]="failed" if c.find("failure") is not None or c.find("error") is not None else "skipped" if c.find("skipped") is not None else "passed"
 result={"cases":cases,"passed":sum(v=="passed" for v in cases.values()),"total":len(cases),"execution_available":len(cases)==8,"source_hashes":{f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in (root/phase).glob("*.kt")}}
 if not result["execution_available"]: print(run.stdout[-12000:]);print(run.stderr[-4000:])
 out["results"][phase]=result
 valid=valid and result["execution_available"] and (phase=="baseline" or result["passed"]==result["total"])
(root/"results").mkdir(exist_ok=True);(root/"results/comparison.json").write_text(json.dumps(out,indent=2)+"\n")
print(json.dumps(out,indent=2))
raise SystemExit(0 if valid else 1)
