# DriveMate testing and evidence policy

User instruction recorded 2026-10-23. This supersedes earlier arrangements requiring private test execution or prohibiting publication of relevant component code.

## Repository responsibilities

- All test definitions, test execution, fixtures, before/after comparisons and sanitized results belong in the appropriate existing public testing repositories. Group related categories according to the register; do not create seventeen repositories.
- Relevant individual app components and their necessary test dependencies may be published and tested there. Keep them scoped to the issue under test; do not publish the combined app, secrets, signing material or personal journey/location data.
- The main private app repository assembles the combined application and generates the final APK. Do not add or run batch-testing workflows there. Move the existing private batch test arrangements to the public testing repositories; historical runs remain historical evidence, not the intended future architecture.
- Public component tests must execute the same component code intended for app integration. Record exact component and test revisions or hashes, dependencies and configuration. A replacement mock or copied implementation alone does not prove the real component works.
- Private APK generation must consume identified, publicly tested component revisions. Record integration/release gaps honestly: component tests alone do not establish combined-app behaviour.

## Expected behaviour must have a stated source

- Separate the user's approved app preferences, legal obligations, authoritative real-world data, technical specifications, and proposed implementation choices. A preference is not UK law; a recommendation is not an approved requirement.
- For legal assertions use current primary legislation, applicable regulations/traffic orders, official traffic-sign rules and official Highway Code provisions. Record source URL, relevant clause/rule, jurisdiction (including differences within the UK), applicability and date checked. Distinguish mandatory legal provisions from advisory guidance. Do not label every Highway Code statement a legal requirement.
- For real roads, restrictions, lane permissions, speed limits and incidents use applicable authoritative, current evidence. Record provider, location/road/direction, timestamp, licence and confidence. OSM geometry alone does not establish current legal lane access, turn permission or speed limit. Do not claim official feeds are live without checking freshness and coverage.
- For UI and timing preferences use the user's approved checklist answer. For engineering assumptions label them as proposals or unknowns until independently justified. Do not invent a legal sensor tolerance or camera zoom rule.
- If evidence conflicts or is missing, report unverified or disputed and explain what evidence would resolve it. Do not mark behaviour correct or defective merely because it matches or differs from our implementation or assumptions. Ask one specific clarification when the user's intended behaviour is genuinely ambiguous.

## Test quality and comparisons

- For each issue define the exact requirement, source, owning component, observable result, tolerance and justification before asserting pass/fail. Audit existing tests for unsupported or circular expectations, false positives, false negatives, missing boundaries and missing integration coverage.
- Include normal operation, boundary values, invalid/missing/stale inputs, failures and recovery, timing/order, uncertainty and relevant neighbouring behaviour. Use realistic attributed scenarios where suitable; label synthetic and simulated scenarios explicitly.
- Independently establish expected outcomes. Do not use app outputs as their own oracle. Comparing a road polyline with itself is a fixture/audit diagnostic, not proof of rendered road alignment. Use known-positive and known-negative controls and targeted mutation checks where they materially establish test sensitivity.
- Preserve the initial baseline. Run the same identified checks after changes, with separate reporting when the suite or data has changed. Keep per-category and per-checklist-item scores, failures, missing/skipped tests and unverified evidence; prevent duplicate or missing cases from silently passing.
- Show before and after scores in chat as well as publishing the evidence. A green workflow, logic-only test, simulated provider or downloaded geometry must not be described as full app/navigation acceptance.
- Continue the authorised batch through test gaps, fixes and final comparison; an intermediate baseline is not completion. Document genuine blockers and unresolved requirements explicitly.

## Current Batch A

Categories 01, 02, 04, 05; 22 checklist requirements. Historical initial baseline: GPS 10/19, maps 22/22, lanes 9/9, routing 5/5. These are diagnostic scores, not approval. The earlier private execution must be replaced by public component execution. Five requirements lack runtime cases and rendered/navigation integration evidence remains incomplete. Batch A is not complete.
