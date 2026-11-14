import hashlib
import io
from pathlib import Path
import tempfile
import unittest
import zipfile
from release_fixtures import FILES, unpack, verify


class ReleaseFixtureTest(unittest.TestCase):
    def test_archive_is_pinned_and_only_named_files_are_written(self):
        buf = io.BytesIO()
        with zipfile.ZipFile(buf, 'w') as archive:
            for name in FILES:
                archive.writestr(name, b'fixture')
            archive.writestr('../escape', b'bad')
        raw = buf.getvalue()
        with tempfile.TemporaryDirectory() as tmp:
            target = Path(tmp) / 'fixtures'
            with self.assertRaises(ValueError):
                unpack(raw, '0' * 64, target)
            unpack(raw, hashlib.sha256(raw).hexdigest(), target)
            self.assertEqual(set(FILES), {p.name for p in target.iterdir()})
            self.assertFalse((Path(tmp) / 'escape').exists())

    def test_missing_or_skipped_reports_cannot_pass(self):
        with tempfile.TemporaryDirectory() as tmp:
            target = Path(tmp)
            with self.assertRaises(ValueError):
                verify(target)
            for name in ('RecoveryTest', 'FirmwarePackageTest', 'CatalogTest'):
                (target / f'TEST-{name}.xml').write_text(
                    f'<testsuite name="pl.ekim.fm1_flasher.{name}" tests="1" skipped="1"/>')
            with self.assertRaises(ValueError):
                verify(target)
            for report in target.iterdir():
                report.write_text(report.read_text().replace('skipped="1"', 'skipped="0"'))
            verify(target)


if __name__ == '__main__':
    unittest.main()
