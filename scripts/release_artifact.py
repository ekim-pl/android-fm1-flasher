#!/usr/bin/env python3
"""Record validated Android builds and retrieve them without rebuilding."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

from release_fixtures import evidence, fixtures
from release_bundle import validate as validate_bundle, VERSION as BUNDLETOOL_VERSION, SHA256 as BUNDLETOOL_SHA256
from release_platform import require_platform

PACKAGE = 'pl.ekim.fm1_flasher'
PRODUCERS = {
    'debug': ('android-debug.yml', 'Build debug APK'),
    'fdroid': ('fdroid-release.yml', 'Build F-Droid APK'),
    'play': ('google-play.yml', 'Build Google Play AAB'),
}


def output(*args):
    return subprocess.check_output(args, text=True).strip()


def api(endpoint):
    return json.loads(output('gh', 'api', endpoint))


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def fingerprint(text):
    matches = re.findall(r'(?:certificate SHA-256 digest:|SHA256:)\s*([0-9a-fA-F:]+)', text)
    values = {value.replace(':', '').upper() for value in matches}
    if len(values) != 1 or not re.fullmatch(r'[0-9A-F]{64}', next(iter(values), '')):
        raise ValueError('Expected exactly one signing certificate')
    return values.pop()


def certificate(path, variant):
    if variant == 'play':
        # jarsigner fails for corrupt signatures; keytool exposes the signer certificate.
        subprocess.run(['jarsigner', '-verify', str(path)], check=True, capture_output=True)
        return fingerprint(output('keytool', '-J-Duser.language=en', '-printcert', '-jarfile', str(path)))
    signer = str(Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0/apksigner')
    return fingerprint(output(signer, 'verify', '--print-certs', str(path)))


def create(variant, path, directory):
    gradle = Path('app/build.gradle').read_text()
    package = re.search(r"applicationId\s+'([^']+)'", gradle).group(1)
    if package != PACKAGE:
        raise ValueError('Source applicationId does not match FM1 Flasher')
    name = re.search(r"versionName\s+'([^']+)'", gradle).group(1)
    code = int(re.search(r'versionCode\s+(\d+)', gradle).group(1))
    tag = os.environ.get('FM1_RELEASE_TAG', '')
    if tag and tag != f'v{name}':
        raise ValueError('Tag does not match versionName')
    cert = certificate(path, variant)
    expected = os.environ['EXPECTED_CERT_SHA256'].replace(':', '').strip().upper()
    if cert != expected:
        raise ValueError('Signing certificate does not match pinned fingerprint')
    if variant == 'play':
        minimum = int(re.search(r'minSdk\s+(\d+)', gradle).group(1))
        target_sdk = int(re.search(r'targetSdk\s+(\d+)', gradle).group(1))
        validate_bundle(path, package, name, code, minimum, target_sdk)
    else:
        aapt = str(Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0/aapt')
        badging = output(aapt, 'dump', 'badging', str(path))
        identity = f"package: name='{PACKAGE}' versionCode='{code}' versionName='{name}'"
        if identity not in badging:
            raise ValueError('APK identity does not match source')
    directory.mkdir(parents=True, exist_ok=True)
    extension = 'aab' if variant == 'play' else 'apk'
    filename = f'fm1-flasher-{tag or name}-{variant}.{extension}'
    target = directory / filename
    target.write_bytes(path.read_bytes())
    manifest = dict(schema=1, variant=variant, package=PACKAGE, version_name=name,
                    version_code=code, tag=tag, commit=output('git', 'rev-parse', 'HEAD'),
                    run_id=os.environ['GITHUB_RUN_ID'], run_attempt=int(os.environ['GITHUB_RUN_ATTEMPT']),
                    file=filename, sha256=digest(target),
                    certificate_sha256=cert)
    if variant == 'play':
        manifest['bundletool'] = dict(version=BUNDLETOOL_VERSION, sha256=BUNDLETOOL_SHA256,
                                      min_sdk=minimum, target_sdk=target_sdk)
    if variant in ('fdroid', 'play') and tag:
        manifest['integration'] = evidence()
    (directory / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    (directory / 'SHA256SUMS').write_text(f'{manifest["sha256"]}  {filename}\n')
    print(f'Validated {filename} at {manifest["commit"]}')


def verify(directory, variant, tag, commit, run_id):
    manifest = json.loads((directory / 'manifest.json').read_text())
    expected = dict(schema=1, variant=variant, package=PACKAGE, tag=tag, commit=commit, run_id=run_id)
    if any(manifest.get(key) != value for key, value in expected.items()):
        raise ValueError('Artifact provenance does not match requested source')
    if not re.fullmatch(r'v\d+\.\d+\.\d+', tag) or tag != 'v' + manifest['version_name']:
        raise ValueError('Expected stable version matching tag')
    if type(manifest.get('version_code')) is not int or manifest['version_code'] <= 0:
        raise ValueError('Invalid versionCode')
    if type(manifest.get('run_attempt')) is not int or manifest['run_attempt'] <= 0:
        raise ValueError('Invalid source run attempt')
    if not re.fullmatch(r'[0-9A-F]{64}', manifest.get('certificate_sha256', '')):
        raise ValueError('Invalid certificate fingerprint')
    if variant in ('fdroid', 'play'):
        integration = manifest.get('integration', {})
        expected_fixtures = {name: item['sha256'] for name, item in fixtures().items()}
        if integration.get('commit') != commit or integration.get('fixtures') != expected_fixtures:
            raise ValueError('Missing or mismatched production integration evidence')
    extension = 'aab' if variant == 'play' else 'apk'
    filename = f'fm1-flasher-{tag}-{variant}.{extension}'
    if manifest.get('file') != filename:
        raise ValueError('Unexpected artifact filename')
    path = directory / filename
    if path.is_symlink() or not path.is_file() or digest(path) != manifest.get('sha256'):
        raise ValueError('Artifact checksum mismatch or missing file')
    if variant == 'play':
        tool = manifest.get('bundletool', {})
        if (tool.get('version') != BUNDLETOOL_VERSION or tool.get('sha256') != BUNDLETOOL_SHA256 or
                type(tool.get('min_sdk')) is not int or type(tool.get('target_sdk')) is not int or
                not 1 <= tool['min_sdk'] <= tool['target_sdk']):
            raise ValueError('Missing pinned AAB manifest validation provenance; rebuild required')
    return manifest


def fetch(variant, run_id, tag, commit, directory):
    if not re.fullmatch(r'\d+', run_id):
        raise ValueError('run_id must be numeric')
    repo = os.environ['GH_REPO']
    run = api(f'repos/{repo}/actions/runs/{run_id}')
    workflow, job_name = PRODUCERS[variant]
    if run['path'] not in (f'.github/workflows/{workflow}', '.github/workflows/android-release.yml'):
        raise ValueError('Artifact source workflow is not allowed')
    if run['event'] != 'workflow_dispatch':
        raise ValueError('Expected a manually dispatched release/build workflow')
    if run['head_repository']['full_name'] != repo:
        raise ValueError('Artifact source repository mismatch')
    # A combined release may still be running while its publish job executes.
    jobs = []
    page = 1
    while True:
        batch = api(f'repos/{repo}/actions/runs/{run_id}/jobs?filter=all&per_page=100&page={page}')['jobs']
        jobs.extend(batch)
        if len(batch) < 100:
            break
        page += 1
    matching = [job for job in jobs if job['name'] == job_name or job['name'].endswith(' / ' + job_name)]
    successful_attempts = {job['run_attempt'] for job in matching if job['conclusion'] == 'success'}
    if not successful_attempts:
        raise ValueError('Source build job must have completed successfully')
    subprocess.run(['gh', 'run', 'download', run_id, '--name', f'android-{variant}-{tag}',
                    '--dir', str(directory)], check=True)
    manifest = verify(directory, variant, tag, commit, run_id)
    if manifest['run_attempt'] not in successful_attempts:
        raise ValueError('Artifact was not produced by a successful build attempt')
    require_platform(api, repo, commit)
    if variant == 'play':
        tool = manifest['bundletool']
        validate_bundle(directory / manifest['file'], PACKAGE, manifest['version_name'],
                        manifest['version_code'], tool['min_sdk'], tool['target_sdk'])
    return manifest


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    make = sub.add_parser('create')
    make.add_argument('variant', choices=PRODUCERS)
    make.add_argument('file', type=Path)
    make.add_argument('directory', type=Path)
    get = sub.add_parser('fetch')
    get.add_argument('variant', choices=PRODUCERS)
    get.add_argument('run_id')
    get.add_argument('tag')
    get.add_argument('commit')
    get.add_argument('directory', type=Path)
    args = parser.parse_args()
    try:
        if args.command == 'create':
            create(args.variant, args.file, args.directory)
        else:
            fetch(args.variant, args.run_id, args.tag, args.commit, args.directory)
    except (OSError, ValueError, KeyError, subprocess.CalledProcessError) as error:
        parser.exit(1, f'Artifact validation failed: {error}\n')
