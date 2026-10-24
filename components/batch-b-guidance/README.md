# Batch B guidance — bounded public component comparison

Baseline Wording.kt and DrivingDisplayPolicy.kt are exact source from private app `f29c40582394817eb7396eb220cbc287f638a479`; Wording also exactly matches already published Batch A common component. Dependencies are the existing public Batch A Models, Geo, LaneDirections, LanePlan and PreferLanes components at public base `74eea170feb5bfebdfe89e08d6c1cf39a6a4172c`.

The same 20 owner-preference/component tests execute both phases. Baseline failures are recorded, not disguised as product changes or missing tests. Candidate wording names usable edge lanes positively, retains explicit numeric counts, and combines straight-on lane instructions as “Use the right 3 lanes to continue straight”. General turn and roundabout wording is preserved. The 5 approach-policy tests exercise existing Now-cue timing only; they do not test or fix large-arrow visibility.

03.01 compact card rendering, 03.02 actual distance/arrow transitions, 03.03 whole-controller presentation and 16.01 Android Auto templates/host/map remain unverified. No legal lane markings or real traffic are inferred from synthetic lane inputs. No complete app, private test execution, credentials, personal journeys or APK appears here. Local execution is unavailable; exact-SHA public CI must establish outcomes.
