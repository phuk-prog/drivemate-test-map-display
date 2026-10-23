# Avoided-road choices — checklist 05.01

Approved user preference: show warned alternatives when no route honours avoidance settings, with nothing selected automatically. With a compliant route, keep it the default; only identify an alternative as faster when current comparable timing supports that assertion. An advisory requires explicit route selection before starting.

Published source is the actual selection, road-metadata interpretation, geometry-identity and request-generation code used by the staged private integration. It is not a routing engine or combined application. Motorway/toll/ferry/charge preferences may be relaxed; unknown/legal/vehicle restrictions remain in the request. Known closed routes are excluded. Lack of a closure match does not prove legal access. A charge-zone crossing does not prove the vehicle owes a fee. Missing metadata never becomes an invented road warning.

31 tests cover supplied synthetic evidence, explicit consent, defaults, freshness/source comparison, invalid geometry/durations, duplicate routes, joint preference relaxation and stale/cancelled request results. Five-minute freshness and two-minute timing skew are existing engineering settings, not law. Route metadata and legal permissions still require real-provider/current-road verification. No app UI or live API is executed here, and no APK is generated.

No numeric original-app baseline exists for this new suite. Report the original coverage as absent, not 0/31. Preserve the original Batch A routing-helper score separately (5/5).
