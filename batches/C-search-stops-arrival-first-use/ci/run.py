import subprocess, pathlib, json, sys
root=pathlib.Path(__file__).resolve().parents[1]
results={}
for phase in ("baseline","candidate"):
    p=subprocess.run(["gradle","--no-daemon","-q","clean","run","-Pphase="+phase],cwd=root,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    print(p.stdout,flush=True)
    if p.returncode: raise SystemExit(phase+" infrastructure/compile failure: no result claimed")
    cases=[line.split("|",3) for line in p.stdout.splitlines() if line.startswith("CASE|")]
    if len(cases)<35: raise SystemExit("Missing behavioral case output")
    results[phase]={"total":len(cases),"passed":sum(c[2]=="PASS" for c in cases),"failed":sum(c[2]=="FAIL" for c in cases),"cases":[{"name":c[1],"status":c[2]} for c in cases]}
out=root/"results";out.mkdir(exist_ok=True)
report={"scope":"isolated public source components, synthetic UK geometry/provider timings; no UI, legal accessibility, APK or full checklist acceptance","local_preflight":"UNAVAILABLE","results":results}
(out/"comparison.json").write_text(json.dumps(report,indent=2)+"\n")
print(json.dumps(report))
if results["candidate"]["failed"]: raise SystemExit("Candidate behavior failures")
if results["baseline"]["failed"]==0: raise SystemExit("Expected independently observed baseline regressions were not reproduced")
