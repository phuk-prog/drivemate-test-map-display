#!/usr/bin/env python3
"""Negative controls: the public 157-item batch contract gate must detect defects."""
import copy
import json
from pathlib import Path
import runpy

ROOT=Path(__file__).resolve().parents[1]
load=lambda path:json.loads((ROOT/path).read_text(encoding="utf8"))
validator=runpy.run_path(str(ROOT/"ci/validate_app_batch_register.py"),run_name="batch_validator")
verify=validator["verify"]
base=load("contracts/app-batch-register.json")
groups=load("contracts/grouped-public-tests.json")
a=load("batches/A-roundabouts-maps-positioning/contracts/checklist.json")
b=load("batches/B-guidance-alerts/contracts/baseline.json")
c2=load("contracts/category2.json")
def must_fail(change,name):
    x=copy.deepcopy(base)
    change(x)
    try: verify(x,groups,a,b,c2)
    except AssertionError: return
    raise AssertionError("Validator accepted faulty fixture: "+name)

assert sum(1 for _ in base["batches"])==5
verify(base,groups,a,b,c2)
must_fail(lambda x:x["batches"][0]["items"].pop(),"dropped item")
must_fail(lambda x:x["batches"][0]["items"].append(copy.deepcopy(x["batches"][0]["items"][0])),"duplicate item")
must_fail(lambda x:x["batches"][0]["items"][0].update(category="16"),"wrong category")
must_fail(lambda x:x["batches"][0]["category_ids"].pop(),"wrong group size")
must_fail(lambda x:x["batches"][0]["items"][0].update(accepted=True),"invented approval")
must_fail(lambda x:x["batches"][0]["items"][0].update(requirement="The answer is what the app produces."),"changed expected wording")
must_fail(lambda x:x["batches"][1]["items"][0].update(id="03.44",category="03"),"missing checklist ID")
print("PASS: 7 mutation controls rejected by the public 157-item checklist batch validator")
