import pathlib,subprocess,xml.etree.ElementTree as ET,json,os,hashlib
root=pathlib.Path(__file__).resolve().parents[1]
p=subprocess.run(["xvfb-run","-a","gradle","-p",str(root),"test","--no-daemon","--max-workers=2"])
cases={}
for f in (root/"build/test-results/test").glob("TEST-*.xml"):
 for t in ET.parse(f).getroot().iter("testcase"):
  cases[t.get("name")]="failed" if t.find("failure") is not None or t.find("error") is not None else "skipped" if t.find("skipped") is not None else "passed"
r={"run_id":os.getenv("GITHUB_RUN_ID"),"public_sha":os.getenv("GITHUB_SHA"),"expected":10,"cases":cases,"gradle_exit":p.returncode,"baseline":{"status":"source_inspected_absent","runtime_score":None,"note":"Private base baf6c06de2c3027e2ea7563e9f9e4dacafbbd147 has no Settings symbol legend; no original UI runtime score was invented."},"scope":"Actual candidate Compose legend executed on desktop under Xvfb with exact existing HazardType enum; not Android SettingsSheet, map renderer, tap popup or whole-app proof.","accepted_items":[],"component_sha256":hashlib.sha256((root/"src/main/kotlin/com/drivemate/app/ui/WarningSymbolLegend.kt").read_bytes()).hexdigest()}
f=root/"results/legend.json";f.parent.mkdir(exist_ok=True);f.write_text(json.dumps(r,indent=2)+"\n");print(json.dumps(r))
assert p.returncode==0 and len(cases)==10 and all(v=="passed" for v in cases.values()), "All ten legend tests must execute and pass"
