import hashlib,json,os,pathlib,re,shutil,subprocess,xml.etree.ElementTree as E
r=pathlib.Path(__file__).resolve().parents[1]
expected=set(re.findall(r'@Test\s+fun\s+(\w+)\(', (r/'src/test/kotlin/com/drivemate/app/nav/VoiceDeliveryTest.kt').read_text()))
assert len(expected)==20
harness=(r/'src/test/kotlin/com/drivemate/app/nav/VoiceDeliveryHarness.kt').read_text()
for method in (r/'reference/VoiceMethods.txt').read_text().split('\n---METHOD---\n'):assert method in harness
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
report={'batch':'B','requirements':['09.03','09.04','10.18','10.23','10.24'],'public_sha':os.environ.get('GITHUB_SHA'),'run_id':os.environ.get('GITHUB_RUN_ID'),'scope':'Actual proposed Voice queue/playback policy + exact say/enqueue/allowed/pump/sound setter/TTS-start callback in deterministic dependency harness; Android engines/scheduling/audio not executed','passed':sum(x=='passed' for x in seen.values()),'total':20,'cases':seen,'execution_valid':not errors,'errors':errors,'source_hashes':{str(p.relative_to(r)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(r.rglob('*.kt')) if 'build' not in p.relative_to(r).parts},'before_runtime_score':None,'physical_audio_verified':False,'whole_android_compile_verified':False,'batch_accepted':False,'apk_generated':False}
(r/'results').mkdir(exist_ok=True);(r/'results/verification.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
raise SystemExit(0 if not errors else 1)
