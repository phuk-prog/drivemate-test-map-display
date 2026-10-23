#!/usr/bin/env python3
"""One batch report, three independently scored checklist categories.

Read real private Gradle JUnit XML outcomes; enforce exactly one outcome for each
public test identity. Do not silently replace missing tests with a pass.
"""
import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET
import sys

def main():
    p = argparse.ArgumentParser()
    for x in ["contract","reports","app-sha","public-sha","output"]:
        p.add_argument("--"+x,required=True)
    a=p.parse_args()
    cfg=json.loads(Path(a.contract).read_text())
    expected={name:item["id"] for item in cfg["items"] for name in item.get("tests",[])}
    observed={}
    for file in Path(a.reports).rglob("TEST-*.xml"):
        root=ET.parse(file).getroot()
        for tc in root.iter("testcase"):
            name=tc.get("name","")
            if not name.startswith("b0"):continue
            status="passed"
            if tc.find("failure") is not None: status="failed"
            elif tc.find("error") is not None: status="error"
            elif tc.find("skipped") is not None: status="skipped"
            if name in observed:status="duplicate"
            observed[name]=status
    missing=sorted(set(expected)-set(observed))
    extra=sorted(set(observed)-set(expected))
    items={}
    for item in cfg["items"]:
        selected=item.get("tests",[])
        statuses={t:observed.get(t,"not_run") for t in selected}
        items[item["id"]]={
            "logic":"passed_diagnostic_only" if selected and all(s=="passed" for s in statuses.values()) else
                    "not_tested" if not selected else "failed_or_missing",
            "tests":statuses,
            "requires":item["acceptance"],
            "other_evidence":"unverified",
        }
    for category in cfg["categories"]:
        entries={k:v for k,v in items.items() if k.startswith(category+".")}
        pass_count=sum(1 for v in entries.values() if v["logic"]=="passed_diagnostic_only")
        items["category_"+category]={"item_count":len(entries),"logic_pass_count":pass_count,
                "fully_accepted":False,"incomplete":len(entries)-pass_count}
    result={
        "batch":"B","categories":cfg["categories"],"app_sha":a.app_sha,"public_suite_sha":a.public_sha,
        "expected":len(expected),"executed":len(observed),
        "passed":sum(s=="passed" for s in observed.values()),"missing":missing,"extra":extra,
        "items":items,
        "test_scope":"Private real-app JUnit logic tests using public third-party source-referenced expectations. Not a rendered UI, live API, speed-sign survey, traffic order, audio or Android Auto test.",
        "approval":"BASELINE_ONLY_NOT_CATEGORY_ACCEPTANCE",
        "tomtom_credentials_supplied":False,
        "apk_built_or_released":False
    }
    target=Path(a.output);target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(result,indent=2)+"\n")
    print(json.dumps({"passed":result["passed"],"expected":result["expected"],"missing":missing,
                      "extra":extra,"accepted_categories":0,"status":result["approval"]}))
    return 1 if missing or extra or any(s!="passed" for s in observed.values()) else 0

if __name__=="__main__":sys.exit(main())
