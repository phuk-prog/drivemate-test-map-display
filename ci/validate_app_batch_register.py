#!/usr/bin/env python3
"""Validate the 157-item DriveMate app batch mirror in PUBLIC CI.

These are public owner-approved checklist specifications and batch assignments,
not assertions that any component or integrated APK has passed.
"""
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
def read(path):
    return json.loads((ROOT/path).read_text(encoding="utf-8"))
def verify(mirror,grouped,previous_a,previous_b,previous_c2):
    assert mirror["schema_version"]==1
    assert mirror["categories_total"]==17 and mirror["checklist_total"]==157
    assert len(mirror["batches"])==len(grouped["batches"])==5
    ids=[]
    cats=[]
    for record,g in zip(mirror["batches"],grouped["batches"]):
        assert record["id"]==g["id"]
        assert record["category_ids"]==g["categories"]
        assert 2<=len(record["category_ids"])<=4
        assert record["requires_separate_category_acceptance"] is True
        assert record["source_index"].startswith("https://github.com/phuk-prog/drivemate-app/blob/")
        cats+=record["category_ids"]
        for item in record["items"]:
            assert item["category"]==item["id"][:2]
            assert item["id"][:2] in record["category_ids"]
            assert item["accepted"] is False, "Do not claim full acceptance from grouping"
            assert item["requirement"] and len(item["requirement"])>15
            ids.append(item["id"])
    assert len(cats)==len(set(cats))==17
    assert set(cats)=={f"{k:02}" for k in range(1,18)}
    assert len(ids)==len(set(ids))==157
    for category in cats:
        local=sorted(i for i in ids if i.startswith(category+"."))
        assert local==[f"{category}.{k:02}" for k in range(1,len(local)+1)], (category,local)

    m={item["id"]:item["requirement"] for batch in mirror["batches"] for item in batch["items"]}
    ref_a={item["id"]:item["requirement"] for item in previous_a["requirements"]}
    assert len(ref_a)==22
    for id,expected in ref_a.items():
        assert m[id]==expected,(id,"Batch A checklist drift")
    assert len(previous_b["items"])==19
    for item in previous_b["items"]:
        assert item["id"] in m,(item["id"],"Batch B missing")
    assert len(previous_c2["items"])==9
    for item in previous_c2["items"]:
        assert item["id"] in m,(item["id"],"Category 2 public suite missing")

    assert mirror["baseline_evidence"]["A"]["status"].endswith("unverified")
    assert mirror["baseline_evidence"]["B"]["status"].endswith("pending")
    assert all(mirror["baseline_evidence"][c]["status"]=="not_tested" for c in ("C","D","E"))
    assert "public" in mirror["public_testing_rule"].lower()
    assert "robot" in mirror["release_policy"].lower()
    return {b["id"]:len(b["items"]) for b in mirror["batches"]}

if __name__=="__main__":
    current=read("contracts/app-batch-register.json")
    results=verify(current,read("contracts/grouped-public-tests.json"),
        read("batches/A-roundabouts-maps-positioning/contracts/checklist.json"),
        read("batches/B-guidance-alerts/contracts/baseline.json"),
        read("contracts/category2.json"))
    print(json.dumps({"status":"PUBLIC_157_ITEM_BATCH_CATALOG_VALIDATED",
        "counts":results,"total":sum(results.values()),
        "app_source_sha":current["source"]["app_commit_sha"],
        "integrated_app_verified":False,"apk_release_approved":False},indent=2))
