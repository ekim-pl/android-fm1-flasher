"""Inspect AAB bytes with the pinned upstream bundletool release."""
import hashlib
from pathlib import Path
import subprocess
import urllib.request
import xml.etree.ElementTree as ET

from release_fixtures import HttpsRedirect

VERSION = '1.18.3'
# GitHub release asset digest: google/bundletool, tag 1.18.3.
SHA256 = 'a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29'
URL = f'https://github.com/google/bundletool/releases/download/{VERSION}/bundletool-all-{VERSION}.jar'
CACHE = Path.home() / '.cache/fm1-release' / f'bundletool-{VERSION}.jar'


def bundletool(path=CACHE):
    if path.exists():
        raw = path.read_bytes()
    else:
        with urllib.request.build_opener(HttpsRedirect()).open(URL, timeout=60) as response:
            raw = response.read(64 * 1024 * 1024 + 1)
    if hashlib.sha256(raw).hexdigest() != SHA256:
        raise ValueError('bundletool SHA-256 mismatch')
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.exists():
        path.write_bytes(raw)
    return path


def validate_manifest(xml, package, name, code, minimum, target):
    root = ET.fromstring(xml)
    android = '{http://schemas.android.com/apk/res/android}'
    sdk = root.findall('uses-sdk')
    if (root.tag != 'manifest' or root.get('package') != package or
            root.get(android + 'versionName') != name or
            root.get(android + 'versionCode') != str(code) or
            root.get(android + 'versionCodeMajor', '0') != '0' or
            len(sdk) != 1 or sdk[0].get(android + 'minSdkVersion') != str(minimum) or
            sdk[0].get(android + 'targetSdkVersion') != str(target) or
            sdk[0].get(android + 'maxSdkVersion') is not None):
        raise ValueError('AAB base manifest identity or SDK does not match source')


def validate(path, package, name, code, minimum, target):
    command = ['java', '-jar', str(bundletool())]
    subprocess.run(command + ['validate', '--bundle=' + str(path)], check=True, capture_output=True)
    xml = subprocess.check_output(command + ['dump', 'manifest', '--module=base',
                                             '--bundle=' + str(path)], text=True)
    try:
        validate_manifest(xml, package, name, code, minimum, target)
    except ET.ParseError as error:
        raise ValueError('Invalid AAB base manifest XML') from error
