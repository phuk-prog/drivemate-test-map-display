#!/usr/bin/env python3
import argparse,hashlib,json,os,pathlib,re,shutil,subprocess,xml.etree.ElementTree as ET
p=argparse.ArgumentParser();p.add_argument('--gradle',default='gradle');a=p.parse_args();r=pathlib.Path(__file__).resolve().parents[1]
src=r/'src/main/kotlin/com/drivemate/app/ui/DraggableRoutePanel.kt';test=r/'src/test/kotlin/com/drivemate/app/ui/RoutePanelTest.kt'
expected=set(re.findall(r'@Test\s+fun\s+(\w+)\s*\(',test.read_text()));assert len(expected)==5
xmls=r/'build/test-results/testDebugUnitTest';shutil.rmtree(xmls,ignore_errors=True)
run=subprocess.run([a.gradle,'-p',str(r),'testDebugUnitTest','--no-daemon','--max-workers=3'])
observed={};errors=[]
for f in xmls.glob('TEST-*.xml'):
 for t in ET.parse(f).getroot().findall('testcase'):
  k=t.get('name','')
  if k in observed:errors.append('duplicate runtime case')
  observed[k]='failed' if t.find('failure') is not None or t.find('error') is not None else 'skipped' if t.find('skipped') is not None else 'passed'
if set(observed)!=expected:errors.append('missing/unexpected runtime case')
if 'skipped' in observed.values():errors.append('skipped runtime case')
if run.returncode:errors.append('Gradle execution failed')
result={'requirement':'05.02','scope':'Real Compose component interactions in Robolectric native graphics; not combined-app/map integration','run_id':os.environ.get('GITHUB_RUN_ID'),'public_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=r,text=True).strip(),'source_sha256':hashlib.sha256(src.read_bytes()).hexdigest(),'test_sha256':hashlib.sha256(test.read_bytes()).hexdigest(),'before':{'runtime_score':None,'status':'Earlier app preview source has fixed Surface; no comparable gesture baseline was executed'},'after':{'passed':sum(x=='passed' for x in observed.values()),'total':5,'case_outcomes':observed},'execution_valid':not errors,'execution_errors':errors,'app_integration_verified':False,'apk_generated':False}
(r/'results').mkdir(exist_ok=True);(r/'results/route-panel.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
raise SystemExit(0 if not errors and result['after']['passed']==5 else 1)
