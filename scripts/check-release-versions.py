#!/usr/bin/env python3
"""Verify the release branch changes only its own version metadata."""
from __future__ import annotations
import argparse
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FILES = {"mobile": "android-version.json", "tv": "tv-version.json"}
ZERO_SHA = "0" * 40


def version(text: str, name: str) -> tuple[int, tuple[int, int, int]]:
    data = json.loads(text)
    code = data.get("versionCode")
    label = data.get("versionName")
    if type(code) is not int or not 1 <= code < 2_100_000_000:
        raise ValueError(f"{name}: invalid versionCode")
    if not isinstance(label, str) or not re.fullmatch(r"\d+\.\d+\.\d+", label):
        raise ValueError(f"{name}: versionName must be major.minor.patch")
    return code, tuple(map(int, label.split(".")))


def verify_release(platform: str, before: str, root: Path = ROOT) -> None:
    if platform not in FILES:
        raise ValueError(f"Invalid platform: {platform}")
    if not re.fullmatch(r"[a-fA-F0-9]{40}", before):
        raise ValueError("Missing or invalid 40-character previous commit SHA")
    if before == ZERO_SHA:
        for file in FILES.values():
            version((root / file).read_text(encoding="utf-8"), file)
        print("New release branch: validated initial version metadata")
        return
    for kind, file in FILES.items():
        result = subprocess.run(
            ["git", "show", f"{before}:{file}"],
            cwd=root, capture_output=True, text=True, check=False
        )
        if result.returncode:
            raise ValueError(f"Could not read previous {file} from {before}; refusing release")
        old = version(result.stdout, f"{before}:{file}")
        new = version((root / file).read_text(encoding="utf-8"), file)
        if kind == platform:
            if new[0] != old[0]:
                raise ValueError(
                    f"{file}: versionCode changed from {old[0]} to {new[0]} before CI; "
                    "preserve the release branch versionCode when merging"
                )
            if new[1] < old[1]:
                raise ValueError(f"{file}: versionName regressed from {old[1]} to {new[1]}")
        elif new != old:
            raise ValueError(
                f"{file}: non-target {kind} version changed on the {platform} branch"
            )
    print(f"Verified {platform} version isolation against {before[:12]}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--platform", required=True, choices=sorted(FILES))
    parser.add_argument("--before", required=True)
    parser.add_argument("--root", type=Path, default=ROOT)
    args = parser.parse_args()
    try:
        verify_release(args.platform, args.before, args.root.resolve())
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        parser.exit(1, f"Release version preflight failed: {exc}\n")


if __name__ == "__main__":
    main()
