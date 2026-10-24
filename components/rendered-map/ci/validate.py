#!/usr/bin/env python3
"""Offline checks before any emulator time: published app files are unedited, fixture is real.

Fails if a published file differs from the hash pinned to the private app commit, if an
unlisted file appears under app-src/, or if the OSM fixture lost its provenance.
"""
import hashlib
import json
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parents[1]


def main():
    manifest = json.loads((HERE / "source-manifest.json").read_text(encoding="utf-8"))
    assert manifest["scope"] == "isolated-component", "this suite never claims combined-app scope"
    listed = set()
    for f in manifest["published_files"]:
        path = HERE / f["public"]
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        assert digest == f["sha256"], f"{f['public']} differs from app commit {manifest['app_commit'][:12]}"
        listed.add(path.relative_to(HERE).as_posix())
    present = {p.relative_to(HERE).as_posix() for p in (HERE / "app-src").rglob("*") if p.is_file()}
    assert present == listed, f"unlisted app files: {sorted(present - listed)}"
    subprocess.check_call([sys.executable, "-I", str(HERE / "ci" / "make_fixture.py"), "--check",
                           str(HERE / "fixtures" / "princess-road-a5103-osm.json")])
    print("rendered-map sources and fixture ok:", len(listed), "app files")


if __name__ == "__main__":
    main()
