import json
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]

class StageOneConfigurationTests(unittest.TestCase):
    def test_exact_sdk_package_matches_minor_dsl(self):
        sdk = dict(line.split('=', 1) for line in (ROOT/'android-sdk.properties').read_text().splitlines() if '=' in line)
        self.assertEqual(sdk['COMPILE_SDK_PACKAGE'], sdk['COMPILE_SDK_VERSION']+'.'+sdk['COMPILE_SDK_MINOR'])
        for name in ('android-release.yml', 'android-validation.yml'):
            workflow = (ROOT/'.github/workflows'/name).read_text()
            self.assertIn('${COMPILE_SDK_PACKAGE}', workflow)
            self.assertNotIn('platforms;android-${COMPILE_SDK_VERSION}', workflow)

    def test_release_remains_push_only_and_guarded(self):
        workflow = (ROOT/'.github/workflows/android-release.yml').read_text()
        self.assertNotIn('workflow_dispatch', workflow)
        self.assertIn('check-release-versions.py', workflow)
        self.assertIn('ref: ${{ github.sha }}', workflow)

    def test_locale_keys_match_and_branding_preserved(self):
        resources = []
        for folder in ('values', 'values-ta', 'values-sa'):
            resources.append({s.attrib['name']:s.text for s in ET.parse(ROOT/'android/app/src/main/res'/folder/'strings.xml').getroot()})
        for locale, native in zip(('en','ta','sa'), resources):
            web = json.loads((ROOT/f'web/vizhi-tivi/src/app/locales/{locale}.json').read_text(encoding='utf-8'))
            self.assertEqual(set(resources[0]),set(native))
            self.assertEqual(native,web)

    def test_development_fixture_is_test_only(self):
        source = (ROOT/'contracts/fixtures/catalogue-v1.json').read_bytes()
        self.assertFalse((ROOT/'web/vizhi-tivi/public/catalogue-v1.json').exists())
        gradle = (ROOT/'android/app/build.gradle.kts').read_text()
        self.assertNotIn('sourceSets.getByName("main").assets.directories.add("../../contracts/fixtures")', gradle)
        fixture = json.loads(source)
        self.assertTrue(fixture['developmentFixture'])
        self.assertTrue(all(not c['streams'] for c in fixture['channels']))
        vectors = json.loads((ROOT/'contracts/fixtures/playlist-vectors.json').read_text(encoding='utf-8'))
        self.assertEqual(vectors['input'], (ROOT/'contracts/fixtures/playlist.m3u').read_text(encoding='utf-8'))

    def test_web_validation_does_not_skip_existing_project(self):
        workflow=(ROOT/'.github/workflows/web-validation.yml').read_text()
        self.assertIn('cd web/vizhi-tivi',workflow)
        self.assertIn('npm test -- --watch=false',workflow)
        self.assertNotIn('exit 0',workflow)

    def test_web_publish_runs_tests_before_publishing(self):
        workflow = (ROOT/'.github/workflows/vizhi-tivi-web.yml').read_text()
        self.assertLess(workflow.index('npm test -- --watch=false'), workflow.index('Push compiled site to gh-pages'))
        self.assertIn("github.ref == 'refs/heads/main-github'", workflow)

    def test_tv_hardware_declaration_is_flavor_specific(self):
        android = '{http://schemas.android.com/apk/res/android}'
        main = ET.parse(ROOT/'android/app/src/main/AndroidManifest.xml').getroot()
        tv = ET.parse(ROOT/'android/app/src/tv/AndroidManifest.xml').getroot()
        self.assertFalse(any(x.get(android+'name') == 'android.software.leanback' for x in main.findall('uses-feature')))
        feature = next(x for x in tv.findall('uses-feature') if x.get(android+'name') == 'android.software.leanback')
        self.assertEqual(feature.get(android+'required'), 'true')
        self.assertIsNone(main.find('application').get(android+'banner'))
        self.assertEqual(tv.find('application').get(android+'banner'), '@drawable/tv_banner')
        mobile = ET.parse(ROOT/'android/app/src/mobile/AndroidManifest.xml').getroot()
        for manifest, category in ((mobile, 'android.intent.category.LAUNCHER'), (tv, 'android.intent.category.LEANBACK_LAUNCHER')):
            self.assertEqual(manifest.find('application/activity/intent-filter/category').get(android+'name'), category)

if __name__ == '__main__': unittest.main()
