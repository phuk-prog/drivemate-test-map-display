#!/usr/bin/env python3
"""Execute actual selected components in this PUBLIC repo. Never checkout the private app."""
import argparse,hashlib,json,pathlib,re,shutil,subprocess,xml.etree.ElementTree as ET
p=argparse.ArgumentParser();p.add_argument('--gradle',default='gradle');p.add_argument('--fetch-roads',action='store_true');a=p.parse_args()
root=pathlib.Path(__file__).resolve().parents[1];repo=root.parents[1];suite=repo/'batches/A-roundabouts-maps-positioning'
manifest=json.loads((root/'source-manifest.json').read_text());records=manifest['files']
assert {str(x.relative_to(root)) for phase in ('common','baseline','candidate') for x in (root/phase).rglob('*.kt')}=={x['published_path'] for x in records},'Unmanifested source component'
for x in records:assert hashlib.sha256((root/x['published_path']).read_bytes()).hexdigest()==x['compiled_sha256'],x['published_path']
expected=json.loads((suite/'contracts/expected.json').read_text())['cases'];frozen={x['id']:x['category'] for x in expected}
extra={}
for file in (root/'tests-expanded').glob('*.kt'):
 text=file.read_text();package=re.search(r'^package ([\w.]+)',text,re.M)[1];cls=re.search(r'class (\w+)',text)[1]
 for method in re.findall(r'@Test\s+fun\s+(\w+)\s*\(',text):extra[package+'.'+cls+'#'+method]=re.search(r'pref(\d\d)_',method)[1]
assert len(frozen)==55 and len(extra)==13 and not set(frozen)&set(extra)
all_expected={**frozen,**extra};fixture=suite/'fixtures/independent-osm-roads.json'
fetch='not_attempted'
if a.fetch_roads:
 fixture.unlink(missing_ok=True)
 fetch='success' if subprocess.run(['python3',str(repo/'ci/fetch_independent_uk_roads.py'),str(fixture)],stdout=subprocess.PIPE,stderr=subprocess.PIPE).returncode==0 else 'source_unavailable'
logs=root/'.private-logs';logs.mkdir(exist_ok=True);results={};valid=True
for phase in ('baseline','candidate'):
 xmls=root/'harness/build/test-results/test'
 shutil.rmtree(xmls,ignore_errors=True)
 with (logs/(phase+'.log')).open('w') as log:
  run=subprocess.run([a.gradle,'-p',str(root/'harness'),'test','-Pphase='+phase,'--no-daemon','--max-workers=3'],stdout=log,stderr=subprocess.STDOUT)
 observed={};errors=[]
 for file in xmls.glob('TEST-*.xml'):
  for case in ET.parse(file).getroot().findall('testcase'):
   key=case.get('classname','')+'#'+case.get('name','')
   if key in observed:errors.append('duplicate case')
   observed[key]='failed' if case.find('failure') is not None or case.find('error') is not None else 'skipped' if case.find('skipped') is not None else 'passed'
 if set(observed)!=set(all_expected):errors.append('missing or unexpected runtime cases')
 if 'skipped' in observed.values():errors.append('skipped cases')
 if run.returncode:errors.append('Gradle compilation/execution failure')
 valid=valid and not errors
 def scores(cases):
  return {cat:{'passed':sum(observed.get(k)=='passed' for k,v in cases.items() if v==cat),'total':sum(v==cat for v in cases.values())} for cat in ('01','02','04','05')}
 results[phase]={'execution_valid':not errors,'errors':errors,'frozen_55_scores':scores(frozen),'expanded_68_scores':scores(all_expected),'case_outcomes':observed}
 print(json.dumps({'phase':phase,'execution_valid':not errors,'frozen_55_scores':scores(frozen),'expanded_68_scores':scores(all_expected)}),flush=True)
suite_hash=hashlib.sha256(b''.join(f.name.encode()+f.read_bytes() for path in (suite/'tests',root/'tests-expanded') for f in sorted(path.glob('*.kt')))).hexdigest()
result={'schema':1,'batch':'A','scope':'PUBLIC component execution; selected original code; no combined app','public_tests_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=repo,text=True).strip(),'run_id':__import__('os').environ.get('GITHUB_RUN_ID'),'source_manifest_sha256':hashlib.sha256((root/'source-manifest.json').read_bytes()).hexdigest(),'test_fingerprint':suite_hash,'external_geometry':{'available':fixture.exists(),'fetch':fetch,'sha256':hashlib.sha256(fixture.read_bytes()).hexdigest() if fixture.exists() else None,'legal_turn_truth':'unverified'},'results':results,'all_executions_valid':bool(valid),'batch_accepted':False,'apk_generated':False,'remaining_gaps':['05.01 avoided-road alternative UI/selection integration','05.02 draggable route panel interaction','actual rendered map/camera and lane diagram UI integration','current road signs/traffic orders and legal lane permissions','navigation replay, Android lifecycle/permission and sensor adapter integration']}
(root/'results').mkdir(exist_ok=True);(root/'results/public-comparison.json').write_text(json.dumps(result,indent=2)+'\n')
# A green execution must have every candidate diagnostic passing and real road data present.
raise SystemExit(0 if valid and fixture.exists() and all(v=='passed' for v in results['candidate']['case_outcomes'].values()) else 1)
