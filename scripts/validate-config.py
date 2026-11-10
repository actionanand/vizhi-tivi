#!/usr/bin/env python3
"""Validate the starter's shared identity and independent Android version files."""
import json
import re
from pathlib import Path
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[1]


def validate(root=ROOT):
    config = json.loads((root / 'app-config.json').read_text(encoding='utf-8'))
    assert config['appName'] == 'Vizhi TiVi'
    assert config['supportedLocales'] == ['en', 'ta', 'sa']
    ids = [config['mobileApplicationId'], config['tvApplicationId']]
    assert len(set(ids)) == 2, 'Separate version tracks require different application IDs'
    assert all(re.fullmatch(r'[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+', x) for x in ids)
    sdk = dict(line.strip().split('=', 1) for line in (root / 'android-sdk.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
    assert int(sdk['MIN_SDK_VERSION']) >= 26
    assert int(sdk['COMPILE_SDK_VERSION']) >= int(sdk['TARGET_SDK_VERSION'])
    for file in ['android-version.json', 'tv-version.json']:
        v = json.loads((root / file).read_text())
        assert type(v['versionCode']) is int and 1 <= v['versionCode'] < 2100000000
        assert re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+', v['versionName'])
    base = root / 'android/app/src/main/res'
    expected = {'values':'The world before your eyes.', 'values-ta':'உலகம் உங்கள் விழிகளில்!', 'values-sa':'विश्वं तव नयनयोः पुरतः'}
    for folder, tagline in expected.items():
        xml = ElementTree.parse(base / folder / 'strings.xml')
        found = {x.attrib['name']: x.text for x in xml.getroot() if x.tag == 'string'}
        assert found['tagline'] == tagline
    for rel in ['android/app/src/main/res/drawable-nodpi/app_icon.png', 'android/app/src/main/res/drawable-nodpi/tv_banner.png','common/branding/icon/app-icon.png','common/branding/tv-banner/tv-banner.png']:
        assert (root / rel).is_file(), rel
    print('Vizhi TiVi configuration, versions, localization and artwork paths: OK')


if __name__ == '__main__':
    validate()
