import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile

SCRIPTS = Path(__file__).resolve().parents[1]

def load(name):
    spec = importlib.util.spec_from_file_location(name, SCRIPTS / (name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

class ExportTests(unittest.TestCase):
    def test_export_uses_committed_source_and_omits_private_and_generated_files(self):
        exporter = load('export_source')
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(['git', 'init', '-q', str(root)], check=True)
            files = {'app/main.kt': 'committed', '.env': 'secret', 'server/credentials.json': 'secret',
                     '.superpowers/progress.md': 'private', 'app/build/result.txt': 'build',
                     'local.properties': 'machine', 'gradle/wrapper/gradle-wrapper.jar': 'wrapper'}
            for name, body in files.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(body)
            subprocess.run(['git', '-C', str(root), 'add', '.'], check=True)
            subprocess.run(['git', '-C', str(root), '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid', 'commit', '-qm', 'fixture'], check=True)
            (root / 'app/main.kt').write_text('changed')
            (root / 'untracked-secret.txt').write_text('secret')
            output = root / 'source.zip'
            exporter.export_source(root, output, 'HEAD')
            with zipfile.ZipFile(output) as archive:
                self.assertEqual(set(archive.namelist()), {'app/main.kt', 'gradle/wrapper/gradle-wrapper.jar'})
                self.assertEqual(archive.read('app/main.kt'), b'committed')
            exporter.export_source(root, output, 'HEAD', working_tree=True)
            with zipfile.ZipFile(output) as archive:
                self.assertEqual(archive.read('app/main.kt'), b'changed')
                self.assertNotIn('untracked-secret.txt', archive.namelist())

    def test_export_guard_does_not_reject_its_own_source(self):
        exporter = load('export_source')
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(['git', 'init', '-q', str(root)], check=True)
            (root / 'export_source.py').write_bytes((SCRIPTS / 'export_source.py').read_bytes())
            subprocess.run(['git', '-C', str(root), 'add', '.'], check=True)
            subprocess.run(['git', '-C', str(root), '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid', 'commit', '-qm', 'fixture'], check=True)
            self.assertEqual(exporter.export_source(root, root / 'source.zip'), 1)
            (root / 'export_source.py').write_bytes(b'-----BEGIN RSA ' + b'PRIVATE KEY-----\nredacted\n-----END RSA ' + b'PRIVATE KEY-----')
            with self.assertRaisesRegex(ValueError, 'Private key material'):
                exporter.export_source(root, root / 'source.zip', working_tree=True)
    def test_rejects_unsafe_paths_and_credentials(self):
        exporter = load('export_source')
        for path in ['../escape', '/absolute', '.git/config', 'server/service-account.json', 'key.pem', 'sdk/private.jar', 'x/.env.production', 'server/secrets/token.txt']:
            with self.subTest(path=path):
                self.assertFalse(exporter.allowed_path(path))
        self.assertTrue(exporter.allowed_path('server/config.py'))

class UiTests(unittest.TestCase):
    def test_selects_portable_wrapper_and_task(self):
        ui = load('ui_tests')
        command = ui.gradle_command(Path('/repo'), connected=True, windows=False)
        self.assertEqual(command[0], str(Path('/repo/gradlew')))
        self.assertIn(':app:connectedDebugAndroidTest', command)
        self.assertIn(':app:pixel2api35DebugAndroidTest', ui.gradle_command(Path('/repo'), connected=False, windows=True))
        self.assertEqual(ui.gradle_command(Path('/repo'), connected=False, windows=True)[0], str(Path('/repo/gradlew.bat')))

    def test_propagates_gradle_failure(self):
        ui = load('ui_tests')
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            wrapper = root / 'gradlew'
            wrapper.write_text('#!/bin/sh\nexit 7\n')
            if sys.platform == 'win32':
                (root / 'gradlew.bat').write_text('@exit /b 7\n')
            else:
                wrapper.chmod(0o755)
            self.assertEqual(ui.run(root, connected=True), 7)

if __name__ == '__main__':
    unittest.main()


