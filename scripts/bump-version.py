#!/usr/bin/env python3
"""Independent, deterministic mobile/TV version bumps."""
import argparse
import json
import os
import re
import tempfile
from pathlib import Path

VERSION_FILES = {"mobile": "android-version.json", "tv": "tv-version.json"}
MAX_VERSION_CODE = 2_100_000_000


def bump_file(path: Path, mode: str = "patch") -> dict:
    data = json.loads(path.read_text(encoding="utf-8"))
    code = data.get("versionCode")
    name = data.get("versionName")
    if type(code) is not int or code < 1 or code >= MAX_VERSION_CODE - 1:
        raise ValueError("versionCode must be a positive integer below 2,099,999,999")
    if not isinstance(name, str) or not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", name):
        raise ValueError("versionName must be semantic numeric major.minor.patch")
    major, minor, patch = map(int, name.split("."))
    if mode == "major":
        major, minor, patch = major + 1, 0, 0
    elif mode == "minor":
        minor, patch = minor + 1, 0
    elif mode == "patch":
        patch += 1
    else:
        raise ValueError("invalid bump mode")
    updated = {**data, "versionCode": code + 1, "versionName": f"{major}.{minor}.{patch}"}
    # Atomic replace prevents a partial JSON file if the process is interrupted.
    with tempfile.NamedTemporaryFile("w", dir=path.parent, prefix=".version-", suffix=".tmp", delete=False, encoding="utf-8") as handle:
        temp_path = Path(handle.name)
        json.dump(updated, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    try:
        os.replace(temp_path, path)
    finally:
        temp_path.unlink(missing_ok=True)
    return updated


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--platform", required=True, choices=sorted(VERSION_FILES))
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument("--patch", dest="mode", action="store_const", const="patch")
    modes.add_argument("--minor", dest="mode", action="store_const", const="minor")
    modes.add_argument("--major", dest="mode", action="store_const", const="major")
    parser.set_defaults(mode="patch")
    args = parser.parse_args()
    data = bump_file(args.root / VERSION_FILES[args.platform], args.mode)
    print(f"{args.platform}: {data['versionName']} (versionCode {data['versionCode']})")


if __name__ == "__main__":
    main()
