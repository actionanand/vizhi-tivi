"""Exercise the actual release verifier with disposable, non-production keys."""
import os
import shutil
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'scripts/verify-aab.sh'


class SigningVerificationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.root = Path(cls.temp.name)
        cls.env = os.environ.copy()
        local_jdk = ROOT / '.tools/jdk/jdk-21.0.13+9/bin'
        if local_jdk.is_dir():
            cls.env['PATH'] = str(local_jdk) + os.pathsep + cls.env.get('PATH', '')
        cls.bash = str(Path('C:/Program Files/Git/bin/bash.exe')) if os.name == 'nt' and Path('C:/Program Files/Git/bin/bash.exe').is_file() else shutil.which('bash')
        cls.keytool = shutil.which('keytool', path=cls.env['PATH'])
        cls.jarsigner = shutil.which('jarsigner', path=cls.env['PATH'])
        if not cls.bash or not cls.keytool or not cls.jarsigner:
            cls.temp.cleanup()
            raise unittest.SkipTest('bash and JDK signing tools are required')
        cls.key = cls.root / 'disposable.p12'
        cls.jar = cls.root / 'signed.jar'
        cls.unsigned = cls.root / 'unsigned.jar'
        cls.env.update(SIGNING_KEYSTORE_PATH=str(cls.key), SIGNING_STORE_PASSWORD='temporary-test-only', SIGNING_KEY_ALIAS='expected')
        for alias in ('expected', 'wrong'):
            subprocess.run([cls.keytool, '-genkeypair', '-keystore', str(cls.key), '-storepass', 'temporary-test-only', '-alias', alias, '-keyalg', 'RSA', '-keysize', '2048', '-validity', '365', '-dname', 'CN=Disposable Test', '-noprompt'], env=cls.env, capture_output=True, check=True, timeout=60)
        for path in (cls.jar, cls.unsigned):
            with zipfile.ZipFile(path, 'w') as output:
                output.writestr('payload.txt', 'synthetic data')
        subprocess.run([cls.jarsigner, '-keystore', str(cls.key), '-storepass', 'temporary-test-only', str(cls.jar), 'expected'], env=cls.env, capture_output=True, check=True, timeout=60)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def verify(self, path, env=None):
        return subprocess.run([self.bash, str(SCRIPT), str(path)], env=env or self.env, capture_output=True, timeout=60).returncode

    def test_expected_signer_is_accepted(self):
        self.assertEqual(self.verify(self.jar), 0)

    def test_wrong_and_missing_signers_and_unsigned_are_rejected(self):
        for alias in ('wrong', 'missing'):
            env = {**self.env, 'SIGNING_KEY_ALIAS': alias}
            self.assertNotEqual(self.verify(self.jar, env), 0)
        self.assertNotEqual(self.verify(self.unsigned), 0)

    def test_tampering_and_missing_credentials_are_rejected(self):
        tampered = self.root / 'tampered.jar'
        with zipfile.ZipFile(self.jar) as source, zipfile.ZipFile(tampered, 'w') as target:
            for item in source.infolist():
                target.writestr(item, b'tampered' if item.filename == 'payload.txt' else source.read(item))
        self.assertNotEqual(self.verify(tampered), 0)
        env = self.env.copy()
        env.pop('SIGNING_STORE_PASSWORD')
        self.assertNotEqual(self.verify(self.jar, env), 0)


if __name__ == '__main__':
    unittest.main()
