#!/usr/bin/env python3
"""Prepare a pinned external fixture archive, or fail release checks on skipped tests."""
import hashlib
import io
import os
from pathlib import Path
import sys
import urllib.request
import zipfile
import xml.etree.ElementTree as ET

FILES = ('v15.fwsc', 'v16.fwsc', 'catalog.json')
LIMIT = 16 * 1024 * 1024


def unpack(raw, digest, directory):
    if len(digest) != 64 or hashlib.sha256(raw).hexdigest() != digest.lower():
        raise ValueError('Fixture archive SHA-256 mismatch')
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        # Read only explicit names; never extract archive paths or symlinks.
        if any(archive.namelist().count(name) != 1 for name in FILES):
            raise ValueError('Archive must contain exactly one of each fixture')
        data = {}
        for name in FILES:
            info = archive.getinfo(name)
            if not 0 < info.file_size <= 4 * 1024 * 1024:
                raise ValueError('Invalid fixture size')
            data[name] = archive.read(name)
    directory.mkdir(parents=True, exist_ok=True)
    for name, content in data.items():
        (directory / name).write_bytes(content)


def verify(directory):
    suites = [ET.parse(path).getroot() for path in directory.glob('TEST-*.xml')]
    required = {'pl.ekim.fm1_flasher.RecoveryTest', 'pl.ekim.fm1_flasher.FirmwarePackageTest',
                'pl.ekim.fm1_flasher.CatalogTest'}
    if not required.issubset({suite.get('name') for suite in suites}):
        raise ValueError('Required release test reports are missing')
    if any(int(suite.get(key, '0')) for suite in suites for key in ('skipped', 'failures', 'errors')):
        raise ValueError('Release tests must pass without skipped fixtures')
    if any(int(suite.get('tests', '0')) == 0 for suite in suites):
        raise ValueError('Empty release test suite')
    print('Release fixture gate passed')


class HttpsRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if not newurl.startswith('https://'):
            raise ValueError('Fixture redirect must use HTTPS')
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def prepare():
    url = os.environ.get('FM1_FIXTURE_ARCHIVE_URL', '')
    digest = os.environ.get('FM1_FIXTURE_ARCHIVE_SHA256', '')
    if not url.startswith('https://') or len(digest) != 64:
        raise ValueError('Configure a permitted HTTPS fixture archive URL and SHA-256 in release variables')
    with urllib.request.build_opener(HttpsRedirect()).open(url, timeout=30) as response:
        raw = response.read(LIMIT + 1)
    if len(raw) > LIMIT:
        raise ValueError('Fixture archive too large')
    directory = Path(os.environ['RUNNER_TEMP']) / 'fm1-fixtures'
    unpack(raw, digest, directory)
    with open(os.environ['GITHUB_ENV'], 'a') as env:
        env.write(f'FM1_TEST_PACKAGES={directory / "v15.fwsc"}{os.pathsep}{directory / "v16.fwsc"}\n')
        env.write(f'FM1_RECOVERY_V15={directory / "v15.fwsc"}\nFM1_TEST_CATALOG={directory / "catalog.json"}\n')


if __name__ == '__main__':
    if sys.argv[1:] == ['prepare']:
        prepare()
    elif sys.argv[1:] == ['verify']:
        verify(Path('app/build/test-results/testDebugUnitTest'))
    else:
        raise SystemExit('Usage: release_fixtures.py prepare|verify')
