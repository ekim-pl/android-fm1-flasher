#!/usr/bin/env python3
"""Publish verified APKs; resume only a draft bound to these exact artifacts."""
import hashlib
import json
import os
from pathlib import Path
import subprocess


def publish(tag, directory):
    variants = ['debug', 'fdroid'] if (directory / 'debug-manifest.json').exists() else ['fdroid']
    manifests = [json.loads((directory / f'{variant}-manifest.json').read_text())
                 for variant in variants]
    if len({(m['commit'], m['version_name'], m['version_code']) for m in manifests}) != 1:
        raise ValueError('APK variants do not describe the same release')
    provenance = json.dumps(manifests, sort_keys=True).encode()
    marker = '<!-- fm1-artifacts-sha256:' + hashlib.sha256(provenance).hexdigest() + ' -->'
    repo = os.environ['GH_REPO']
    releases = json.loads(subprocess.check_output(
        ['gh', 'api', '--paginate', '--slurp', f'repos/{repo}/releases'], text=True))
    existing = next((r for page in releases for r in page if r['tag_name'] == tag), None)
    if existing:
        if marker not in (existing['body'] or ''):
            raise ValueError('Existing release belongs to different or unrecorded artifacts')
        if not existing['draft']:
            print('These exact artifacts are already public; nothing to change')
            return
    else:
        notes = directory / 'release-notes.txt'
        description = 'F-Droid APK: no Firebase.\n'
        if 'debug' in variants:
            description += 'Developer APK: test Firebase.\n'
        notes.write_text(marker + f'\nFM1 Flasher {tag}\n\n' + description +
                         f'Source commit: {manifests[0]["commit"]}\n')
        subprocess.run(['gh', 'release', 'create', tag, '--verify-tag', '--draft',
                        '--title', tag, '--notes-file', str(notes)], check=True)
    assets = sorted(directory.glob('*.apk')) + sorted(directory.glob('*-manifest.json')) + [directory / 'SHA256SUMS']
    subprocess.run(['gh', 'release', 'upload', tag, *map(str, assets), '--clobber'], check=True)
    subprocess.run(['gh', 'release', 'edit', tag, '--draft=false'], check=True)


if __name__ == '__main__':
    try:
        publish(os.environ['RELEASE_TAG'], Path('dist'))
    except (ValueError, OSError, KeyError, subprocess.CalledProcessError) as error:
        raise SystemExit(f'Publication failed: {error}')
