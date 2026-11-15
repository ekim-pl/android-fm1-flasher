#!/usr/bin/env python3
"""Reject an F-Droid APK containing Firebase or Google Play tracking SDKs."""
import re
import subprocess
import sys
import zipfile
from pathlib import Path


PACKAGE = 'pl.ekim.fm1_flasher'
FORBIDDEN = (b'com/google/firebase/', b'com/google/android/gms/measurement/',
             b'com/google/android/gms/ads/identifier/')


def main(apk_path, aapt_path):
    root = Path.cwd()
    gradle = (root / 'app/build.gradle').read_text()
    version_name = re.search(r"versionName\s+'([^']+)'", gradle).group(1)
    version_code = re.search(r'versionCode\s+(\d+)', gradle).group(1)
    apk = Path(apk_path)
    if not apk.is_file():
        raise ValueError(f'APK not found: {apk}')

    badging = subprocess.check_output([aapt_path, 'dump', 'badging', str(apk)], text=True)
    expected = f"package: name='{PACKAGE}' versionCode='{version_code}' versionName='{version_name}'"
    if expected not in badging:
        raise ValueError(f'Unexpected APK identity; expected {expected}')

    with zipfile.ZipFile(apk) as archive:
        dex = b''.join(archive.read(name) for name in archive.namelist()
                       if re.fullmatch(r'classes(?:\d+)?\.dex', name))
    if not dex:
        raise ValueError('APK contains no DEX files')
    for marker in FORBIDDEN:
        if marker in dex:
            raise ValueError(f'Forbidden Google tracking SDK reference: {marker.decode()}')
    print(f'F-Droid APK verified: {PACKAGE} {version_name} ({version_code}), no Firebase/Google tracking SDK')


if __name__ == '__main__':
    try:
        main(sys.argv[1], sys.argv[2])
    except (IndexError, OSError, ValueError, subprocess.CalledProcessError, zipfile.BadZipFile) as error:
        print(f'F-Droid APK verification failed: {error}', file=sys.stderr)
        raise SystemExit(1)
