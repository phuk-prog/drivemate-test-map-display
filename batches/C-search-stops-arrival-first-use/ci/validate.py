import hashlib,json,pathlib
root=pathlib.Path(__file__).resolve().parents[1]
repo=root.parents[1]
m=json.loads((root/"source-manifest.json").read_text())
for x in m["source_files"]:
    data=(repo/x["path"]).read_bytes()
    digest=hashlib.sha1(b"blob "+str(len(data)).encode()+b"\0"+data).hexdigest()
    assert digest==x["blob"],"Source drift: "+x["path"]
for x in m["candidate_parity"]:
    assert x["private_candidate_blob"]==x["public_candidate_blob"],"Public/private candidate mismatch"
r=json.loads((root/"results/verified-20261024.json").read_text())
assert len(r["outcomes"])==18 and len({x["id"] for x in r["outcomes"]})==18
assert all(x["full_acceptance"]=="NOT_VERIFIED" for x in r["outcomes"])
assert r["results"]["baseline"]["total"]==r["results"]["candidate"]["total"]==45
assert r["results"]["baseline"]["failed"]==21 and r["results"]["candidate"]["failed"]==0
print("Batch C source provenance,18 outcomes and recorded exact-run evidence valid")
