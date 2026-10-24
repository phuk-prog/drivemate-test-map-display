# Actual formatter chain — expanded paired comparison

Public CI [40071840169](https://github.com/phuk-prog/drivemate-test-map-display/actions/runs/40071840169) succeeded at `53510775c6e88d973eae3c136692cf731661b609`: **baseline 15/26 → candidate 26/26**, no missing/skipped cases. This expands the earlier 20-case suite; do not compare 13/20 directly to26/26. Same26 cases run forbothphases.

21wording cases: **10/21→21/21**. Unchanged5Nowpolicycases: **5/5→5/5**. Sixaddedcases call the actual TurnInstructions.describe→RouteStep→Wording.withLane chain: fourpositive explicitstraight/through inputs and two negatives forunknown/bendingdirection. Currentcontinue formatter produces “Continue onto A6”; the revised candidate uses explicit route-direction evidence to say “Use the right 3 lanes to continue straight onto A6”. It never infers straight from generic Continue alone.

CandidateWording SHA256 `c2816efefcb46f88eb29b3a07d8b38bca51f2cdbb170d907e0bdf8a20a0bc326`. Exactsourcehashes/caseoutcomes areincomparison.json. The originalmoduleREADMEand20-case evidence describe the first comparison; this26-case chain report supersedes that denominator. No renderedcard, actualarrowvisibility, completeprivatecontroller, AndroidAuto, liveprovider orlegality proof. BatchB remainsunaccepted.

Localpreupload syntax/tests unavailable; connector source/callerreviewcompleted. ExactpublicCI isseparatepostuploadproof. No private tests,APK orcombinedapp publication.
