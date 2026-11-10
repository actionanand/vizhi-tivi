#!/usr/bin/env python3
"""Copy source PNG branding into Android drawable-nodpi assets."""
from pathlib import Path
from shutil import copy2
from struct import unpack

ROOT=Path(__file__).resolve().parents[1]
PAIRS=[('common/branding/icon/app-icon.png','android/app/src/main/res/drawable-nodpi/app_icon.png',(512,512)),
       ('common/branding/tv-banner/tv-banner.png','android/app/src/main/res/drawable-nodpi/tv_banner.png',(320,180))]


def size(path):
    data=path.read_bytes()[:24]
    if len(data)<24 or data[:8]!=b'\x89PNG\r\n\x1a\n':
        raise ValueError(f'Not a PNG: {path}')
    return unpack('>II',data[16:24])

if __name__=='__main__':
    for src,dst,expected in PAIRS:
        origin,destination=ROOT/src,ROOT/dst
        if size(origin)!=expected: raise SystemExit(f'{src} should be {expected}, got {size(origin)}')
        destination.parent.mkdir(parents=True,exist_ok=True)
        copy2(origin,destination)
        print(f'{src} -> {dst}')
