#!/usr/bin/env python3
"""One private execution; public case outcomes, distinct category scores, no release approval."""
import argparse,hashlib,json,pathlib,re,shutil,subprocess,tempfile,xml.etree.ElementTree as ET
p=argparse.ArgumentParser();p.add_argument('--app',type=pathlib.Path,required=True);p.add_argument('--app-sha',required=True);p.add_argument('--public-sha',required=True);p.add_argument('--fetch-roads',action='store_true');a=p.parse_args()
root=pathlib.Path(__file__).resolve().parents[1];repo=root.parents[1];app=a.app.resolve()
manifest=json.loads((root/'contracts/expected.json').read_text());checklist=json.loads((root/'contracts/checklist.json').read_text())
expected={c['id']:c for c in manifest['cases']};fixture=root/'fixtures/independent-osm-roads.json'
road_fetch='not_attempted'
if a.fetch_roads:
    road_fetch='success' if subprocess.run(['python3',str(repo/'ci/fetch_independent_uk_roads.py'),str(fixture)],stdout=subprocess.PIPE,stderr=subprocess.PIPE).returncode==0 else 'source_unavailable'
with tempfile.TemporaryDirectory(prefix='batch-a-') as td:
    src=pathlib.Path(td)/'kotlin';src.mkdir()
    for f in (root/'tests').glob('*.kt'):shutil.copyfile(f,src/f.name)
    init=pathlib.Path(td)/'batch.gradle'
    init.write_text('allprojects { plugins.withId("com.android.application") { android.sourceSets.test.java.srcDir('+json.dumps(str(src))+'); android.sourceSets.test.resources.srcDir('+json.dumps(str(root/'fixtures'))+') } }\n')
    xmls=app/'app/build/test-results/testDebugUnitTest'
    for old in xmls.glob('TEST-*.xml'):old.unlink()
    lint=app/'app/build/reports/lint-results-debug.xml'
    if lint.exists():lint.unlink()
    log=root/'.private-logs/gradle.log';log.parent.mkdir(parents=True,exist_ok=True)
    args=[str(app/'gradlew'),'-I',str(init),'testDebugUnitTest']
    for cls in sorted({c['id'].split('#')[0] for c in expected.values()}):args+=['--tests',cls]
    args+=['lintDebug','--continue','--no-daemon','--max-workers=3']
    with log.open('w') as out:process=subprocess.run(args,cwd=app,stdout=out,stderr=subprocess.STDOUT)
observed={};errors=[]
classes={x.split('#')[0] for x in expected}
for f in sorted(xmls.glob('TEST-*.xml')):
    try:
        for c in ET.parse(f).getroot().findall('testcase'):
            cls=c.get('classname','')
            if cls not in classes:continue
            key=cls+'#'+c.get('name','');status='passed'
            for tag in ('failure','error','skipped'):
                if c.find(tag) is not None:status={'failure':'failed','error':'error','skipped':'skipped'}[tag]
            if key in observed:errors.append('duplicate case identity')
            observed[key]=status
    except ET.ParseError:errors.append('invalid JUnit XML')
missing=sorted(set(expected)-set(observed));unexpected=sorted(set(observed)-set(expected))
if missing:errors.append('missing runtime cases')
if unexpected:errors.append('unexpected runtime cases')
if any(v=='skipped' for v in observed.values()):errors.append('skipped runtime cases')
failed=sum(v in ('failed','error') for v in observed.values())
if process.returncode and not failed:errors.append('Gradle failure not explained by diagnostic failures')
lint=app/'app/build/reports/lint-results-debug.xml';lint_ok=False
if lint.exists():
    lint_ok=not any(i.get('severity') in ('Error','Fatal') for i in ET.parse(lint).getroot().findall('issue'))
if not lint_ok:errors.append('lint missing or failed')
categories={}
for category in manifest['categories']:
    ids=[k for k,c in expected.items() if c['category']==category]
    categories[category]={'passed':sum(observed.get(k)=='passed' for k in ids),'total':len(ids),'failed':sum(observed.get(k) in ('failed','error') for k in ids),'missing':sum(k not in observed for k in ids),'accepted':False}
items=[]
for item in checklist['requirements']:
    cases=[k for k,c in expected.items() if c.get('item')==item['id']]
    if item['category']=='01':
        terms={'01.01':('Uncertainty','uncertainLabel','goodPositionClears'),'01.02':('Lanes','lanes'),'01.03':(),'01.04':('Heading','heading','Gyro','gyro','Gravity','stationaryGps')}
        cases=[k for k,c in expected.items() if c['category']=='01' and any(t in k.split('#')[1] for t in terms[item['id']])]
    items.append({**item,'runtime_cases':cases,'passed':sum(observed.get(k)=='passed' for k in cases),'total':len(cases),'diagnostic_status':'not_tested' if not cases else 'failures_or_missing' if any(observed.get(k)!='passed' for k in cases) else 'logic_diagnostics_passed','accepted':False})
source=(app/'app/src/main/java/com/drivemate/app/map/MapController.kt').read_text()
traffic_block=source.split('private fun applyTraffic(s: Style)',1)[-1].split('\n    /**',1)[0]
traffic='wired' if 'source.setGeoJson(' in traffic_block and 'trafficEnabled' in traffic_block else 'not_wired_or_unverified'
fixture_evidence={'status':'available' if fixture.exists() else 'unavailable','fetch':road_fetch,'legal_turn_permissions':'unknown'}
if fixture.exists():fixture_evidence.update({'sha256':hashlib.sha256(fixture.read_bytes()).hexdigest(),'source':json.loads(fixture.read_text()).get('retrieved_from'),'source_kind':'external OSM geometry; not legal sign corroboration'})
result={'batch':'A','stage':'baseline','app_sha':a.app_sha,'public_tests_sha':a.public_sha,'tested_local_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=app,text=True).strip(),'app_tree':subprocess.check_output(['git','rev-parse','HEAD:app'],cwd=app,text=True).strip(),'test_fingerprint':hashlib.sha256(b''.join(f.name.encode()+b'\0'+f.read_bytes() for f in sorted((root/'tests').glob('*.kt')))).hexdigest(),'baseline_execution_valid':not errors,'execution_errors':errors,'gradle_exit':process.returncode,'lint_passed':lint_ok,'categories':categories,'items':items,'case_outcomes':observed,'missing':missing,'unexpected':unexpected,'external_road_geometry':fixture_evidence,'static_findings':{'traffic_route_integration':traffic},'mandatory_release_gaps':['rendered carriageway and marker verification','navigation replay/robot','independent current sign and turn-permission corroboration'],'batch_accepted':False,'apk_built':False,'apk_released':False,'scope':'logic diagnostics and external-geometry audit on current main; not a combined driving acceptance test'}
(root/'results').mkdir(exist_ok=True);(root/'results/baseline.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({k:result[k] for k in ('batch','app_sha','baseline_execution_valid','execution_errors','categories','external_road_geometry','batch_accepted')}))
raise SystemExit(0 if not errors else 1)
