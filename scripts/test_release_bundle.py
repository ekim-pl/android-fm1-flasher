import os
import re
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch, Mock
import zipfile

import release_bundle as bundle
from release_artifact import create, certificate


def manifest(package='pl.ekim.fm1_flasher', name='0.10.0', code='31', minimum='34', target='36'):
    return (f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
            f'package="{package}" android:versionName="{name}" android:versionCode="{code}">'
            f'<uses-sdk android:minSdkVersion="{minimum}" android:targetSdkVersion="{target}"/>'
            '<application android:hasCode="false"/></manifest>')


class BundleTest(unittest.TestCase):
    def check(self, xml):
        bundle.validate_manifest(xml, 'pl.ekim.fm1_flasher', '0.10.0', 31, 34, 36)

    def test_actual_manifest_fields_must_match(self):
        self.check(manifest())
        for field, value in [('package', 'other.app'), ('name', '0.9.0'), ('code', '30'),
                             ('minimum', '33'), ('target', '35')]:
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.check(manifest(**{field: value}))

    def test_missing_duplicate_sdk_and_extra_version_constraints_rejected(self):
        for xml in [manifest().replace('<uses-sdk', '<other'),
                    manifest().replace('<application', '<uses-sdk/><application'),
                    manifest().replace('android:versionCode="31"', 'android:versionCode="31" android:versionCodeMajor="1"'),
                    manifest().replace('android:targetSdkVersion="36"', 'android:targetSdkVersion="36" android:maxSdkVersion="36"')]:
            with self.assertRaises(ValueError):
                self.check(xml)

    def test_download_and_cached_jar_are_hash_checked(self):
        import hashlib
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'bundletool.jar'
            response = Mock()
            response.__enter__ = Mock(return_value=response)
            response.__exit__ = Mock(return_value=False)
            response.read.return_value = b'wrong download'
            with patch('release_bundle.urllib.request.build_opener') as opener:
                opener.return_value.open.return_value = response
                with self.assertRaises(ValueError):
                    bundle.bundletool(path)
                self.assertFalse(path.exists())
                response.read.return_value = b'verified jar'
                with patch('release_bundle.SHA256', hashlib.sha256(b'verified jar').hexdigest()):
                    self.assertEqual(bundle.bundletool(path), path)
                    self.assertEqual(bundle.bundletool(path), path)
            path.write_bytes(b'tampered cache')
            with self.assertRaises(ValueError):
                bundle.bundletool(path)

    def test_structure_validation_precedes_manifest_dump(self):
        with patch('release_bundle.bundletool', return_value=Path('verified.jar')), \
                patch('release_bundle.subprocess.run', side_effect=subprocess.CalledProcessError(1, 'validate')), \
                patch('release_bundle.subprocess.check_output') as dump:
            with self.assertRaises(subprocess.CalledProcessError):
                bundle.validate(Path('bad.aab'), 'pl.ekim.fm1_flasher', '0.10.0', 31, 34, 36)
            dump.assert_not_called()


@unittest.skipUnless(os.environ.get('FM1_BUNDLE_INTEGRATION') == '1',
                     'Opt-in: needs Java, Android SDK and pinned bundletool download/cache')
class SignedBundleIntegrationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.temp.cleanup)
        cls.root = Path(cls.temp.name)
        cls.jar = bundle.bundletool()
        gradle = Path('app/build.gradle').read_text()
        cls.expected = {key: re.search(pattern, gradle).group(1) for key, pattern in {
            'package': r"applicationId\s+'([^']+)'", 'name': r"versionName\s+'([^']+)'",
            'code': r'versionCode\s+(\d+)', 'minimum': r'minSdk\s+(\d+)',
            'target': r'targetSdk\s+(\d+)'}.items()}
        cls.sdk = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Android/Sdk')))
        cls.keystore = cls.root / 'test.jks'
        cls.command('keytool', '-genkeypair', '-keystore', str(cls.keystore), '-storepass', 'test-only',
                '-keypass', 'test-only', '-alias', 'test', '-keyalg', 'RSA', '-validity', '2',
                '-dname', 'CN=Local release validation test')

    @staticmethod
    def command(*args):
        subprocess.run(args, check=True, capture_output=True)

    def make_bundle(self, directory, **fields):
        directory.mkdir()
        source = directory / 'AndroidManifest.xml'
        source.write_text(manifest(**dict(self.expected, **fields)))
        proto = directory / 'proto.apk'
        self.command(str(self.sdk / 'build-tools/35.0.0/aapt2'), 'link', '--proto-format',
                 '-I', str(self.sdk / 'platforms/android-36/android.jar'),
                 '--manifest', str(source), '-o', str(proto))
        module = directory / 'base.zip'
        with zipfile.ZipFile(proto) as src, zipfile.ZipFile(module, 'w') as dst:
            for name in src.namelist():
                dst.writestr('manifest/AndroidManifest.xml' if name == 'AndroidManifest.xml' else name,
                             src.read(name))
        aab = directory / 'test.aab'
        self.command('java', '-jar', str(self.jar), 'build-bundle', '--modules=' + str(module), '--output=' + str(aab))
        self.command('jarsigner', '-keystore', str(self.keystore), '-storepass', 'test-only', str(aab), 'test')
        return aab

    def test_signed_real_bundles_create_only_matching_artifact(self):
        for index, fields in enumerate([{}, {'package': 'other.app'},
                                        {'code': str(int(self.expected['code']) + 1)},
                                        {'name': self.expected['name'] + '.wrong'},
                                        {'minimum': str(int(self.expected['minimum']) - 1)},
                                        {'target': str(int(self.expected['target']) - 1)}]):
            with self.subTest(fields=fields):
                aab = self.make_bundle(self.root / str(index), **fields)
                cert = certificate(aab, 'play')
                destination = self.root / f'output-{index}'
                with patch.dict(os.environ, EXPECTED_CERT_SHA256=cert, FM1_RELEASE_TAG='',
                                GITHUB_RUN_ID='123', GITHUB_RUN_ATTEMPT='1'):
                    if fields:
                        with self.assertRaisesRegex(ValueError, 'AAB base manifest'):
                            create('play', aab, destination)
                        self.assertFalse(destination.exists())
                    else:
                        create('play', aab, destination)
                        self.assertTrue((destination / 'manifest.json').is_file())

    def test_corrupt_bundle_rejected_by_real_tool(self):
        path = self.root / 'corrupt.aab'
        path.write_bytes(b'not an app bundle')
        with self.assertRaises(subprocess.CalledProcessError):
            bundle.validate(path, 'pl.ekim.fm1_flasher', '0.10.0', 31, 34, 36)
