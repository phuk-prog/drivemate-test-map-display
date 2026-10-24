# Batch B 08.02 — traffic credential request authority

The actual individual TrafficRequestEpoch class is shared byte for byte with the private app candidate. It prevents an old incident request from publishing after a key change, preserves blocking-work ownership until finally returns, and yields one pending refresh after completion. The class stores no key, location, route or provider response.

14 candidate cases execute this class, including real coroutine cancellation with a non-cooperative completion, an A→B→A credential-change sequence, old-finally protection, invalidation without an active request and concurrent acquisition. The coroutine tests use synthetic surrounding orchestration and no HTTP. They establish the component protocol, not the private NavigationSession wiring or UI.

Before: no comparable executed original epoch suite; no numeric baseline is invented. After: exact-SHA public CI must establish the candidate result. Existing Batch B traffic diagnostics and historical private execution are separate.

Private source reference before the proposed fix: 9f01c0a1f7154861941eee57fb52a471917b7ce5. NavigationSession previously checked only a session ticket after incident HTTP; saving/clearing a traffic key did not invalidate that ticket. New wiring invalidates authority before changing credentials, cancels best effort, rejects late health/cache publication and allows a latest-key retry only after blocking work finishes and the session is still current. Fresh independently tagged TfL items are retained on key changes. PrivacyNotice describes existing automatic bounding-box requests after saving a key.

Local execution/preflight: UNAVAILABLE (execution environment unavailable). Public CI executes only this individual component. No private tests, complete app, credential, personal journey or APK is published. Live ETA is still unsupported, official/community conflict reconciliation remains open, and no full 08.02/Batch B acceptance or legal compliance claim follows.
