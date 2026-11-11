#!/usr/bin/env python3
"""Export source-only ZIP from a Git revision; untracked files are never included."""
import argparse
import io
from pathlib import Path, PurePosixPath
import re
import stat
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BLOCKED_DIRS = {'.git', '.gradle', '.kotlin', '.gradle-user-home', '.idea', '.superpowers',
                'build', 'node_modules', '__pycache__', '.venv', 'venv', 'captures',
                'credentials', 'secrets', '.aws', '.ssh'}
BLOCKED_NAMES = {'local.properties', '.ds_store', 'credentials.json', 'service-account.json',
                 'service_account.json', 'google-services.json', 'keystore.properties'}
BLOCKED_SUFFIXES = {'.apk', '.aab', '.aar', '.zip', '.pyc', '.pyo', '.jks', '.keystore', '.pem', '.key', '.p12', '.pfx', '.jar'}

def allowed_path(name):
    path = PurePosixPath(name)
    lower = [part.lower() for part in path.parts]
    if not lower or path.is_absolute() or '..' in path.parts or '\\' in name or ':' in name:
        return False
    if any(part in BLOCKED_DIRS or part.startswith('.env') for part in lower):
        return False
    if lower[-1] in BLOCKED_NAMES or any(word in lower[-1] for word in ('credential', 'service-account', 'service_account', 'secret')):
        return False
    if name == 'gradle/wrapper/gradle-wrapper.jar':
        return True
    return path.suffix.lower() not in BLOCKED_SUFFIXES

def export_source(root, output, revision='HEAD', *, working_tree=False):
    root, output = Path(root).resolve(), Path(output).resolve()
    # git archive preserves only the selected revision and honors export-ignore.
    raw = subprocess.check_output(['git', 'archive', '--format=zip', revision], cwd=root)
    members = []
    with zipfile.ZipFile(io.BytesIO(raw)) as source:
        for entry in source.infolist():
            if entry.is_dir() or not allowed_path(entry.filename):
                continue
            mode = entry.external_attr >> 16
            if stat.S_ISLNK(mode):
                continue
            if working_tree:
                path = root / entry.filename
                if path.is_symlink() or not path.is_file() or not path.resolve().is_relative_to(root):
                    continue
                data = path.read_bytes()
            else:
                data = source.read(entry)
            if re.search(rb'-----BEGIN (?:[A-Z0-9]+ )*PRIVATE KEY-----', data):
                raise ValueError(f'Private key material found in {entry.filename}; export aborted.')
            members.append((entry, data))
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for entry, data in members:
            entry.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(entry, data)
    return len(members)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output', type=Path)
    parser.add_argument('--ref', default='HEAD', help='Committed revision (default HEAD).')
    parser.add_argument('--working-tree', action='store_true', help='Read current bytes only for paths already present in --ref; never includes new/untracked paths.')
    args = parser.parse_args()
    try:
        count = export_source(ROOT, args.output, args.ref, working_tree=args.working_tree)
    except (OSError, ValueError, subprocess.CalledProcessError, zipfile.BadZipFile) as error:
        print(f'Export failed: {error}', file=sys.stderr)
        return 1
    print(f'Exported {count} source files to {args.output}')
    return 0

if __name__ == '__main__':
    sys.exit(main())

