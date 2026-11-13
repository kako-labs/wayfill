import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from check_demo_isolation import check_apk

class DemoIsolationTest(unittest.TestCase):
    def apk(self, payload, name='classes.dex'):
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        path = Path(folder.name) / 'app.apk'
        with zipfile.ZipFile(path, 'w') as archive:
            archive.writestr(name, payload)
        return path

    def test_clean_release_passes(self):
        self.assertEqual([], check_apk(self.apk(b'dex\nLdev/fuelpilot/MainActivity;')))

    def test_debug_package_is_rejected(self):
        self.assertTrue(check_apk(self.apk(b'Ldev/fuelpilot/demo/DemoActivity;', 'classes2.dex')))

    def test_synthetic_marker_is_rejected(self):
        self.assertTrue(check_apk(self.apk(b'WAYFILL_SYNTHETIC_ONLY')))

    def test_apk_without_dex_fails_closed(self):
        with self.assertRaises(ValueError):
            check_apk(self.apk(b'placeholder', 'res/file'))

    def test_debug_launcher_in_manifest_is_rejected_for_both_string_encodings(self):
        for encoding in ('utf-8', 'utf-16-le'):
            path=self.apk(b'clean release dex')
            with zipfile.ZipFile(path,'a') as archive:
                archive.writestr('AndroidManifest.xml','dev.fuelpilot.demo.SyntheticLauncher'.encode(encoding))
            self.assertTrue(check_apk(path))

if __name__ == '__main__':
    unittest.main()
