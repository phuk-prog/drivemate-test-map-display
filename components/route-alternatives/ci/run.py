#!/usr/bin/env python3
import argparse,hashlib,json,os,pathlib,re,shutil,subprocess,xml.etree.ElementTree as E
p=argparse.ArgumentParser();p.add_argument('--gradle',default='gradle');a=p.parse_args();r=pathlib.Path(__file__).resolve().parents[1]
expected=set()
for f in (r/'src/test').rglob('*.kt'):
 s=f.read_text();c=re.search(r'class (\w+)',s)[1];expected.update(c+'#'+n for n in re.findall(r'@Test\s+fun\s+(\w+)\s*\(',s))
assert len(expected)==31
xmls=r/'build/test-results/test';shutil.rmtree(xmls,ignore_errors=True)
run=subprocess.run([a.gradle,'-p',str(r),'test','--no-daemon','--max-workers=3'])
seen={};errors=[]
for f in xmls.glob('TEST-*.xml'):
 for t in E.parse(f).getroot().findall('testcase'):
  k=t.get('classname').split('.')[-1]+'#'+t.get('name')
  if k in seen:errors.append('duplicate case')
  seen[k]='failed' if t.find('failure') is not None or t.find('error') is not None else 'skipped' if t.find('skipped') is not None else 'passed'
if set(seen)!=expected:errors.append('missing/unexpected cases')
if 'skipped' in seen.values():errors.append('skipped cases')
if run.returncode:errors.append('Gradle failure')
result={'requirement':'05.01','run_id':os.environ.get('GITHUB_RUN_ID'),'public_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=r,text=True).strip(),'source_hashes':{str(f.relative_to(r)):hashlib.sha256(f.read_bytes()).hexdigest() for f in (r/'src/main').rglob('*.kt')},'scope':'Actual preference-selection and request-generation components on supplied synthetic evidence; no live provider or full-app UI claims','before':{'runtime_score':None,'status':'No comparable executed original fallback suite; prior app requested only compliant routes'},'after':{'passed':sum(v=='passed' for v in seen.values()),'total':31,'case_outcomes':seen},'execution_valid':not errors,'errors':errors,'batch_accepted':False,'apk_generated':False}
(r/'results').mkdir(exist_ok=True);(r/'results/verification.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
raise SystemExit(0 if not errors and result['after']['passed']==31 else 1)
