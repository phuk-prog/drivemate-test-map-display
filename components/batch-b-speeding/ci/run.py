import hashlib,json,os,pathlib,shutil,subprocess,xml.etree.ElementTree as E
r=pathlib.Path(__file__).resolve().parents[1]
expected={'immediateAtFirstMeasuredExcess','tinyExcessNotHiddenByRoundedDisplay','equalLimitIsSilent','belowLimitIsSilent','noEarlyRepeatAt59999ms','repeatAt60000ms','repeatsEveryFurtherMinute','recoveryStartsNewEpisodeImmediately','missingSpeedNeverWarns','missingLimitNeverWarns','estimatedSpeedNeverWarns','mutedNeverEmitsAudio','alertsOnlyUsesTone','unmuteDoesNotWaitForMutedWarningTimer','sessionClockZeroCanWarn','silenceAfterDroppingToLimit'}
results={}; errors=[]
for variant in ['baseline','candidate']:
    shutil.rmtree(r/'build/test-results/test',ignore_errors=True)
    run=subprocess.run(['gradle','-p',str(r),'test','--no-daemon','--max-workers=3','--rerun-tasks','-PwarningVariant='+variant])
    outcomes={}
    for f in (r/'build/test-results/test').glob('TEST-*.xml'):
        for t in E.parse(f).getroot().findall('testcase'):
            name=t.get('name')
            if name in outcomes: errors.append(variant+': duplicate')
            outcomes[name]='failed' if t.find('failure') is not None or t.find('error') is not None else 'skipped' if t.find('skipped') is not None else 'passed'
    if set(outcomes)!=expected:errors.append(variant+': missing/unexpected cases')
    if 'skipped' in outcomes.values():errors.append(variant+': skipped')
    results[variant]={'passed':sum(v=='passed' for v in outcomes.values()),'total':16,'cases':outcomes,'gradle_exit':run.returncode}
    if variant=='candidate' and (run.returncode or any(v!='passed' for v in outcomes.values())):errors.append('candidate failed')
if results['baseline']['cases'].get('immediateAtFirstMeasuredExcess')!='failed' or results['baseline']['cases'].get('repeatAt60000ms')!='failed':errors.append('baseline mismatch not reproduced')
report={'batch':'B','requirements':['09.03','09.04'],'app_reference':'f29c40582394817eb7396eb220cbc287f638a479','public_sha':os.environ.get('GITHUB_SHA'),'run_id':os.environ.get('GITHUB_RUN_ID'),'scope':'Same16cases on actual extracted legacy controller semantics vs candidate pure policy; no private wiring/Android TTS/live-road/legal proof','source_hashes':{str(p.relative_to(r)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(r.rglob('*.kt')) if 'build' not in p.relative_to(r).parts},'before':results['baseline'],'after':results['candidate'],'execution_valid':not errors,'errors':errors,'batch_accepted':False,'private_wired':False,'apk_generated':False}
(r/'results').mkdir(exist_ok=True);(r/'results/verification.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
raise SystemExit(0 if not errors else 1)
