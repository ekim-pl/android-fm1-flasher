#!/usr/bin/env python3
"""Check pinned JVM artifacts, site dependencies and Ruby gems against OSV."""
import json
from pathlib import Path
import re
import urllib.request
import xml.etree.ElementTree as ET


def packages():
    root = Path(__file__).resolve().parent.parent
    metadata = ET.parse(root / 'gradle/verification-metadata.xml')
    found = {('Maven', f'{c.attrib["group"]}:{c.attrib["name"]}', c.attrib['version'])
             for c in metadata.iter() if c.tag.endswith('}component')
             and any(a.get('name', '').endswith(('.jar', '.aar')) for a in c)}
    found.update(('PyPI', name, version) for name, version in
                 re.findall(r'^([\w.-]+)==([^\s]+)$', (root / 'website/requirements.txt').read_text(), re.M))
    found.update(('RubyGems', name, version) for name, version in
                 re.findall(r'^    ([\w.-]+) \(([^ ,)]+)\)', (root / 'Gemfile.lock').read_text(), re.M))
    return sorted(found)


if __name__ == '__main__':
    pinned = packages()
    results = []
    for at in range(0, len(pinned), 100):
        batch = pinned[at:at + 100]
        query = {'queries': [{'package': {'ecosystem': e, 'name': n}, 'version': v} for e, n, v in batch]}
        request = urllib.request.Request('https://api.osv.dev/v1/querybatch',
                                        data=json.dumps(query).encode(), headers={'Content-Type': 'application/json'})
        with urllib.request.urlopen(request, timeout=30) as response:
            answers = json.load(response)['results']
        if len(answers) != len(batch):
            raise ValueError('Incomplete vulnerability response')
        for package, answer in zip(batch, answers):
            for issue in answer.get('vulns', []):
                results.append((package, issue['id']))
    for package, issue in results:
        print(f'{package}: https://osv.dev/vulnerability/{issue}')
    print(f'Checked {len(pinned)} package versions; {len(results)} advisory matches')
    raise SystemExit(1 if results else 0)
