#!/usr/bin/env python3
"""Check generated HTML links, assets and fragment identifiers, without a server."""

from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit

from build_site import ALIASES, LANGUAGES, PAGES

ROOT = Path(__file__).resolve().parent.parent / 'build/site'


class Document(HTMLParser):
    def __init__(self, path):
        super().__init__()
        self.ids = set()
        self.duplicate_ids = set()
        self.links = []
        self.html_language = None
        self.language = None
        self.page = None
        self.language_links = []
        self.language_labels = []
        self.active_label = None
        self.in_main = False
        self.main_text = []
        self.feed(path.read_text(encoding='utf-8'))

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if 'id' in attrs:
            if attrs['id'] in self.ids:
                self.duplicate_ids.add(attrs['id'])
            self.ids.add(attrs['id'])
        if tag == 'html':
            self.html_language = attrs.get('lang')
        elif tag == 'body':
            self.language, self.page = attrs.get('data-language'), attrs.get('data-page')
        elif tag == 'main':
            self.in_main = True
        elif tag == 'a' and 'data-language' in attrs:
            self.language_links.append(attrs)
            self.active_label = []
        for attribute in ('href', 'src'):
            if attribute in attrs:
                self.links.append(attrs[attribute])

    def handle_data(self, data):
        if self.in_main:
            self.main_text.append(data)
        if self.active_label is not None:
            self.active_label.append(data)

    def handle_endtag(self, tag):
        if tag == 'main':
            self.in_main = False
        elif tag == 'a' and self.active_label is not None:
            self.language_labels.append(''.join(self.active_label))
            self.active_label = None


def check():
    documents = {path: Document(path) for path in ROOT.rglob('*.html')}
    expected = {ROOT / language / page: (language, page)
                for language in LANGUAGES for page in PAGES}
    expected.update({ROOT / path: value for path, value in ALIASES.items()})
    assert documents.keys() == expected.keys(), 'Missing or unexpected generated pages'
    errors = []
    if (documents[ROOT / 'index.html'].html_language, documents[ROOT / 'index.html'].language) != ('en', 'en'):
        errors.append('index.html: default language must be English')
    for path, document in documents.items():
        language, page = expected[path]
        prefix = str(path.relative_to(ROOT))
        if (document.html_language != LANGUAGES[language]['html']
                or document.language != language or document.page != page):
            errors.append(f'{prefix}: wrong page language or identity')
        if document.duplicate_ids:
            errors.append(f'{prefix}: duplicate anchors: {document.duplicate_ids}')
        if not ''.join(document.main_text).strip():
            errors.append(f'{prefix}: empty translated content')
        if [link.get('aria-label') for link in document.language_links] != [LANGUAGES[code]['name'] for code in LANGUAGES]:
            errors.append(f'{prefix}: incorrect language menu labels')
        selected = [link['data-language'] for link in document.language_links
                    if link.get('aria-current') == 'true']
        if selected != [language]:
            errors.append(f'{prefix}: wrong selected language')
        if len(document.language_links) != len(LANGUAGES):
            errors.append(f'{prefix}: missing language links')
        for link in document.language_links:
            code = link['data-language']
            target = (path.parent / unquote(urlsplit(link['href']).path)).resolve()
            if code not in LANGUAGES or target != ROOT / code / page:
                errors.append(f'{prefix}: language switch does not preserve page: {link["href"]}')
            elif link.get('hreflang') != LANGUAGES[code]['html']:
                errors.append(f'{prefix}: wrong language hint')
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
    for page in PAGES:
        bodies = {''.join(documents[ROOT / language / page].main_text).strip()
                  for language in LANGUAGES}
        if len(bodies) != len(LANGUAGES):
            errors.append(f'{page}: repeated content instead of a translation')
    assert not errors, '\n'.join(errors)
    print(f'Checked {len(documents)} HTML pages: links, anchors, translations and language switching')


if __name__ == '__main__':
    check()
