# Batch C component evidence
Baseline source is pinned to private app baf6c06de2c3027e2ea7563e9f9e4dacafbbd147. Only individual isolated source files are copied here, with synthetic model/provider boundaries. Geo, AlongRoute and ArrivalApproachInstruction are unchanged. ArrivalPolicy and StopDetour are tested before and after with identical assertions.
No app checkout, Android credentials, private route, device trace or APK is present.
Run ci/run.py with Java 17 and Gradle 8.14.3. A compile/setup failure is not a behavioral pass. Local execution and canonical preflight are UNAVAILABLE in this connector-only session.
Synthetic road geometry, injected place listings and injected road-duration responses do not establish vehicle-accessible entrances, live provider availability, UI interactions, wake phrases or legal routing. Passing component cases cannot accept full checklist items.

StopCompletionPolicy baseline is an explicitly labelled test adapter for the exact baseline count expression in NavigationSession. It cannot test the baseline callsite's predicted-position input; measured-input callsite correctness requires private integration review. Candidate StopCompletionPolicy is the actual new production source.
