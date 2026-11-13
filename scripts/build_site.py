#!/usr/bin/env python3
"""Build the public documentation without copying application or signing files."""

import os
from pathlib import Path
import posixpath
import shutil
from html import escape
from urllib.parse import quote, urlsplit

import markdown
from markdown.treeprocessors import Treeprocessor
from markdown.extensions import Extension


ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'build/site'
PAGES = {
    'website/index.md': ('index.html', 'pl', 'FM-1 Flasher — firmware przez USB MIDI'),
    'website/pages.md': ('pages.html', 'pl', 'Publikacja strony'),
    'README.md': ('documentation.html', 'pl', 'Dokumentacja'),
    'about/README.pl.md': ('about/pl.html', 'pl', 'O aplikacji'),
    'about/README.md': ('about/en.html', 'en', 'About the app'),
    'about/README.zh-Hans.md': ('about/zh-Hans.html', 'zh-Hans', '关于应用'),
    'LICENSE.md': ('license.html', 'en', 'MIT License'),
    'PRIVACY_POLICY.md': ('privacy-policy.html', 'en', 'Privacy policy'),
    'publication/README.txt': ('publication.html', 'pl', 'Materiały publikacyjne'),
    'publication/github-actions.txt': ('github-actions.html', 'pl', 'GitHub Actions i Google Play'),
}
ASSETS = {
    'assets/icon.svg': 'assets/icon.svg',
    'website/style.css': 'assets/style.css',
    'third_party/commonmark-LICENSE.txt': 'licenses/commonmark.txt',
}
repository = os.environ.get('GITHUB_REPOSITORY', '')
server = os.environ.get('GITHUB_SERVER_URL', 'https://github.com').rstrip('/')
ref = os.environ.get('GITHUB_REF_NAME', 'main')
repository_url = f'{server}/{repository}' if repository else ''


def relative(target, output):
    return posixpath.relpath(target, posixpath.dirname(output) or '.')


class Links(Treeprocessor):
    def run(self, tree):
        for element in tree.iter('a'):
            href = element.get('href', '')
            url = urlsplit(href)
            if url.scheme or url.netloc or not url.path:
                continue
            source = posixpath.normpath(posixpath.join(posixpath.dirname(self.source), url.path))
            target = PAGES.get(source, (ASSETS.get(source),))[0]
            if source == 'about':
                target = 'about/pl.html'
            elif source == 'publication':
                target = 'publication.html'
            elif source.startswith('publication/privacy/'):
                target = source.removeprefix('publication/')
            if target:
                element.set('href', relative(target, self.output)
                            + (f'?{url.query}' if url.query else '')
                            + (f'#{url.fragment}' if url.fragment else ''))
            elif repository_url:
                kind = 'tree' if (ROOT / source).is_dir() else 'blob'
                element.set('href', f'{repository_url}/{kind}/{quote(ref, safe="")}/{quote(source)}'
                            + (f'#{url.fragment}' if url.fragment else ''))
            else:
                # Local builds without a remote keep source-only references readable.
                element.tag = 'span'
                element.attrib.clear()


class SiteLinks(Extension):
    def __init__(self, source, output):
        super().__init__()
        self.source, self.output = source, output

    def extendMarkdown(self, md):
        links = Links(md)
        links.source, links.output = self.source, self.output
        md.treeprocessors.register(links, 'site_links', 5)


def build():
    OUT.mkdir(parents=True, exist_ok=True)
    for source, target in ASSETS.items():
        destination = OUT / target
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(ROOT / source, destination)
    shutil.copytree(ROOT / 'publication/privacy', OUT / 'privacy', dirs_exist_ok=True)
    for source, (output, language, title) in PAGES.items():
        content = (ROOT / source).read_text(encoding='utf-8')
        if source.endswith('.txt'):
            body = f'<h1>{escape(title)}</h1><pre>{escape(content)}</pre>'
        else:
            body = markdown.markdown(content, extensions=[
                'fenced_code', 'tables', 'toc', SiteLinks(source, output),
            ])
        nav = ''.join(f'<a href="{relative(target, output)}">{label}</a>' for target, label in [
            ('documentation.html', 'Dokumentacja'), ('about/en.html', 'English'),
            ('about/zh-Hans.html', '简体中文'), ('privacy/index.html', 'Prywatność'),
        ])
        if repository_url:
            nav += f'<a href="{escape(repository_url, quote=True)}/releases">Wydania</a>'
            nav += f'<a href="{escape(repository_url, quote=True)}">GitHub</a>'
        page = f'''<!doctype html>
<html lang="{language}">
<head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>{escape(title)} | FM-1 Flasher</title>
<meta name="description" content="FM-1 Flasher: aplikacja Android do aktualizacji M-VAVE FM-1 przez USB MIDI. Instrukcja, dokumentacja i prywatność.">
<link rel="icon" href="{relative('assets/icon.svg', output)}" type="image/svg+xml">
<link rel="stylesheet" href="{relative('assets/style.css', output)}"></head>
<body><a class="skip" href="#content">Przejdź do treści / Skip to content</a>
<header><a class="brand" href="{relative('index.html', output)}"><img src="{relative('assets/icon.svg', output)}" alt="" width="40" height="40">FM-1 Flasher</a>
<nav aria-label="Menu">{nav}</nav></header>
<main id="content">{body}</main>
<footer>Niezależny projekt · Independent project · MIT · <a href="{relative('license.html', output)}">License</a></footer></body></html>
'''
        destination = OUT / output
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(page, encoding='utf-8')
    (OUT / '.nojekyll').touch()
    print(f'Built {len(PAGES)} documentation pages in {OUT}')


if __name__ == '__main__':
    build()
