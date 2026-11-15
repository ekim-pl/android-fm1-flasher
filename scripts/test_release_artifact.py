import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from release_artifact import fetch, fingerprint, verify
from publish_android_release import publish


class ArtifactTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.tag = 'v0.10.0'
        self.commit = 'a' * 40
        self.manifest = dict(schema=1, variant='fdroid', package='pl.ekim.fm1_flasher',
                             tag=self.tag, commit=self.commit, run_id='123', run_attempt=1,
                             version_name='0.10.0', version_code=31,
                             certificate_sha256='A' * 64,
                             file='fm1-flasher-v0.10.0-fdroid.apk')
        apk = self.directory / self.manifest['file']
        apk.write_bytes(b'validated apk')
        self.manifest['sha256'] = hashlib.sha256(apk.read_bytes()).hexdigest()
        self.save()

    def save(self):
        (self.directory / 'manifest.json').write_text(json.dumps(self.manifest))

    def check(self):
        return verify(self.directory, 'fdroid', self.tag, self.commit, '123')

    def test_matching_artifact_is_accepted(self):
        self.assertEqual(self.check(), self.manifest)

    def test_tampering_and_wrong_source_are_rejected(self):
        for key, value in [('commit', 'b' * 40), ('run_id', '456'), ('variant', 'debug'),
                           ('tag', 'v0.9.0'), ('package', 'other.app'),
                           ('file', '../escape.apk'), ('version_code', 0), ('run_attempt', 0),
                           ('certificate_sha256', 'bad'), ('version_name', '0.9.0')]:
            with self.subTest(key=key):
                original = self.manifest[key]
                self.manifest[key] = value
                self.save()
                with self.assertRaises(ValueError):
                    self.check()
                self.manifest[key] = original
        self.save()
        (self.directory / self.manifest['file']).write_bytes(b'modified')
        with self.assertRaises(ValueError):
            self.check()

    def test_missing_binary_is_rejected(self):
        (self.directory / self.manifest['file']).unlink()
        with self.assertRaises(ValueError):
            self.check()

    def test_source_job_must_succeed_before_download(self):
        run = dict(path='.github/workflows/fdroid-release.yml', event='workflow_dispatch',
                   head_repository=dict(full_name='owner/repo'))
        with patch.dict(os.environ, GH_REPO='owner/repo'), patch('release_artifact.api') as api, \
                patch('release_artifact.subprocess.run') as command:
            api.side_effect = [run, {'jobs': [dict(name='Build F-Droid APK', conclusion='failure')]}]
            with self.assertRaises(ValueError):
                fetch('fdroid', '123', self.tag, self.commit, self.directory)
            command.assert_not_called()

    def test_independent_and_combined_builds_can_be_retrieved(self):
        for workflow, name in [('fdroid-release.yml', 'Build F-Droid APK'),
                               ('android-release.yml', 'fdroid / Build F-Droid APK')]:
            with self.subTest(workflow=workflow), patch.dict(os.environ, GH_REPO='owner/repo'), \
                    patch('release_artifact.api') as api, patch('release_artifact.subprocess.run'):
                api.side_effect = [dict(path='.github/workflows/' + workflow,
                                        event='workflow_dispatch',
                                        head_repository=dict(full_name='owner/repo')),
                                   {'jobs': [dict(name=name, conclusion='success', run_attempt=1),
                                             dict(name=name, conclusion='failure', run_attempt=2)]}]
                self.assertEqual(fetch('fdroid', '123', self.tag, self.commit, self.directory), self.manifest)

    def test_wrong_workflow_is_rejected(self):
        with patch.dict(os.environ, GH_REPO='owner/repo'), patch('release_artifact.api') as api:
            api.return_value = dict(path='.github/workflows/android-ci.yml', event='pull_request')
            with self.assertRaises(ValueError):
                fetch('fdroid', '123', self.tag, self.commit, self.directory)

    def test_certificate_requires_one_valid_signer(self):
        cert = ':'.join(['AB'] * 32)
        self.assertEqual(fingerprint('SHA256: ' + cert), 'AB' * 32)
        for text in ('unsigned', 'SHA256: 123', 'SHA256: ' + cert + '\nSHA256: ' + 'CD' * 32):
            with self.assertRaises(ValueError):
                fingerprint(text)

    def test_draft_retry_uses_same_artifacts_and_public_release_is_idempotent(self):
        debug = dict(self.manifest, variant='debug')
        for variant, manifest in [('debug', debug), ('fdroid', self.manifest)]:
            (self.directory / f'{variant}-manifest.json').write_text(json.dumps(manifest))
        provenance = json.dumps([debug, self.manifest], sort_keys=True).encode()
        marker = '<!-- fm1-artifacts-sha256:' + hashlib.sha256(provenance).hexdigest() + ' -->'
        for draft in (True, False):
            with self.subTest(draft=draft), patch.dict(os.environ, GH_REPO='owner/repo'), \
                    patch('publish_android_release.subprocess.check_output') as listing, \
                    patch('publish_android_release.subprocess.run') as command:
                listing.return_value = json.dumps([[dict(tag_name=self.tag, draft=draft, body=marker)]])
                publish(self.tag, self.directory)
                self.assertEqual(command.call_count, 2 if draft else 0)
        with patch.dict(os.environ, GH_REPO='owner/repo'), \
                patch('publish_android_release.subprocess.check_output', return_value=json.dumps(
                    [[dict(tag_name=self.tag, draft=True, body='different artifacts')]])), \
                patch('publish_android_release.subprocess.run') as command:
            with self.assertRaises(ValueError):
                publish(self.tag, self.directory)
            command.assert_not_called()


    def test_fdroid_can_publish_without_a_debug_build(self):
        (self.directory / 'fdroid-manifest.json').write_text(json.dumps(self.manifest))
        with patch.dict(os.environ, GH_REPO='owner/repo'), \
                patch('publish_android_release.subprocess.check_output', return_value='[[]]'), \
                patch('publish_android_release.subprocess.run') as command:
            publish(self.tag, self.directory)
            self.assertEqual(command.call_count, 3)
            self.assertNotIn('Developer APK', (self.directory / 'release-notes.txt').read_text())

    def test_different_variant_versions_cannot_share_a_release(self):
        (self.directory / 'fdroid-manifest.json').write_text(json.dumps(self.manifest))
        (self.directory / 'debug-manifest.json').write_text(json.dumps(dict(self.manifest, version_code=32)))
        with self.assertRaises(ValueError):
            publish(self.tag, self.directory)


if __name__ == '__main__':
    unittest.main()
