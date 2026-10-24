# Batch B — turn guidance, traffic reliability and road warnings

One public test suite covers **Category 03 (3 requirements), Category 08 (3), and Category 09 (13)** through shared public component tests. Batch B also includes Category 16 Android Auto; its runtime coverage is still missing.

- [Contract and independently sourced UK expectations](contracts/baseline.json)
- [Kotlin production-class diagnostic tests](tests/BatchBGuidanceTrafficWarningsTest.kt)
- [Public validation](ci/validate.py) and [per-item private-run summary](ci/summarize.py)
- Official references: [Highway Code roundabout/junction guidance](https://www.gov.uk/guidance/the-highway-code/using-the-road-159-to-203), [DfT road markings](https://www.gov.uk/government/publications/know-your-traffic-signs/road-markings), [DfT regulatory signs](https://www.gov.uk/government/publications/know-your-traffic-signs/regulatory-signs) and [Highway Code speed rules](https://www.gov.uk/guidance/the-highway-code/general-rules-techniques-and-advice-for-all-drivers-and-riders-103-to-158).

These are **16 initial logical checks**, not proof of full UI layout, on-phone alerts, real UK active traffic orders, current variable limits, Android Auto or a successful live TomTom API response. The required tests that do not exist are recorded **not tested**, never passed. Real UK roundabout signs and actual live warnings require further corroborated evidence, not simulated app predictions.

**Public/private separation (current instruction):** all tests execute in public testing repositories, including the relevant individual app components, fixtures and sanitized results. The combined app, keys, personal journeys and APK generation stay private. Historical private runs below are preserved as historical diagnostics and must not be represented as current public execution. No APK release before genuine required replay, robot and automated evidence passes.

**2026-10-24 work:** public baseline/candidate runs are being prepared on `codex/batch-b-guidance-public-20261024` and `codex/batch-b-warnings-public-20261024`. Source audit reference is private app main `f29c40582394817eb7396eb220cbc287f638a479`. Current public runtime score is unverified until exact CI results exist. Each of 20 checklist items retains its own acceptance outcome.

**Current scope:** Start a new branch from app `main` for a baseline; keep the existing Category 2 draft PR separate. Batch B is not accepted until every category's applicable UI, functional, safety and independent-source tests are completed.
