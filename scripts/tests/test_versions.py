import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / 'bump-version.py'
spec = importlib.util.spec_from_file_location('vizhi_bump', SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class BumpVersionTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        for x in ('android-version.json','tv-version.json'):
            (self.root / x).write_text(json.dumps({'versionCode':7,'versionName':'1.2.3'}))

    def test_mobile_bump_only_affects_mobile_file(self):
        mobile=self.root/'android-version.json'; tv=self.root/'tv-version.json'
        before=tv.read_bytes()
        result=module.bump_file(mobile)
        self.assertEqual(result['versionCode'], 8)
        self.assertEqual(result['versionName'], '1.2.4')
        self.assertEqual(tv.read_bytes(), before)

    def test_tv_minor_resets_patch(self):
        result=module.bump_file(self.root/'tv-version.json','minor')
        self.assertEqual(result['versionName'],'1.3.0')
        self.assertEqual(result['versionCode'],8)

    def test_major(self):
        self.assertEqual(module.bump_file(self.root/'tv-version.json','major')['versionName'],'2.0.0')

    def test_invalid_code_rejected_without_modification(self):
        path=self.root/'tv-version.json'
        path.write_text('{"versionCode": true, "versionName":"1.0.0"}')
        orig=path.read_bytes()
        with self.assertRaises(ValueError): module.bump_file(path)
        self.assertEqual(orig,path.read_bytes())

    def test_invalid_name_rejected(self):
        path=self.root/'tv-version.json'
        path.write_text('{"versionCode": 2, "versionName":"not-a-version"}')
        with self.assertRaises(ValueError): module.bump_file(path)

if __name__=='__main__': unittest.main()
