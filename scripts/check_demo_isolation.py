"""Fail the release gate if debug demo classes or fixtures occur in any DEX."""
import argparse
from pathlib import Path
import re
import zipfile

FORBIDDEN = (b'Ldev/fuelpilot/demo/', b'FUEL_PILOT_SYNTHETIC_ONLY')

def check_apk(path: Path) -> list[str]:
    findings = []
    with zipfile.ZipFile(path) as archive:
        dex_files = [name for name in archive.namelist() if re.fullmatch(r'classes(?:\d+)?\.dex', name)]
        if not dex_files:
            raise ValueError('APK contains no DEX files; isolation cannot be verified')
        for name in dex_files:
            contents = archive.read(name)
            for marker in FORBIDDEN:
                if marker in contents:
                    findings.append(f'{name}: {marker.decode()}')
    return findings

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    args = parser.parse_args()
    try:
        findings = check_apk(args.apk)
    except (OSError, ValueError, zipfile.BadZipFile) as failure:
        parser.exit(2, f'Cannot verify demo isolation: {failure}\n')
    if findings:
        print('Debug demo leaked into release:\n' + '\n'.join(findings))
        return 1
    print('Release DEX contains no demo package or synthetic marker.')
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
