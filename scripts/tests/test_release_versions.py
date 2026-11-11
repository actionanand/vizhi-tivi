"""Regression tests for independent Android and TV release version metadata."""
import importlib.util
import json
import subprocess
import tempfile
import unittest
from pathlib import Path

SOURCE = Path(__file__).resolve().parents[1] / "check-release-versions.py"
SPEC = importlib.util.spec_from_file_location("release_guard", SOURCE)
GUARD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GUARD)


class ReleaseVersionGuardTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.git("init", "-q")
        self.git("config", "user.name", "Test")
        self.git("config", "user.email", "test@example.com")
        self.set_version("android-version.json", 11, "1.2.3")
        self.set_version("tv-version.json", 31, "2.3.4")
        self.git("add", ".")
        self.git("commit", "-qm", "baseline")
        self.before = self.git("rev-parse", "HEAD").strip()

    def git(self, *args):
        return subprocess.check_output(["git", *args], cwd=self.root, text=True)

    def set_version(self, filename, code, name):
        (self.root / filename).write_text(
            json.dumps({"versionCode": code, "versionName": name}) + "\n"
        )

    def verify(self, target):
        GUARD.verify_release(target, self.before, self.root)

    def test_mobile_unchanged_passes(self):
        self.verify("mobile")

    def test_tv_unchanged_passes(self):
        self.verify("tv")

    def test_mobile_code_rollback_fails(self):
        self.set_version("android-version.json", 10, "1.2.3")
        with self.assertRaisesRegex(ValueError, "versionCode changed"):
            self.verify("mobile")

    def test_mobile_manual_code_bump_fails(self):
        self.set_version("android-version.json", 12, "1.2.4")
        with self.assertRaisesRegex(ValueError, "versionCode changed"):
            self.verify("mobile")

    def test_tv_code_rollback_fails(self):
        self.set_version("tv-version.json", 30, "2.3.4")
        with self.assertRaisesRegex(ValueError, "versionCode changed"):
            self.verify("tv")

    def test_tv_name_rollback_fails(self):
        self.set_version("tv-version.json", 31, "2.3.3")
        with self.assertRaisesRegex(ValueError, "versionName regressed"):
            self.verify("tv")

    def test_tv_name_advance_without_code_change_passes(self):
        self.set_version("tv-version.json", 31, "2.4.0")
        self.verify("tv")

    def test_mobile_release_rejects_tv_version_change(self):
        self.set_version("tv-version.json", 32, "2.3.5")
        with self.assertRaisesRegex(ValueError, "non-target"):
            self.verify("mobile")

    def test_tv_release_rejects_mobile_version_change(self):
        self.set_version("android-version.json", 12, "1.2.4")
        with self.assertRaisesRegex(ValueError, "non-target"):
            self.verify("tv")

    def test_new_release_branch_is_allowed(self):
        GUARD.verify_release("tv", "0" * 40, self.root)

    def test_unknown_git_baseline_fails(self):
        with self.assertRaisesRegex(ValueError, "Could not read previous"):
            GUARD.verify_release("tv", "a" * 40, self.root)

    def test_invalid_commit_sha_fails(self):
        with self.assertRaisesRegex(ValueError, "40-character"):
            GUARD.verify_release("mobile", "wrong", self.root)


if __name__ == "__main__":
    unittest.main()
