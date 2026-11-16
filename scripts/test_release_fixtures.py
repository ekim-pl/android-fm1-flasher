import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from release_fixtures import HttpsRedirect, fixtures, prepare, validate, verify


class ReleaseFixtureTest(unittest.TestCase):
    def test_official_fixture_manifest_has_both_versions(self):
        self.assertEqual({v['product'] for v in fixtures().values()}, {'FM-1_015', 'FM-1_016'})

    def test_download_validates_bytes_and_uses_verified_cache(self):
        raw = b'x' * 960
        item = dict(sha256=hashlib.sha256(raw).hexdigest(), url='https://example.com/fwsc')
        with tempfile.TemporaryDirectory() as tmp, patch('release_fixtures.fixtures', return_value={'v15': item}), \
                patch('release_fixtures.urllib.request.build_opener') as opener:
            opener.return_value.open.return_value.__enter__.return_value.read.return_value = raw
            prepare(directory=Path(tmp))
            self.assertEqual((Path(tmp) / 'v15.fwsc').read_bytes(), raw)
            prepare(directory=Path(tmp))
            self.assertEqual(opener.return_value.open.call_count, 1)
            (Path(tmp) / 'v15.fwsc').write_bytes(b'corrupt')
            with self.assertRaises(ValueError):
                prepare(directory=Path(tmp))
        for content in (b'', raw + b'changed'):
            with self.assertRaises(ValueError):
                validate(content, item)

    def test_http_redirect_is_rejected(self):
        with self.assertRaises(ValueError):
            HttpsRedirect().redirect_request(None, None, 302, '', {}, 'http://example.com/firmware')

    def reports(self, directory, names, test='<testcase name="test"/>', **attributes):
        attrs = dict(tests='1', skipped='0', failures='0', errors='0')
        attrs.update(attributes)
        for name in names:
            details = ' '.join(f'{k}="{v}"' for k, v in attrs.items())
            (directory / f'TEST-{name}.xml').write_text(
                f'<testsuite name="pl.ekim.fm1_flasher.{name}" {details}>{test}</testsuite>')

    def test_missing_empty_skipped_or_failed_reports_cannot_pass(self):
        names = ('RecoveryTest', 'FirmwarePackageTest', 'CatalogTest')
        with tempfile.TemporaryDirectory() as tmp:
            target = Path(tmp)
            with self.assertRaises(ValueError):
                verify(target)
            for attributes, case in [({'tests': '0'}, ''), ({'skipped': '1'}, '<testcase/>'),
                                     ({'failures': '1'}, '<testcase/>'), ({'errors': '1'}, '<testcase/>'),
                                     ({}, '<testcase><skipped/></testcase>'),
                                     ({}, '<testcase><failure/></testcase>')]:
                self.reports(target, names, test=case, **attributes)
                with self.assertRaises(ValueError):
                    verify(target)
            self.reports(target, names)
            verify(target)
            with self.assertRaises(ValueError):
                verify(target, integration=True)
            self.reports(target, ['FirmwareIntegrationTest'])
            with self.assertRaises(ValueError):
                verify(target, integration=True)
            report = target / 'TEST-FirmwareIntegrationTest.xml'
            scenarios = ['officialPackagesHavePinnedIdentitiesAndV16CannotBeRecovered',
                         'recoveryRevalidatesBackupAndPreservesDeviceDataOnBothChips',
                         'changedDeviceAndUnknownBootBlockRecovery']
            report.write_text('<testsuite name="pl.ekim.fm1_flasher.FirmwareIntegrationTest" tests="3">' +
                              ''.join(f'<testcase name="{name}"/>' for name in scenarios) + '</testsuite>')
            verify(target, integration=True)


if __name__ == '__main__':
    unittest.main()
