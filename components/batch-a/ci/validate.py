#!/usr/bin/env python3
import hashlib,json,pathlib,re
r=pathlib.Path(__file__).resolve().parents[1];repo=r.parents[1]
m=json.loads((r/'source-manifest.json').read_text())
for f in m['files']:assert hashlib.sha256((r/f['published_path']).read_bytes()).hexdigest()==f['compiled_sha256'],f['published_path']
expected={x['id'] for x in json.loads((repo/'batches/A-roundabouts-maps-positioning/contracts/expected.json').read_text())['cases']}
for f in (r/'tests-expanded').glob('*.kt'):
 s=f.read_text();pkg=re.search(r'^package ([\w.]+)',s,re.M)[1];cls=re.search(r'class (\w+)',s)[1]
 expected.update(pkg+'.'+cls+'#'+m for m in re.findall(r'@Test\s+fun\s+(\w+)\s*\(',s))
audit=json.loads((r/'evidence/test-expectation-audit.json').read_text())
assert len(expected)==len(audit['tests'])==68
assert expected=={x['id'] for x in audit['tests']}
sources=json.loads((r/'evidence/source-registry.json').read_text())
refs={x['id'] for x in sources['sources']}
assert all(x['basis'] and x['limitation'] and set(x['references'])<=refs and not x['claims_legal_compliance'] and not x['claims_complete_requirement'] for x in audit['tests'])
for f in r.rglob('*'):
 if not f.is_file() or set(f.relative_to(r).parts)&{'build','.gradle','.kotlin','.private-logs','__pycache__','results'}:continue
 assert f.suffix not in {'.apk','.aab','.jks','.keystore'},'No apps or keys in component repository'
 if f.suffix=='.kt':assert 'class MainActivity' not in f.read_text(),'Combined application entry point forbidden'
print(json.dumps({'component_sources':len(m['files']),'audited_tests':68,'claims_complete_batch':False}))
