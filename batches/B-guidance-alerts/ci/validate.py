#!/usr/bin/env python3
"""Public source/contract validation only; does not inspect private app source."""
import json
from pathlib import Path
import re
import sys

ROOT=Path(__file__).resolve().parents[1]
j=json.loads((ROOT/"contracts/baseline.json").read_text())
expected={"03":3,"08":3,"09":13}
assert j["categories"]==["03","08","09"]
for category,n in expected.items():
    assert sum(it["id"].startswith(category+".") for it in j["items"])==n
assert j["phase"]=="baseline"
assert len(j["independent_sources"])>=4
assert all(s["url"].startswith("https://www.gov.uk/") for s in j["independent_sources"])
identities=[x for item in j["items"] for x in item.get("tests",[])]
assert len(identities)==len(set(identities))==16
body=(ROOT/"tests/BatchBGuidanceTrafficWarningsTest.kt").read_text()
found=re.findall(r"@Test\s+fun\s+(b\d\d_\d\d_\w+)\s*\(",body)
assert set(found)==set(identities),(set(identities)-set(found),set(found)-set(identities))
assert all(it["acceptance"].strip() for it in j["items"])
assert all(s["id"] in ("HC160_184","DFT_LANE","DFT_SIGNS","HC124_125") for s in j["independent_sources"])
assert "TomTom" not in body or "API" not in body  # No network or provider credential fixtures
print(json.dumps({"status":"PUBLIC_CONTRACT_VALIDATED","categories":list(expected),
  "per_category_items":expected,"logic_tests":len(found),"app_tests_executed":False,
  "legal_road_truth":"official documents referenced; actual road signage not yet validated"}))
