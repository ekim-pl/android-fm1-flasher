#!/usr/bin/env python3
"""Assemble a Pages-hosted F-Droid repo from a published release APK."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile


def published_releases():
    page = 1
    while True:
        batch = json.loads(subprocess.check_output(
            ['gh', 'api', f"repos/{os.environ['GITHUB_REPOSITORY']}/releases?per_page=100&page={page}"],
            text=True))
        yield from (release for release in batch if not release['draft'])
        if len(batch) < 100:
            return
        page += 1


def on_default_branch(ref):
    result = subprocess.run(['git', 'merge-base', '--is-ancestor', ref, 'HEAD'],
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if result.returncode not in (0, 1):
        raise ValueError(f'Cannot verify {ref} against the default branch')
    return result.returncode == 0


def latest_stable_release():
    candidates = []
    for release in published_releases():
        match = re.fullmatch(r'v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)',
                             release['tag_name'])
        if not release['prerelease'] and match and on_default_branch(f"refs/tags/{release['tag_name']}"):
            candidates.append((tuple(map(int, match.groups())), release['tag_name']))
    if not candidates:
        raise ValueError('No published stable release on the default branch')
    return max(candidates)[1]


def latest_nightly_release():
    candidates = []
    for release in published_releases():
        tag = release['tag_name']
        match = re.fullmatch(r'nightly-([1-9][0-9]{6,9})', tag)
        if release['prerelease'] and match and 1000000 < int(match[1]) <= 2100000000:
            candidates.append((int(match[1]), tag))
    for code, tag in sorted(candidates, reverse=True):
        if on_default_branch(f'refs/tags/{tag}'):
            return tag, code
    return None


def download_apk(root, tag, nightly_code):
    variant = 'debug' if nightly_code else 'fdroid'
    manifest_name = f'{variant}-manifest.json'
    subprocess.run(['gh', 'release', 'download', tag, '--repo', os.environ['GITHUB_REPOSITORY'],
                    '--pattern', f'*-{variant}.apk', '--pattern', manifest_name,
                    '--dir', str(root)], check=True)
    manifest = json.loads((root / manifest_name).read_text())
    if nightly_code:
        name = manifest['version_name']
        if (not re.fullmatch(rf'[0-9]+\.[0-9]+\.[0-9]+-nightly\.{nightly_code - 1000000}', name) or
                manifest['version_code'] != nightly_code or manifest.get('commit') !=
                subprocess.check_output(['git', 'rev-parse', f'refs/tags/{tag}^{{commit}}'], text=True).strip()):
            raise ValueError('Invalid nightly build metadata')
        filename = f'fm1-flasher-{name}-debug.apk'
    else:
        filename = f'fm1-flasher-{tag}-fdroid.apk'
    apk = root / filename
    if (manifest.get('file') != filename or manifest.get('tag') != ('' if nightly_code else tag) or
            manifest.get('package') != 'pl.ekim.fm1_flasher' or manifest.get('variant') != variant or
            hashlib.sha256(apk.read_bytes()).hexdigest() != manifest.get('sha256')):
        raise ValueError('Release APK and manifest do not match')
    return apk, manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--nightly', action='store_true', help='Publish the nightly repository')
    nightly = parser.parse_args().nightly
    if nightly:
        selected = latest_nightly_release()
        if selected is None:
            print('No published nightly release on the default branch yet')
            return
        tag, nightly_code = selected
    else:
        tag, nightly_code = latest_stable_release(), None
    print(f'Selected {tag}', flush=True)
    prefix = 'FDROID_NIGHTLY' if nightly else 'FDROID'
    expected_cert = os.environ[f'{prefix}_APP_CERT_SHA256'].replace(':', '').strip().lower()
    if not re.fullmatch(r'[0-9a-f]{64}', expected_cert):
        raise ValueError(f'{prefix}_APP_CERT_SHA256 must be a SHA-256 fingerprint')
    with tempfile.TemporaryDirectory(prefix='fm1-fdroid-') as tmp:
        root = Path(tmp)
        repo = root / 'repo'
        repo.mkdir()
        shutil.copyfile('fastlane/metadata/android/en-US/images/icon.png', root / 'icon.png')
        apk, manifest = download_apk(root, tag, nightly_code)
        filename = apk.name
        signer = subprocess.check_output(['apksigner', 'verify', '--print-certs', str(apk)], text=True)
        certs = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F:]+)', signer)
        if len(certs) != 1 or certs[0].replace(':', '').lower() != expected_cert:
            raise ValueError('APK signer does not match the configured certificate')
        if (manifest.get('certificate_sha256', '').lower() != expected_cert or
                type(manifest.get('version_code')) is not int or manifest['version_code'] <= 0 or
                (not nightly and manifest.get('version_name') != tag[1:])):
            raise ValueError('Invalid F-Droid update metadata')
        apk.rename(repo / filename)
        key = root / 'index.p12'
        key.write_bytes(base64.b64decode(os.environ[f'{prefix}_INDEX_KEYSTORE_BASE64'], validate=True))
        key.chmod(0o600)
        config = {
            'repo_url': os.environ[f'{prefix}_REPO_URL'].rstrip('/'),
            'repo_name': 'FM-1 Flasher Nightly' if nightly else 'FM-1 Flasher',
            'repo_description': 'Developer builds of FM-1 Flasher' if nightly else 'Official FM-1 Flasher releases',
            'keystore': str(key),
            'repo_keyalias': os.environ[f'{prefix}_INDEX_KEY_ALIAS'],
            'keystorepass': {'env': f'{prefix}_INDEX_KEYSTORE_PASSWORD'},
            'keypass': {'env': f'{prefix}_INDEX_KEY_PASSWORD'},
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
        index = json.loads((repo / 'index-v2.json').read_text())
        versions = index['packages'][manifest['package']]['versions'].values()
        if not (repo / 'entry.jar').is_file() or not any(
                version['file']['name'] == f'/{filename}' and
                version['manifest']['versionCode'] == manifest['version_code'] and
                version['manifest']['versionName'] == manifest['version_name']
                for version in versions):
            raise ValueError('F-Droid index does not contain the selected APK version')
        if not nightly:
            index_certificate = subprocess.check_output([
                'keytool', '-exportcert', '-storetype', 'PKCS12', '-keystore', str(key),
                '-alias', os.environ[f'{prefix}_INDEX_KEY_ALIAS'], '-storepass:env',
                f'{prefix}_INDEX_KEYSTORE_PASSWORD',
            ])
            update = {field: manifest[field] for field in (
                'schema', 'variant', 'package', 'version_name', 'version_code', 'certificate_sha256')}
            update['repo_fingerprint'] = hashlib.sha256(index_certificate).hexdigest()
            (repo / 'update.json').write_text(json.dumps(update) + '\n')
        shutil.rmtree(repo / 'status', ignore_errors=True)
        destination = Path('build/site') / ('fdroid-nightly' if nightly else 'fdroid') / 'repo'
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copytree(repo, destination)


if __name__ == '__main__':
    main()
