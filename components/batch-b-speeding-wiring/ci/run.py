import hashlib,json,os,pathlib,re,shutil,subprocess,xml.etree.ElementTree as E
r=pathlib.Path(__file__).resolve().parents[1]
test=r/'src/test/kotlin/com/drivemate/app/controller/SpeedingWiringTest.kt'
expected=set(re.findall(r'@Test\s+fun\s+(\w+)\(',test.read_text()))
assert len(expected)==18
method=(r/'reference/ProposedPrivateMethod.txt').read_text()
harness=(r/'src/test/kotlin/com/drivemate/app/controller/SpeedingWiringHarness.kt').read_text()
assert method in harness
shutil.rmtree(r/'build/test-results/test',ignore_errors=True)
run=subprocess.run(['gradle','-p',str(r),'test','--no-daemon','--max-workers=3'])
seen={};errors=[]
for f in (r/'build/test-results/test').glob('TEST-*.xml'):
    for t in E.parse(f).getroot().findall('testcase'):
        name=t.get('name')
        if name in seen:errors.append('duplicate case')
        seen[name]='failed' if t.find('failure') is not None or t.find('error') is not None else 'skipped' if t.find('skipped') is not None else 'passed'
if set(seen)!=expected:errors.append('missing/unexpected cases')
if run.returncode:errors.append('Gradle failure')
if any(x!='passed' for x in seen.values()):errors.append('failed/skipped case')
policy_hash=hashlib.sha256((r/'src/main/kotlin/com/drivemate/app/nav/SpeedingWarningPolicy.kt').read_bytes()).hexdigest()
if policy_hash!='50db4e4e7ef48303b39af102bff368d221b390769b9291b82794ac19c52f714a':errors.append('tested policy mismatch')
report={'batch':'B','requirements':['09.03','09.04'],'private_base':'9f01c0a1f7154861941eee57fb52a471917b7ce5','public_sha':os.environ.get('GITHUB_SHA'),'run_id':os.environ.get('GITHUB_RUN_ID'),'scope':'Actual proposed private warnIfSpeeding method executed unchanged inside deterministic dependency harness; not complete private controller Android/UI/Voice execution','cases':seen,'passed':sum(x=='passed' for x in seen.values()),'total':18,'execution_valid':not errors,'errors':errors,'policy_sha256':policy_hash,'method_sha256':hashlib.sha256(method.encode()).hexdigest(),'private_wiring_staged':False,'batch_accepted':False,'apk_generated':False}
(r/'results').mkdir(exist_ok=True);(r/'results/verification.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
raise SystemExit(0 if not errors else 1)
