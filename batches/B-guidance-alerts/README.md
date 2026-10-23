# Batch B — turn guidance, traffic reliability and road warnings

One public test suite covers **Category 03 (3 requirements), Category 08 (3), and Category 09 (13)** in a single private-app test run and common Android build, rather than one APK per requirement.

- [Contract and independently sourced UK expectations](contracts/baseline.json)
- [Kotlin production-class diagnostic tests](tests/BatchBGuidanceTrafficWarningsTest.kt)
- [Public validation](ci/validate.py) and [per-item private-run summary](ci/summarize.py)
- Official references: [Highway Code roundabout/junction guidance](https://www.gov.uk/guidance/the-highway-code/using-the-road-159-to-203), [DfT road markings](https://www.gov.uk/government/publications/know-your-traffic-signs/road-markings), [DfT regulatory signs](https://www.gov.uk/government/publications/know-your-traffic-signs/regulatory-signs) and [Highway Code speed rules](https://www.gov.uk/guidance/the-highway-code/general-rules-techniques-and-advice-for-all-drivers-and-riders-103-to-158).

These are **16 initial logical checks**, not proof of full UI layout, on-phone alerts, real UK active traffic orders, current variable limits, Android Auto or a successful live TomTom API response. The required tests that do not exist are recorded **not tested**, never passed. Real UK roundabout signs and actual live warnings require further corroborated evidence, not simulated app predictions.

**Public/private separation:** all source-backed test definitions and shareable reference inputs are public; the real proprietary app code runs these tests only inside the private app's GitHub Actions checkout, and its SHA-tagged minimal results can be publicly reported. No private source, API key, APK or personal journey data are published. No APK release before all required replay, robot and automated gates pass.

**Current scope:** Start a new branch from app `main` for a baseline; keep the existing Category 2 draft PR separate. Batch B is not accepted until every category's applicable UI, functional, safety and independent-source tests are completed.
