#!/usr/bin/env python3
"""Public, secret-free checks for DriveMate grouped acceptance-test governance.

This validates externally visible test contracts, not the private Android app.
CI against the actual app remains a separately identified private GitHub Actions run.
"""
from __future__ import annotations

import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
POLICY = ROOT / "contracts/grouped-test-policy.json"
CATEGORY2 = ROOT / "contracts/category2.json"
UK = ROOT / "contracts/uk-independent-evidence.json"


def require(condition, reason):
    if not condition:
        raise AssertionError(reason)


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


def main():
    policy = load(POLICY)
    require(policy["schema_version"] == 1, "Unsupported grouped policy schema")
    require(policy["public_contracts_required"] is True, "Tests must be openly reviewable")
    require(policy["public_expected_results_required"] is True, "Expected results must be public")
    require(policy["private_app_source_allowed_in_public"] is False, "Never expose app source")
    require(policy["public_secrets_allowed"] is False, "Never publish keys")
    batch = policy["batching"]
    require(batch["preferred_category_count_min"] == 2, "Normally group at least two related categories")
    require(batch["preferred_category_count_max"] == 4, "Never make oversized batches")
    require(batch["one_shared_candidate_build_and_test_apk_per_batch"], "One test build per related group")
    require(batch["per_category_pretest_after_retest_required"], "Each category keeps pre- and post-test")
    require(batch["separate_category_release_gates"], "Group success does not waive category gates")
    require(batch["allow_related_cross_category_regression_without_starting_unapproved_category_development"],
            "Cross-category regressions must be allowed")
    require(policy["apk_policy"]["public_release"].startswith("No APK"), "APK safety gate must be explicit")
    require("TomTom" in policy["apk_policy"]["no_tomtom_key"], "Protect provider key")

    ids = []
    for group in policy["batch_templates"]:
        cats = group["categories"]
        require(2 <= len(cats) <= 4, "Group should cover 2–4 related categories: " + group["id"])
        require(len(cats) == len(set(cats)), "Duplicate category in a group: " + group["id"])
        require(all(re.fullmatch(r"\\d{2}", cat) for cat in cats), "Invalid checklist ID")
        require(group["shared_fixture"].strip(), "Missing shared-scene reason")
        ids.extend(cats)
    expected = {f"{i:02d}" for i in range(1, 18)}
    require(set(ids) == expected and len(ids) == 17, "All 17 categories must have exactly one primary group")
    focus = policy["current_focus"]
    require(focus["primary_category"] == "02", "Do not silently switch active category")
    require(set(focus["permitted_related_test_categories"]) <= set(policy["batch_templates"][0]["categories"]),
            "Cross-checks must share road-scene coverage")
    require(set(focus["explicit_not_started"]) == {"03", "04", "05"},
            "Do not claim unapproved categories are developed")

    c2 = load(CATEGORY2)
    require(c2["category"] == "02" and len(c2["items"]) == 9, "Category 2 must keep all nine items")
    require(c2["traffic_test_mode"] == "SIMULATION_ONLY_NO_CREDENTIALS", "Traffic tests are keyless simulation")
    require(len(c2["render_expected"]) >= 2, "Map rendering requires native integration checks")

    independent = load(UK)
    official = {s["id"] for s in independent["sources"]}
    require(all(s["url"].startswith("https://www.gov.uk/") for s in independent["sources"]),
            "UK rule references cannot come from the app itself")
    require(all(set(sc["source_ids"]) <= official for sc in independent["scenarios"]),
            "All rule-based scenarios must reference the independent evidence registry")
    require("not_legal_truth" not in str(independent) or independent["road_truth_status"], "No assumed legal truth")

    fixture = load(ROOT / "fixtures/tomtom-v5-simulated-traffic.json")
    require(fixture["kind"] == "SYNTHETIC_TOMTOM_V5_TRAFFIC_TEST_DATA", "Do not relabel live provider records")
    for banned in ("api_key", "apikey", "private_key", "access_token"):
        require(banned not in fixture.keys(), "Credential field in fixture")
    print(json.dumps({
        "status": "public_governance_passed",
        "groups": len(policy["batch_templates"]),
        "categories_covered": len(ids),
        "preferred_size": "2-4",
        "category2_requirements": len(c2["items"]),
        "category2_rendered_robots_expected": len(c2["render_expected"]),
        "traffic_mode": c2["traffic_test_mode"],
        "limit": "Public contract/provenance validation only; never report private app or emulator tests passed"
    }, indent=2))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (AssertionError, KeyError, ValueError) as e:
        print(f"PUBLIC TEST GOVERNANCE FAILED: {e}", file=sys.stderr)
        sys.exit(1)
