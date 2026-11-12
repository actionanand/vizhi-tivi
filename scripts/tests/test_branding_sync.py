import importlib.util
import shutil
import struct
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('branding_sync', ROOT / 'scripts/sync-branding.py')
BRANDING = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BRANDING)


class BrandingSyncTests(unittest.TestCase):
    def test_checked_in_assets_match_canonical_source(self):
        BRANDING.sync(check=True)
        outputs = BRANDING.generated()
        for name in ('android/app/src/main/res/drawable-nodpi/app_icon.png', 'web/vizhi-tivi/public/app-icon.png'):
            self.assertEqual(struct.unpack('>II', outputs[name][16:24]), (512, 512))
        self.assertEqual(outputs['web/vizhi-tivi/public/favicon.ico'][:6], b'\x00\x00\x01\x00\x01\x00')

    def test_missing_and_changed_output_are_detected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in ('common/branding/icon/app-icon.png', 'common/branding/tv-banner/tv-banner.png'):
                target = root / name
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(ROOT / name, target)
            with self.assertRaisesRegex(ValueError, 'Outdated branding'):
                BRANDING.sync(root, check=True)
            BRANDING.sync(root)
            BRANDING.sync(root, check=True)
            (root / 'web/vizhi-tivi/public/app-icon.png').write_bytes(b'changed')
            with self.assertRaisesRegex(ValueError, 'Outdated branding'):
                BRANDING.sync(root, check=True)

    def test_corrupted_source_is_rejected(self):
        data = bytearray((ROOT / 'common/branding/icon/app-icon.png').read_bytes())
        data[50] ^= 1
        with self.assertRaises(ValueError):
            BRANDING.indexed_pixels(bytes(data))


if __name__ == '__main__':
    unittest.main()
