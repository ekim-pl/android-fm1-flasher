#!/usr/bin/env python3
"""Assemble a Pages-hosted F-Droid repo from a published release APK."""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile


def latest_stable_release():
    releases = []
    page = 1
    while True:
        batch = json.loads(subprocess.check_output(
            ['gh', 'api', f"repos/{os.environ['GITHUB_REPOSITORY']}/releases?per_page=100&page={page}"],
            text=True))
        releases.extend(batch)
        if len(batch) < 100:
            break
        page += 1
    candidates = []
    for release in releases:
        match = re.fullmatch(r'v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)',
                             release['tag_name'])
        if release['draft'] or release['prerelease'] or not match:
            continue
        tag = release['tag_name']
        result = subprocess.run(['git', 'merge-base', '--is-ancestor', f'refs/tags/{tag}', 'HEAD'],
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if result.returncode == 0:
            candidates.append((tuple(map(int, match.groups())), tag))
        elif result.returncode != 1:
            raise ValueError(f'Cannot verify release tag {tag} against the default branch')
    if not candidates:
        raise ValueError('No published stable release on the default branch')
    return max(candidates)[1]


def main():
    tag = latest_stable_release()
    print(f'Selected latest stable mainline release: {tag}', flush=True)
    expected_cert = os.environ['FDROID_APP_CERT_SHA256'].replace(':', '').strip().lower()
    if not re.fullmatch(r'[0-9a-f]{64}', expected_cert):
        raise ValueError('FDROID_APP_CERT_SHA256 must be a SHA-256 fingerprint')
    with tempfile.TemporaryDirectory(prefix='fm1-fdroid-') as tmp:
        root = Path(tmp)
        repo = root / 'repo'
        repo.mkdir()
        (repo / 'icons').mkdir()
        shutil.copyfile('fastlane/metadata/android/en-US/images/icon.png', repo / 'icons/icon.png')
        subprocess.run(['gh', 'release', 'download', tag, '--repo', os.environ['GITHUB_REPOSITORY'],
                        '--pattern', '*-fdroid.apk', '--pattern', 'fdroid-manifest.json',
                        '--dir', str(root)], check=True)
        manifest = json.loads((root / 'fdroid-manifest.json').read_text())
        filename = f'fm1-flasher-{tag}-fdroid.apk'
        apk = root / filename
        if (manifest.get('file') != filename or manifest.get('tag') != tag or
                manifest.get('package') != 'pl.ekim.fm1_flasher' or
                manifest.get('variant') != 'fdroid' or
                hashlib.sha256(apk.read_bytes()).hexdigest() != manifest.get('sha256')):
            raise ValueError('Release APK and manifest do not match')
        signer = subprocess.check_output(['apksigner', 'verify', '--print-certs', str(apk)], text=True)
        certs = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F:]+)', signer)
        if len(certs) != 1 or certs[0].replace(':', '').lower() != expected_cert:
            raise ValueError('APK signer does not match the configured certificate')
        apk.rename(repo / filename)
        key = root / 'index.p12'
        key.write_bytes(base64.b64decode(os.environ['FDROID_INDEX_KEYSTORE_BASE64'], validate=True))
        key.chmod(0o600)
        config = {
            'repo_url': os.environ['FDROID_REPO_URL'],
            'repo_name': 'FM-1 Flasher',
            'repo_description': 'Official FM-1 Flasher releases',
            'keystore': str(key),
            'repo_keyalias': os.environ['FDROID_INDEX_KEY_ALIAS'],
            'keystorepass': {'env': 'FDROID_INDEX_KEYSTORE_PASSWORD'},
            'keypass': {'env': 'FDROID_INDEX_KEY_PASSWORD'},
            'archive_older': 0,
            'make_current_version_link': False,
        }
        # JSON is valid YAML and avoids quoting passwords or paths in shell commands.
        config_file = root / 'config.yml'
        config_file.write_text(json.dumps(config))
        config_file.chmod(0o600)
        metadata = root / 'metadata'
        metadata.mkdir()
        shutil.copyfile('publication/fdroid/repo-metadata.yml', metadata / 'pl.ekim.fm1_flasher.yml')
        subprocess.run(['fdroid', 'update', '--create-metadata'], cwd=root, check=True)
        shutil.rmtree(repo / 'status', ignore_errors=True)
        destination = Path('build/site/fdroid/repo')
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copytree(repo, destination)


if __name__ == '__main__':
    main()
