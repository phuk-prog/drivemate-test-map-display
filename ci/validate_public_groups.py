#!/usr/bin/env python3
"""Public CI validation for DriveMate grouped test reference. No private app access."""
import json
from pathlib import Path
import sys

path = Path(sys.argv[1] if len(sys.argv) > 1 else "contracts/grouped-public-tests.json")
data = json.loads(path.read_text(encoding="utf-8"))
assert data["schema_version"] == 1
minimum = data["grouped_execution"]["minimum_categories"]
maximum = data["grouped_execution"]["maximum_categories"]
assert (minimum, maximum) == (2, 4), "Group sizes must be two to four categories"
expected = set(data["checklist_categories"])
assert expected == {f"{x:02}" for x in range(1, 18)}
seen = []
for batch in data["batches"]:
    ids = batch["categories"]
    assert minimum <= len(ids) <= maximum, f"Wrong size: {batch['id']}"
    assert len(ids) == len(set(ids)), f"Duplicate in group {batch['id']}"
    assert batch["scope"].strip() and batch["example_test_case"].strip()
    assert batch["id"].strip() and batch["title"].strip()
    for url in batch["public_test_repos"]:
        assert url.startswith("https://github.com/phuk-prog/drivemate-test-"), url
    seen.extend(ids)
assert len(seen) == len(expected)
assert len(seen) == len(set(seen)), "A category is grouped twice"
assert set(seen) == expected, "Missing or unrecognized categories"
visibility = data["visibility"]
assert visibility["app_source"].startswith("private ")
assert visibility["test_contracts"].startswith("public ")
assert "independent" in visibility["external_reference_rule"].lower()
assert "TomTom" in visibility["credentials"] and "signing" in visibility["credentials"]
assert "not released" in data["grouped_execution"]["release_rule"]
assert "checklist ID" in data["grouped_execution"]["reporting_rule"]
assert data["grouped_execution"]["allow_subset_of_group"]
print(json.dumps({
  "validated": True,
  "categories": len(seen),
  "groups": len(data["batches"]),
  "group_sizes": {b["id"]: len(b["categories"]) for b in data["batches"]},
  "source": str(path),
  "public_test_definition_only": True,
  "private_app_execution_claimed": False
}, indent=2))
