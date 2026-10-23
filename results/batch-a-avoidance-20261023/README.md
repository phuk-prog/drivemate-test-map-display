# Checklist 05.01 — public route-choice evidence

43/43 cases passed in public CI [40024858622](https://github.com/phuk-prog/drivemate-test-map-display/actions/runs/40024858622), source commit `ec509142dd321b10fb1651a8775a3dab960d8174`. No missing or skipped cases. Exact source hashes and individual outcomes are in verification.json.

Approved fallback: warned preference alternatives, no automatic selection, explicit choice before starting. Missing metadata is not compliance evidence. Known closures are excluded; unknown/legal/vehicle restrictions cannot be waived as preferences. Unknown retained preference compliance remains labelled. Faster comparisons require fresh comparable timing.

Tests execute real individual policy, metadata, geometry-key and request-ticket code on synthetic supplied evidence. They do not execute a live provider, private controller/UI or complete app. The private candidate additionally stages stale-preview invalidation and no-route fallback wiring; those integrations are reviewed but not runtime-verified here. DMN request capabilities do not establish actual road legal access.

Before: no comparable executed original-app fallback suite. After: 43/43 candidate cases. Original routing-helper 5/5 → 5/5 is a separate suite. Batch A remains unaccepted; no APK generated.
