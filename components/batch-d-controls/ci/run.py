import pathlib, subprocess, xml.etree.ElementTree as ET, json, os, hashlib
root=pathlib.Path(__file__).resolve().parents[1]
p=subprocess.run(["gradle","-p",str(root),"test","--no-daemon","--max-workers=2"])
cases={}
for f in (root/"build/test-results/test").glob("TEST-*.xml"):
 for t in ET.parse(f).getroot().iter("testcase"):
  cases[t.get("name")]="failed" if t.find("failure") is not None or t.find("error") is not None else "skipped" if t.find("skipped") is not None else "passed"
result={"app_base":"baf6c06de2c3027e2ea7563e9f9e4dacafbbd147","run_id":os.getenv("GITHUB_RUN_ID"),"public_head":os.getenv("GITHUB_SHA"),"cases":cases,"passed":sum(v=="passed" for v in cases.values()),"expected":7,"gradle_exit":p.returncode,"scope":"Actual bounded SoundMode enum and actual LocalPrivacySettings against synthetic preference storage. No UI, full app, Android persistence, account, navigation or APK proof.","source_sha256":{f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in (root/"src/main/kotlin/com/drivemate/app/data").glob("*.kt")}}
out=root/"results/controls.json"; out.parent.mkdir(exist_ok=True);out.write_text(json.dumps(result,indent=2)+"\n");print(json.dumps(result))
assert len(cases)==7, "All seven tests must execute"
# Baseline failures are recorded; candidate check is enabled in the subsequent source-only change.

assert p.returncode == 0 and all(v=="passed" for v in cases.values()), "Candidate must pass all unchanged tests"
