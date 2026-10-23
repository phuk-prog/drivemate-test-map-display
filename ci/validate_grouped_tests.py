#!/usr/bin/env python3
"""Public governance check: grouped tests and independent UK truth requirements.

Validating a public contract is NOT a private app, emulator or robot test.
This deliberately validates the single canonical public grouping manifest.
"""
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return json.loads((ROOT / path).read_text(encoding="utf-8"))

def check(condition, msg):
    if not condition:
        raise AssertionError(msg)

def main():
    policy = read("contracts/grouped-public-tests.json")
    check(policy["schema_version"] == 1, "Unsupported canonical public test schema")
    check(policy["grouped_execution"]["minimum_categories"] == 2, "Prefer two related categories")
    check(policy["grouped_execution"]["maximum_categories"] == 4, "Maximum four related categories")
    check(policy["grouped_execution"]["allow_subset_of_group"], "Allow narrow paired regression runs")
    check("before" in policy["grouped_execution"]["reporting_rule"] or
          "baseline" in policy["grouped_execution"]["reporting_rule"],
          "Per-category pre/post evidence required")
    check("not released" in policy["grouped_execution"]["release_rule"].lower(),
          "Test APKs must not be released before the gates")
    visibility = policy["visibility"]
    check("public" in visibility["test_contracts"].lower(), "Tests must remain publicly inspectable")
    check("private" in visibility["app_source"].lower(), "No public private-app source")
    check(visibility["test_execution_repository_visibility"] == "public", "All tests execute publicly")
    check(visibility["component_source_publication"] == "allowed", "Relevant individual code may be public")
    check(visibility["combined_app_publication"] == "forbidden", "Keep the combined app private")
    check("No TomTom API key" in visibility["credentials"], "No provider secrets in public repositories")
    groups = policy["batches"]
    check(groups, "No coherent batch templates")
    ids = []
    for g in groups:
        c = g["categories"]
        check(2 <= len(c) <= 4, "Out-of-range group " + g["id"])
        check(len(c) == len(set(c)), "Repeated category in group " + g["id"])
        check(g["scope"] and g["example_test_case"], "No shared test scenario for " + g["id"])
        ids.extend(c)
    check(len(ids) == 17 and set(ids) == {f"{i:02}" for i in range(1,18)},
          "Exactly 17 category references must be covered without duplicates")
    road = next(g for g in groups if g["id"] == "A")
    check(set(road["categories"]) == {"01","02","04","05"},
          "Roundabout GPS + map + lanes + routing belongs together")
    check(len(road["public_test_repos"]) >= 2,
          "Existing public GPS and map-test repositories must be reused")

    c2 = read("contracts/category2.json")
    check(c2["category"] == "02" and len(c2["items"]) == 9,
          "Retain all nine map checklist items")
    check(c2["traffic_test_mode"] == "SIMULATION_ONLY_NO_CREDENTIALS",
          "TomTom traffic stays simulated/keyless")
    check(len(c2["render_expected"]) >= 2, "Do not replace native rendering with unit-only checks")

    official = read("contracts/uk-independent-evidence.json")
    official_ids = {s["id"] for s in official["sources"]}
    check(len(official_ids) >= 5, "Official UK source references missing")
    check(all(s["url"].startswith("https://www.gov.uk/") for s in official["sources"]),
          "Expected UK legal rules must originate independently, not app output")
    check(all(set(case["source_ids"]) <= official_ids for case in official["scenarios"]),
          "Every cited legal reference must be registered")
    check(bool(official["road_truth_status"]), "Unknown legal road truth must remain explicit")

    traffic = read("fixtures/tomtom-v5-simulated-traffic.json")
    check(traffic["kind"] == "SYNTHETIC_TOMTOM_V5_TRAFFIC_TEST_DATA",
          "Simulated provider fixtures may not claim to be real traffic")
    forbidden = {"api_key", "apikey", "access_token", "secret", "private_key"}
    def ensure_no_key(value):
        if isinstance(value,dict):
            check(not ({x.lower() for x in value} & forbidden), "Credential-style field in public data")
            for child in value.values(): ensure_no_key(child)
        elif isinstance(value,list):
            for child in value: ensure_no_key(child)
    ensure_no_key(traffic)

    print(json.dumps({
        "status": "PUBLIC_CONTRACTS_VALIDATED_ONLY",
        "grouped_categories": len(ids), "batch_families": len(groups),
        "preferred_groups": "2-4", "map_checklist_items":len(c2["items"]),
        "native_robot_checks_expected":len(c2["render_expected"]),
        "tomtom_traffic_test": "simulated, no key",
        "app_execution_verified":False,
        "public_test_apk_released":False,
    },indent=2))
    return 0

if __name__ == "__main__":
    try:
        sys.exit(main())
    except (KeyError,ValueError,AssertionError,StopIteration) as exc:
        print("Public batch policy verification failed:",exc,file=sys.stderr)
        sys.exit(1)
