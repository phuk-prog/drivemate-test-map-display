from pathlib import Path
import subprocess, shutil, xml.etree.ElementTree as ET, json, hashlib
root=Path(__file__).resolve().parents[1]
results={}
for phase in ("baseline","candidate"):
    dest=root/"src/main/java/com/drivemate/app/ui"
    for name in ("NavigationHeader.kt","RenderCard.kt"):
        shutil.copyfile(root/"phases"/phase/name,dest/name)
    proc=subprocess.run(["gradle","--no-daemon","--console=plain","clean","testDebugUnitTest"],cwd=root)
    tests=[]
    for report in (root/"build/test-results/testDebugUnitTest").glob("TEST-*.xml"):
        for c in ET.parse(report).getroot().iter("testcase"):
            tests.append({"name":c.attrib["name"],"passed":c.find("failure") is None and c.find("error") is None and c.find("skipped") is None})
    if len(tests)!=10: raise SystemExit(f"{phase}: expected 10 executed render tests, got {len(tests)}")
    results[phase]={"passed":sum(t["passed"] for t in tests),"total":len(tests),"tests":tests,"exit_code":proc.returncode}
    shutil.copytree(root/"build/reports/tests/testDebugUnitTest",root/"results"/phase,dirs_exist_ok=True)
results["limitations"]=["Individual real Compose header; published Shield model subset and synthetic display inputs.","Baseline wrapper represents actual unconditional TurnBanner icon call; candidate wrapper supplies proposed policy.","No private controller, real GPS, road legality, Android Auto host or live provider proof.","Local preflight UNAVAILABLE: executor failed."]
results["source"]={"private_base":"baf6c06de2c3027e2ea7563e9f9e4dacafbbd147","header_git_blob":"c491d7785af9d50107a29f1cc764d05f6925ff91","baseline_header_sha256":hashlib.sha256((root/"phases/baseline/NavigationHeader.kt").read_bytes()).hexdigest(),"candidate_header_sha256":hashlib.sha256((root/"phases/candidate/NavigationHeader.kt").read_bytes()).hexdigest()}
(root/"results").mkdir(exist_ok=True)
(root/"results/verification.json").write_text(json.dumps(results,indent=2)+"\n")
print(json.dumps(results,indent=2))
if results["candidate"]["passed"]!=10 or results["candidate"]["exit_code"]!=0: raise SystemExit(1)
