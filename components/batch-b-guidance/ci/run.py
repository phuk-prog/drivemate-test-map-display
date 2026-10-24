#!/usr/bin/env python3
"""Paired actual individual components, same owner-preference cases, no private app checkout."""
import hashlib,json,os,pathlib,re,shutil,subprocess,xml.etree.ElementTree as E
root=pathlib.Path(__file__).resolve().parents[1]
expected={"GuidancePreferenceTest#"+n for n in re.findall(r"@Test\s+fun\s+(\w+)\s*\(",(root/"tests/GuidancePreferenceTest.kt").read_text())}
assert len(expected)==20
results={};errors=[]
for phase in ("baseline","candidate"):
    xml=root/"build/test-results/test"
    shutil.rmtree(xml,ignore_errors=True)
    proc=subprocess.run(["gradle","-p",str(root),"clean","test","-Pphase="+phase,"--no-daemon","--max-workers=3"])
    cases={}
    for f in xml.glob("TEST-*.xml"):
        for c in E.parse(f).getroot().findall("testcase"):
            k=c.get("classname").split(".")[-1]+"#"+c.get("name")
            if k in cases: errors.append(phase+": duplicate case")
            cases[k]="failed" if c.find("failure") is not None or c.find("error") is not None else "skipped" if c.find("skipped") is not None else "passed"
    if proc.returncode: errors.append(phase+": compile/infrastructure failure")
    if set(cases)!=expected: errors.append(phase+": missing/unexpected cases")
    if "skipped" in cases.values(): errors.append(phase+": skipped cases")
    results[phase]={"passed":sum(v=="passed" for v in cases.values()),"total":len(expected),"cases":cases}
if results["candidate"]["passed"]!=20: errors.append("candidate requirements fail")
r={"requirement_ids":["03.03"],"approach_policy_only":"03.02; not arrow visibility or UI acceptance","run_id":os.environ.get("GITHUB_RUN_ID"),"commit":subprocess.check_output(["git","rev-parse","HEAD"],text=True).strip(),"results":results,"source_hashes":{str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in root.rglob("*.kt") if "build" not in p.parts},"errors":errors,"execution_valid":not errors,"ui_runtime_verified":False,"android_auto_verified":False,"batch_accepted":False,"apk_generated":False}
(root/"results").mkdir(exist_ok=True)
(root/"results/comparison.json").write_text(json.dumps(r,indent=2)+"\n")
print(json.dumps(r))
raise SystemExit(1 if errors else 0)
