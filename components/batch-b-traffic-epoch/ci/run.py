import hashlib,json,os,pathlib,subprocess,shutil,xml.etree.ElementTree as E
root=pathlib.Path(__file__).resolve().parents[1]
expected=set(["freshTicketMayPublish","finishRevokesPublication","invalidationRevokesPublicationImmediately","invalidationWithoutActiveRequestDoesNotBlockNextRequest","newRequestWaitsUntilOldBlockingWorkFinishes","keyAToBToADoesNotRestoreAnOldTicket","repeatedInvalidationsYieldOnePendingRefresh","foreignTicketCannotReleaseAReservation","oldFinallyCannotUnlockANewerRequest","completedOldTicketCannotConsumeANewerRetry","ordinaryFinishDoesNotScheduleAnotherFetch","cancellationKeepsReservationUntilBlockingWorkActuallyReturns","nonCooperativeLateResultLosesAuthorityWithoutCancellation","concurrentAcquisitionHasExactlyOneOwner"])
xml=root/'build/test-results/test'
shutil.rmtree(xml,ignore_errors=True)
run=subprocess.run(['gradle','-p',str(root),'test','--no-daemon','--max-workers=3','--rerun-tasks'])
cases={};errors=[]
for f in xml.glob('TEST-*.xml'):
    for case in E.parse(f).getroot().findall('testcase'):
        name=case.get('name')
        if name in cases:errors.append('duplicate case')
        cases[name]='failed' if case.find('failure') is not None or case.find('error') is not None else 'skipped' if case.find('skipped') is not None else 'passed'
if set(cases)!=expected:errors.append('missing/unexpected cases')
if run.returncode:errors.append('Gradle failure')
if any(v!='passed' for v in cases.values()):errors.append('failed/skipped cases')
report={'requirement':'08.02','run_id':os.environ.get('GITHUB_RUN_ID'),'public_sha':os.environ.get('GITHUB_SHA'),'scope':'Actual credential request epoch component; synthetic scheduling/cancellation scenarios, not private NavigationSession or HTTP execution','before':{'score':None,'reason':'No comparable executed original request-generation component suite'},'after':{'passed':sum(v=='passed' for v in cases.values()),'total':14,'cases':cases},'source_hashes':{str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in root.rglob('*.kt') if 'build' not in p.parts},'execution_valid':not errors,'errors':errors,'private_controller_runtime_verified':False,'live_api_verified':False,'batch_accepted':False,'apk_generated':False}
(root/'results').mkdir(exist_ok=True)
(root/'results/verification.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report))
raise SystemExit(1 if errors else 0)
