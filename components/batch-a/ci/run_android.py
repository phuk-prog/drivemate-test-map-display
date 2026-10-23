#!/usr/bin/env python3
"""Public Robolectric simulation of actual Android sensor adapter; no application build."""
import argparse,hashlib,json,os,pathlib,re,shutil,subprocess,xml.etree.ElementTree as ET
p=argparse.ArgumentParser();p.add_argument('--gradle',default='gradle');a=p.parse_args()
r=pathlib.Path(__file__).resolve().parents[1];repo=r.parents[1];module=r/'android'
manifest=json.loads((r/'source-manifest.json').read_text())
for x in manifest['android_files']:assert hashlib.sha256((r/x['published_path']).read_bytes()).hexdigest()==x['sha256']
source=(module/'tests/GpsSensorAdapterTest.kt').read_text();expected=set(re.findall(r'@Test\s+fun\s+(\w+)\s*\(',source));assert len(expected)==6
results={};valid=True
for phase in ('baseline','candidate'):
 xmls=module/'build/test-results/testDebugUnitTest';shutil.rmtree(xmls,ignore_errors=True)
 log=r/'.private-logs'/('android-'+phase+'.log');log.parent.mkdir(exist_ok=True)
 with log.open('w') as out:run=subprocess.run([a.gradle,'-p',str(module),'testDebugUnitTest','-Pphase='+phase,'--no-daemon','--max-workers=3'],stdout=out,stderr=subprocess.STDOUT)
 observed={};errors=[]
 for file in xmls.glob('TEST-*.xml'):
  for t in ET.parse(file).getroot().findall('testcase'):
   name=t.get('name','')
   if name in observed:errors.append('duplicate runtime test')
   observed[name]='failed' if t.find('failure') is not None or t.find('error') is not None else 'skipped' if t.find('skipped') is not None else 'passed'
 if set(observed)!=expected:errors.append('missing/unexpected runtime cases')
 if 'skipped' in observed.values():errors.append('skipped cases')
 if run.returncode:errors.append('Gradle compile/execution failed')
 valid=valid and not errors
 results[phase]={'passed':sum(x=='passed' for x in observed.values()),'total':6,'execution_valid':not errors,'errors':errors,'case_outcomes':observed}
 print(json.dumps({'phase':phase,'sensor_adapter':results[phase]}),flush=True)
result={'schema':1,'batch':'A','category':'01','scope':'Public Robolectric Android sensor simulation; not physical navigation or full-app lifecycle','public_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=repo,text=True).strip(),'run_id':os.environ.get('GITHUB_RUN_ID'),'source_manifest_sha256':hashlib.sha256((r/'source-manifest.json').read_bytes()).hexdigest(),'test_sha256':hashlib.sha256(source.encode()).hexdigest(),'results':results,'execution_valid':bool(valid),'batch_accepted':False,'apk_generated':False}
(r/'results').mkdir(exist_ok=True);(r/'results/android-sensor-comparison.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
raise SystemExit(0 if valid and results['candidate']['passed']==6 else 1)
