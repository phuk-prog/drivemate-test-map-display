#!/usr/bin/env python3
import json,pathlib,re,subprocess,sys,xml.etree.ElementTree as ET,hashlib,os,shutil
root=pathlib.Path(__file__).resolve().parents[1]
test=root/'src/test/kotlin/com/drivemate/app/map/MapOverviewPolicyTest.kt'
src=root/'src/main/kotlin/com/drivemate/app/map/MapOverviewPolicy.kt'
names=re.findall(r'@Test\s+fun\s+(\w+)\(',test.read_text())
assert len(names)==len(set(names))==12
out=root/'build/test-results/test'
shutil.rmtree(out,ignore_errors=True)
proc=subprocess.run(['gradle','-p',str(root),'test','--no-daemon','--max-workers=2'])
cases={}
for f in out.glob('TEST-*.xml'):
 for tc in ET.parse(f).getroot().iter('testcase'):
  name=tc.get('name')
  if name not in names:continue
  assert name not in cases, 'duplicate '+name
  cases[name]='skipped' if tc.find('skipped') is not None else 'failed' if tc.find('failure') is not None or tc.find('error') is not None else 'passed'
errors=[]
if set(cases)!=set(names):errors.append('missing or unexpected cases')
if proc.returncode:errors.append('Gradle returned nonzero')
if any(q!='passed' for q in cases.values()):errors.append('nonpassing case')
result={'scope':'public exact Kotlin overview-policy component only, NOT MapLibre real-app rendering',
'app_candidate_sha':'00b540ffae77d1248049c0373c2bc87c1860ba48',
'app_policy_git_blob':'f1f1561e3c7f20db294fa9280c67c04a0e1d8768',
'public_component_sha256':hashlib.sha256(src.read_bytes()).hexdigest(),
'public_test_sha256':hashlib.sha256(test.read_bytes()).hexdigest(),
'workflow_run_id':os.environ.get('GITHUB_RUN_ID'),
'before':{'arrow_drawable_dp':44,'max_zoom_scale':1.7,'min_zoom_scale':1.1,'hazards_allow_overlap':True,
'hazards_ignore_placement':True,'viewport_coverage_warning':False,
'logical_tests_run':0,'note':'source-inspected original app baseline, not simulated test pass/fail'},
'after':{'arrow_drawable_dp':32,'max_zoom_scale':1.25,'min_zoom_scale':0.8,
'hazards_allow_overlap':False,'hazards_ignore_placement':False,
'viewport_coverage_warning_proposed':True,'passed':sum(x=='passed' for x in cases.values()),
'expected':len(names),'case_outcomes':cases},
'errors':errors,'component_execution_valid':not errors,'combined_app_verified':False,'gpu_rendering_verified':False,
'uk_data_coverage_verified':False,'apk_built':False,'category_02_accepted':False}
f=root/'results/overview-policy.json';f.parent.mkdir(exist_ok=True);f.write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({'passed':result['after']['passed'],'expected':len(names),'errors':errors,'scope':result['scope']}))
sys.exit(0 if not errors else 1)
