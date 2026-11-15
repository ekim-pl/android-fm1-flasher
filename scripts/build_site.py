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
LANGUAGES = {
    'en': {
        'html': 'en', 'name': 'English', 'skip': 'Skip to content',
        'languages': 'Choose language', 'navigation': 'Project navigation',
        'guide': 'Guide', 'about': 'About', 'privacy': 'Privacy',
        'releases': 'Releases', 'license': 'License', 'independent': 'An Android app for browsing firmware and updating the M-VAVE FM-1 over USB MIDI.',
        'description': 'FM-1 Flasher for Android: M-VAVE FM-1 firmware updates over USB MIDI. Guides, documentation and privacy.',
        'titles': {
            'index.html': 'FM-1 Flasher — firmware over USB MIDI',
            'documentation.html': 'Technical guide', 'about.html': 'About the app',
            'privacy.html': 'Privacy policy', 'license.html': 'MIT License',
            'publication.html': 'Publication materials',
            'github-actions.html': 'GitHub Actions and Google Play',
            'pages.html': 'Website deployment',
            'commonmark-license.html': 'CommonMark — BSD-2-Clause license',
        },
    },
    'pl': {
        'html': 'pl', 'name': 'Polski', 'skip': 'Przejdź do treści',
        'languages': 'Wybierz język', 'navigation': 'Nawigacja projektu',
        'guide': 'Instrukcja', 'about': 'O aplikacji', 'privacy': 'Prywatność',
        'releases': 'Wydania', 'license': 'Licencja', 'independent': 'Aplikacja na Androida do przeglądania firmware i aktualizowania M-VAVE FM-1 przez USB MIDI.',
        'description': 'FM-1 Flasher dla Androida: aktualizacja M-VAVE FM-1 przez USB MIDI. Instrukcje, dokumentacja i prywatność.',
        'titles': {
            'index.html': 'FM-1 Flasher — firmware przez USB MIDI',
            'documentation.html': 'Instrukcja techniczna', 'about.html': 'O aplikacji',
            'privacy.html': 'Polityka prywatności', 'license.html': 'Licencja MIT',
            'publication.html': 'Materiały publikacyjne',
            'github-actions.html': 'GitHub Actions i Google Play',
            'pages.html': 'Publikacja witryny',
            'commonmark-license.html': 'CommonMark — licencja BSD-2-Clause',
        },
    },
    'zh-CN': {
        'html': 'zh-Hans', 'name': '简体中文', 'skip': '跳转到正文',
        'languages': '选择语言', 'navigation': '项目导航',
        'guide': '使用指南', 'about': '关于应用', 'privacy': '隐私',
        'releases': '发布版本', 'license': '许可证', 'independent': '一款用于浏览固件并通过 USB MIDI 更新 M-VAVE FM-1 的 Android 应用。',
        'description': 'Android 版 FM-1 Flasher：通过 USB MIDI 更新 M-VAVE FM-1 固件。使用指南、技术文档与隐私政策。',
        'titles': {
            'index.html': 'FM-1 Flasher — USB MIDI 固件更新',
            'documentation.html': '技术指南', 'about.html': '关于应用',
            'privacy.html': '隐私政策', 'license.html': 'MIT 许可证',
            'publication.html': '发布材料',
            'github-actions.html': 'GitHub Actions 与 Google Play',
            'pages.html': '网站部署',
            'commonmark-license.html': 'CommonMark — BSD-2-Clause 许可证',
        },
    },
}
PAGES = {
    'index.html': {'en': 'website/index.md', 'pl': 'website/pl/index.md', 'zh-CN': 'website/zh-CN/index.md'},
    'documentation.html': {'en': 'README.md', 'pl': 'website/pl/documentation.md', 'zh-CN': 'website/zh-CN/documentation.md'},
    'about.html': {'en': 'about/README.md', 'pl': 'about/README.pl.md', 'zh-CN': 'about/README.zh-Hans.md'},
    'privacy.html': {'en': 'PRIVACY_POLICY.md', 'pl': 'website/pl/privacy.md', 'zh-CN': 'website/zh-CN/privacy.md'},
    'license.html': {'en': 'LICENSE.md', 'pl': 'website/pl/license.md', 'zh-CN': 'website/zh-CN/license.md'},
    'publication.html': {'en': 'publication/README.txt', 'pl': 'website/pl/publication.txt', 'zh-CN': 'website/zh-CN/publication.txt'},
    'github-actions.html': {'en': 'publication/github-actions.txt', 'pl': 'website/pl/github-actions.txt', 'zh-CN': 'website/zh-CN/github-actions.txt'},
    'pages.html': {'en': 'website/pages.md', 'pl': 'website/pl/pages.md', 'zh-CN': 'website/zh-CN/pages.md'},
    'commonmark-license.html': {'en': 'third_party/commonmark-LICENSE.txt', 'pl': 'website/pl/commonmark-license.md', 'zh-CN': 'website/zh-CN/commonmark-license.md'},
}
SOURCE_PAGES = {source: page for page, translations in PAGES.items() for source in translations.values()}
EXPLICIT_LANGUAGES = {
    source: language
    for page in ('about.html', 'documentation.html')
    for language, source in PAGES[page].items()
}
ALIASES = {page: ('en', page) for page in PAGES}
ALIASES.update({
    'about/en.html': ('en', 'about.html'),
    'about/pl.html': ('pl', 'about.html'),
    'about/zh-Hans.html': ('zh-CN', 'about.html'),
    'privacy-policy.html': ('en', 'privacy.html'),
    'privacy/index.html': ('en', 'privacy.html'),
    **{f'privacy/{language}/index.html': (language, 'privacy.html') for language in LANGUAGES},
})
ASSETS = {
    'assets/icon.svg': 'assets/icon.svg',
    'website/style.css': 'assets/style.css',
    **{f'website/icons/{name}': f'assets/icons/{name}' for name in ('ekim.webp', 'github.svg', 'youtube.svg', 'instagram.svg', 'discord.svg')},
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
            page = SOURCE_PAGES.get(source)
            language = EXPLICIT_LANGUAGES.get(source, self.language)
            if source == 'about':
                page = 'about.html'
            elif source == 'publication':
                page = 'publication.html'
            elif source.startswith('publication/privacy/'):
                page = 'privacy.html'
                parts = source.split('/')
                if parts[2] in LANGUAGES:
                    language = parts[2]
            target = f'{language}/{page}' if page else ASSETS.get(source)
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
    def __init__(self, source, output, language):
        super().__init__()
        self.source, self.output, self.language = source, output, language

    def extendMarkdown(self, md):
        links = Links(md)
        links.source, links.output = self.source, self.output
        links.language = self.language
        md.treeprocessors.register(links, 'site_links', 5)


def write_page(language, page, output):
    ui = LANGUAGES[language]
    source = PAGES[page][language]
    title = ui['titles'][page]
    content = (ROOT / source).read_text(encoding='utf-8')
    if source.endswith('.txt'):
        body = f'<h1>{escape(title)}</h1><pre>{escape(content)}</pre>'
    else:
        body = markdown.markdown(content, extensions=[
            'fenced_code', 'tables', 'toc', SiteLinks(source, output, language),
        ])
    languages = ''
    alternates = ''
    for code in ('en', 'pl', 'zh-CN'):
        target = relative(f'{code}/{page}', output)
        html_language = LANGUAGES[code]['html']
        current = ' aria-current="true"' if code == language else ''
        languages += (f'<a data-language="{code}" href="{target}" lang="{html_language}" '
                      f'hreflang="{html_language}"{current}>{LANGUAGES[code]["name"]}</a>')
        alternates += f'<link rel="alternate" hreflang="{html_language}" href="{target}">\n'
    nav = ''.join(f'<a href="{relative(f"{language}/{target}", output)}">{ui[label]}</a>'
                  for target, label in [('documentation.html', 'guide'), ('about.html', 'about'),
                                        ('privacy.html', 'privacy')])
    if repository_url:
        nav += f'<a href="{escape(repository_url, quote=True)}/releases">{ui["releases"]}</a>'
        nav += f'<a href="{escape(repository_url, quote=True)}">GitHub</a>'
    html = f'''<!doctype html>
<html lang="{ui['html']}">
<head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>{escape(title)} | FM-1 Flasher</title>
<meta name="description" content="{escape(ui['description'], quote=True)}">
{alternates}<link rel="canonical" href="{relative(f'{language}/{page}', output)}">
<link rel="icon" href="{relative('assets/icon.svg', output)}" type="image/svg+xml">
<link rel="stylesheet" href="{relative('assets/style.css', output)}"></head>
<body data-language="{language}" data-page="{page}"><a class="skip" href="#content">{ui['skip']}</a>
<header><a class="brand" href="{relative(f'{language}/index.html', output)}"><img src="{relative('assets/icon.svg', output)}" alt="" width="48" height="48"><span class="brand-name">FM-1 <span>Flasher</span></span></a>
<nav class="languages" aria-label="{ui['languages']}">{languages}</nav>
<nav aria-label="{ui['navigation']}">{nav}</nav></header>
<main id="content">{body}</main>
<footer><span class="footer-brand"><img src="{relative('assets/icon.svg', output)}" alt="" width="28" height="28">FM-1 Flasher</span><p>{ui['independent']}</p><a href="{relative(f'{language}/license.html', output)}">MIT · {ui['license']}</a>
<nav class="footer-links" aria-label="Social links"><a class="personal-site" href="https://ekim-pl.github.io/" aria-label="Ekim"><img class="ekim-logo" src="{relative('assets/icons/ekim.webp', output)}" alt="" width="70" height="28"></a> <a href="https://github.com/ekim-pl" aria-label="GitHub"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 19c-4 1-4-2-6-2m12 4v-3c0-1 .1-1.5-.5-2 3-.3 6-1.5 6-6a4.5 4.5 0 0 0-1.2-3.1A4 4 0 0 0 19.2 3S18 2.6 15 4.5a14 14 0 0 0-6 0C6 2.6 4.8 3 4.8 3a4 4 0 0 0-.1 3.9A4.5 4.5 0 0 0 3.5 10c0 4.5 3 5.7 6 6-.5.5-.6 1-.5 2v3"/></svg></a> <a href="https://www.youtube.com/@ekim-pl" aria-label="YouTube"><svg viewBox="0 0 24 24" aria-hidden="true"><rect x="2" y="5" width="20" height="14" rx="4"/><path d="m10 9 5 3-5 3Z"/></svg></a> <a href="https://www.instagram.com/ekimpl/" aria-label="Instagram"><svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="3" width="18" height="18" rx="5"/><circle cx="12" cy="12" r="4"/><circle cx="17.5" cy="6.5" r=".8" fill="currentColor"/></svg></a> <a href="https://www.tiktok.com/@ekim_pl" aria-label="TikTok"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M14 3h3a5 5 0 0 0 5 5v3a8 8 0 0 1-5-1.7V16a6 6 0 1 1-6-6v3a3 3 0 1 0 3 3Z"/></svg></a> <a href="https://discord.com/users/1422549758785880215" aria-label="Discord"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7 5a19 19 0 0 1 10 0l1-2m-11 2L6 3M7 5C4 6 2 11 2 17l5 3 2-3m8-12c3 1 5 6 5 12l-5 3-2-3M6 16a15 15 0 0 0 12 0"/><circle cx="8" cy="12" r="1"/><circle cx="16" cy="12" r="1"/></svg></a></nav></footer></body></html>
'''
    destination = OUT / output
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(html, encoding='utf-8')


def build():
    for page, translations in PAGES.items():
        assert translations.keys() == LANGUAGES.keys(), f'Missing translation: {page}'
        for source in translations.values():
            assert (ROOT / source).is_file(), f'Missing translation source: {source}'
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    for source, target in ASSETS.items():
        destination = OUT / target
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(ROOT / source, destination)
    for page in PAGES:
        for language in LANGUAGES:
            write_page(language, page, f'{language}/{page}')
    for output, (language, page) in ALIASES.items():
        write_page(language, page, output)
    (OUT / '.nojekyll').touch()
    print(f'Built {len(PAGES)} pages in {len(LANGUAGES)} languages and {len(ALIASES)} compatibility pages in {OUT}')


if __name__ == '__main__':
    build()
