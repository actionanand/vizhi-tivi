from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[2]

class StructureTests(unittest.TestCase):
    def test_both_release_branches_and_signing_present(self):
        workflow=(ROOT/'.github/workflows/android-release.yml').read_text()
        for value in ['main-android','main-tv','KEYSTORE_BASE64','KEYSTORE_PASSWORD','KEY_ALIAS','ci-build.sh','bump-version.py']:
            self.assertIn(value,workflow)

    def test_web_and_common_are_separate(self):
        self.assertTrue((ROOT/'web/README.md').is_file())
        self.assertTrue((ROOT/'common/branding/README.md').is_file())

    def test_single_android_app_module(self):
        text=(ROOT/'android/settings.gradle.kts').read_text()
        self.assertIn('include(":app")',text)
        self.assertNotIn('include(":tv")',text)

if __name__=='__main__': unittest.main()
