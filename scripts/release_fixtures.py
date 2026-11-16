#!/usr/bin/env python3
"""Download pinned official packages only for production integration tests."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import urllib.request
import xml.etree.ElementTree as ET

MANIFEST = Path('scripts/firmware-fixtures.json')
DIRECTORY = Path('build/firmware-fixtures')
LIMIT = 4 * 1024 * 1024


def fixtures(manifest=MANIFEST):
    data = json.loads(manifest.read_text())
    if set(data) != {'v15', 'v16'}:
        raise ValueError('Expected V15 and V16 fixtures')
    for name, item in data.items():
        if (not item['url'].startswith('https://') or
                not re.fullmatch(r'[0-9a-f]{64}', item['sha256']) or
                item['product'] != 'FM-1_0' + name[1:]):
            raise ValueError('Invalid pinned firmware fixture')
    return data


def validate(raw, item):
    if not 960 <= len(raw) <= LIMIT or hashlib.sha256(raw).hexdigest() != item['sha256']:
        raise ValueError('Firmware fixture SHA-256 mismatch or invalid size')


class HttpsRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if not newurl.startswith('https://'):
            raise ValueError('Fixture redirect must use HTTPS')
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def prepare(manifest=MANIFEST, directory=DIRECTORY):
    directory.mkdir(parents=True, exist_ok=True)
    opener = urllib.request.build_opener(HttpsRedirect())
    for name, item in fixtures(manifest).items():
        target = directory / f'{name}.fwsc'
        if target.is_file():
            validate(target.read_bytes(), item)
        else:
            with opener.open(item['url'], timeout=45) as response:
                raw = response.read(LIMIT + 1)
            validate(raw, item)
            target.write_bytes(raw)
        print(f'Verified {name}: {item["sha256"]}')


def verify(directory, integration=False):
    suites = [ET.parse(path).getroot() for path in directory.glob('TEST-*.xml')]
    names = {'FirmwareIntegrationTest'} if integration else {'RecoveryTest', 'FirmwarePackageTest', 'CatalogTest'}
    required = {'pl.ekim.fm1_flasher.' + name for name in names}
    if not required.issubset({suite.get('name') for suite in suites}):
        raise ValueError('Required test reports are missing')
    if integration:
        coverage = {test.get('name') for suite in suites if suite.get('name') in required
                    for test in suite.findall('testcase')}
        if not {'officialPackagesHavePinnedIdentitiesAndV16CannotBeRecovered',
                'recoveryRevalidatesBackupAndPreservesDeviceDataOnBothChips',
                'changedDeviceAndUnknownBootBlockRecovery'}.issubset(coverage):
            raise ValueError('Required official package/recovery scenarios are missing')
    for suite in suites:
        tests = suite.findall('testcase')
        if (not tests or int(suite.get('tests', '0')) != len(tests) or
                any(int(suite.get(key, '0')) for key in ('skipped', 'failures', 'errors')) or
                any(test.find(key) is not None for test in tests for key in ('skipped', 'failure', 'error'))):
            raise ValueError('Tests must pass without skips, failures or empty suites')
    print('Integration test gate passed' if integration else 'Synthetic test gate passed')


def evidence(manifest=MANIFEST, directory=DIRECTORY):
    verify(Path('app/build/test-results/firmwareIntegrationTest'), integration=True)
    data = fixtures(manifest)
    for name, item in data.items():
        validate((directory / f'{name}.fwsc').read_bytes(), item)
    return dict(commit=subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
                fixtures={name: item['sha256'] for name, item in data.items()})


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['prepare', 'verify', 'verify-integration'])
    args = parser.parse_args()
    if args.command == 'prepare':
        prepare()
    else:
        integration = args.command == 'verify-integration'
        verify(Path('app/build/test-results/' + ('firmwareIntegrationTest' if integration else 'testDebugUnitTest')), integration)
