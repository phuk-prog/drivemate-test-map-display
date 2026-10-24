import pathlib,subprocess,xml.etree.ElementTree as ET,json,os
r=pathlib.Path(__file__).resolve().parents[1]
p=subprocess.run(["gradle","-p",str(r/"harness"),"clean","test","-Pphase=candidate","--tests","*CleanupSelectedAreasTest","--no-daemon","--max-workers=2"],capture_output=True,text=True)
print(p.stdout[-12000:]);print(p.stderr[-8000:])
cases={}
for f in (r/"harness/build/test-results/test").glob("TEST-*.xml"):
 for c in ET.parse(f).getroot().findall("testcase"):
  cases[c.get("name")]="failed" if c.find("failure") is not None or c.find("error") is not None else "skipped" if c.find("skipped") is not None else "passed"
out={"scope":"Additional newly integrated per-area cleanup source runtime checks; baseline class absent, baseline score unavailable.","run_id":os.environ.get("GITHUB_RUN_ID"),"cases":cases,"total":len(cases),"passed":sum(x=="passed" for x in cases.values()),"accepted":False}
(r/"results/cleanup.json").write_text(json.dumps(out,indent=2)+"\n");print(json.dumps(out))
raise SystemExit(0 if p.returncode==0 and len(cases)==6 and all(v=="passed" for v in cases.values()) else 1)
