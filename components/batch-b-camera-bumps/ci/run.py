import hashlib,json,os,pathlib,re,shutil,subprocess,xml.etree.ElementTree as E
r=pathlib.Path(__file__).resolve().parents[1]
test=(r/'src/test/kotlin/com/drivemate/app/controller/WarningWiringTest.kt').read_text()
expected=set(re.findall(r'@Test\s+fun\s+(\w+)\(',test));assert len(expected)==20
methods=(r/'reference/ProposedWarningMethods.txt').read_text()
assert methods in (r/'src/test/kotlin/com/drivemate/app/controller/WarningWiringHarness.kt').read_text()
enum=(r/'src/main/kotlin/com/drivemate/app/data/HazardType.kt').read_text()
assert enum.split('\n',2)[2] == (r/'reference/ProposedHazardType.txt').read_text()
shutil.rmtree(r/'build/test-results/test',ignore_errors=True)
run=subprocess.run(['gradle','-p',str(r),'test','--no-daemon','--max-workers=3'])
seen={};errors=[]
for f in (r/'build/test-results/test').glob('TEST-*.xml'):
 for t in E.parse(f).getroot().findall('testcase'):
  name=t.get('name')
  if name in seen:errors.append('duplicate')
  seen[name]='failed' if t.find('failure') is not None or t.find('error') is not None else 'skipped' if t.find('skipped') is not None else 'passed'
if set(seen)!=expected:errors.append('missing/unexpected')
if run.returncode:errors.append('Gradle failure')
if any(x!='passed' for x in seen.values()):errors.append('failed/skipped')
files=[r/'src/main/kotlin/com/drivemate/app/nav/CameraApproachPolicy.kt',r/'reference/ProposedWarningMethods.txt',r/'reference/ProposedHazardType.txt']
report={'batch':'B','requirements':['09.01','09.02','09.07','09.08','09.09'],'public_sha':os.environ.get('GITHUB_SHA'),'run_id':os.environ.get('GITHUB_RUN_ID'),'scope':'Exact proposed checkHazards+bumpRun and actual CameraApproachPolicy/HazardType enum; synthetic route distances and adapted geometry/Android/output/provider dependencies. Map visibility flag is not rendered-map proof.','passed':sum(x=='passed' for x in seen.values()),'total':20,'cases':seen,'execution_valid':not errors,'errors':errors,'source_hashes':{str(p.relative_to(r)):hashlib.sha256(p.read_bytes()).hexdigest() for p in files},'before_runtime_score':None,'physical_audio_verified':False,'whole_android_compile_verified':False,'rendered_map_verified':False,'camera_provider_accuracy_verified':False,'average_zone_endpoints_verified':False,'batch_accepted':False,'apk_generated':False}
(r/'results').mkdir(exist_ok=True);(r/'results/verification.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
raise SystemExit(0 if not errors else 1)
