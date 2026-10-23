#!/usr/bin/env python3
"""Reject incomplete public baseline definitions; does not execute the private app."""
import json,pathlib,re
root=pathlib.Path(__file__).resolve().parents[1]
m=json.loads((root/'contracts/expected.json').read_text())
c=json.loads((root/'contracts/checklist.json').read_text())
assert m['categories']==['01','02','04','05']
assert len(c['requirements'])==22==c['count']
assert len({x['id'] for x in c['requirements']})==22
assert {k:sum(x['category']==k for x in c['requirements']) for k in m['categories']}=={'01':4,'02':9,'04':6,'05':3}
actual=set()
for f in (root/'tests').glob('*.kt'):
    text=f.read_text();package=re.search(r'^package ([\w.]+)',text,re.M)[1]
    cls=re.search(r'class (\w+)',text)[1]
    actual.update(package+'.'+cls+'#'+method for method in re.findall(r'@Test\s+fun\s+(\w+)\s*\(',text))
expected={x['id'] for x in m['cases']}
assert len(expected)==len(m['cases'])==55
assert expected==actual,(sorted(expected-actual),sorted(actual-expected))
assert all(x['category'] in m['categories'] for x in m['cases'])
assert all((root/x['source']).is_file() for x in m['cases'])
print(json.dumps({'public_definition_valid':True,'cases':55,'checklist_items':22,'private_app_executed':False}))
