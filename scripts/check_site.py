#!/usr/bin/env python3
"""Check generated HTML links, assets and fragment identifiers, without a server."""

from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parent.parent / 'build/site'


class Document(HTMLParser):
    def __init__(self, path):
        super().__init__()
        self.ids = set()
        self.links = []
        self.feed(path.read_text(encoding='utf-8'))

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if 'id' in attrs:
            self.ids.add(attrs['id'])
        for attribute in ('href', 'src'):
            if attribute in attrs:
                self.links.append(attrs[attribute])


def check():
    documents = {path: Document(path) for path in ROOT.rglob('*.html')}
    assert ROOT / 'index.html' in documents, 'Missing home page'
    errors = []
    for path, document in documents.items():
        for link in document.links:
            url = urlsplit(link)
            if url.scheme or url.netloc:
                continue
            target = (path.parent / unquote(url.path)).resolve() if url.path else path
            if not target.is_relative_to(ROOT.resolve()):
                errors.append(f'{path.relative_to(ROOT)}: link escapes site: {link}')
                continue
            if target.is_dir():
                target /= 'index.html'
            if not target.is_file():
                errors.append(f'{path.relative_to(ROOT)}: missing {link}')
            elif url.fragment and target in documents and unquote(url.fragment) not in documents[target].ids:
                errors.append(f'{path.relative_to(ROOT)}: missing anchor {link}')
    assert not errors, '\n'.join(errors)
    print(f'Checked links and anchors in {len(documents)} HTML pages')


if __name__ == '__main__':
    check()
