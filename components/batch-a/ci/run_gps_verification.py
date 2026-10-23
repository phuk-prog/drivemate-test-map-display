#!/usr/bin/env python3
import argparse,hashlib,json,os,pathlib,re,shutil,subprocess,xml.etree.ElementTree as ET
p=argparse.ArgumentParser();p.add_argument('--gradle',default='gradle');a=p.parse_args();r=pathlib.Path(__file__).resolve().parents[1];repo=r.parents[1]
expected=set()
for f in (r/'gps-verification').glob('*.kt'):
 s=f.read_text();cls=re.search(r'class (\w+)',s)[1]
 expected.update('com.drivemate.app.nav.'+cls+'#'+n for n in re.findall(r'@Test\s+fun\s+(\w+)\s*\(',s))
assert len(expected)==32
xmls=r/'harness/build/test-results/test';shutil.rmtree(xmls,ignore_errors=True)
run=subprocess.run([a.gradle,'-p',str(r/'harness'),'test','-Pphase=candidate','-Pverification=true','--no-daemon','--max-workers=3'])
observed={};errors=[]
for f in xmls.glob('TEST-*.xml'):
 for t in ET.parse(f).getroot().findall('testcase'):
  k=t.get('classname','')+'#'+t.get('name','')
  if k in observed:errors.append('duplicate runtime case')
  observed[k]='failed' if t.find('failure') is not None or t.find('error') is not None else 'skipped' if t.find('skipped') is not None else 'passed'
if set(observed)!=expected:errors.append('missing/unexpected runtime cases')
if 'skipped' in observed.values():errors.append('skipped cases')
if run.returncode:errors.append('Gradle execution failed')
result={'scope':'Additional 32 candidate GPS verification cases; separate from frozen before-after suite','public_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=repo,text=True).strip(),'run_id':os.environ.get('GITHUB_RUN_ID'),'source_manifest_sha256':hashlib.sha256((r/'source-manifest.json').read_bytes()).hexdigest(),'passed':sum(v=='passed' for v in observed.values()),'total':32,'case_outcomes':observed,'execution_valid':not errors,'execution_errors':errors,'baseline_for_this_suite':None,'batch_accepted':False,'apk_generated':False}
(r/'results').mkdir(exist_ok=True);(r/'results/gps-extra-verification.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
raise SystemExit(0 if not errors and result['passed']==32 else 1)
