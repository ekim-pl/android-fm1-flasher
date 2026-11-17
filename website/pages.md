# Project website on GitHub Pages

This website documents Android **0.10.1** preparation for Google Play.

The website, documentation and privacy policy are built from this repository. Every page is available in English, Polish and Simplified Chinese, including navigation, publishing instructions and license translations. The **Polski**, **English** and **简体中文** links switch to the same page in another language.

## First deployment

1. Push the project to GitHub, including `.github/workflows/pages.yml`, `website/` and `scripts/build_site.py`.
2. In **Settings → Pages → Build and deployment → Source**, choose **GitHub Actions**.
3. In **Actions → GitHub Pages → Run workflow**, select the default branch. Subsequent changes to that branch deploy automatically.
4. The published site is [ekim-pl.github.io/android-fm1-flasher](https://ekim-pl.github.io/android-fm1-flasher/). Forks usually use `https://LOGIN.github.io/REPOSITORY/`; check the deployment summary or **Settings → Pages**.

If the `github-pages` environment restricts deployment branches, allow the project's default branch. The workflow builds pull requests and other branches. It deploys from the default branch. It needs no application secrets or Google Play access.

## Languages and stable addresses

- `en/`: the English website, also served at the site root for existing links.
- `pl/`: the complete Polish website.
- `zh-CN/`: the complete Simplified Chinese website.

Each language has a home page, technical guide, About page, privacy policy, license, publication materials, Google Play setup guide and website deployment guide. The language switcher keeps the page you are reading. Navigation, accessibility labels, titles, descriptions and footers follow the selected language.

Existing links such as `documentation.html`, `about/pl.html` and `privacy/en/` remain available. The public policy URLs `privacy/pl/`, `privacy/en/` and `privacy/zh-CN/` can still be used in Play Console.

## Local build

Run from the project root:

```sh
python3 -m venv .venv-pages
.venv-pages/bin/pip install -r website/requirements.txt
.venv-pages/bin/python scripts/build_site.py
.venv-pages/bin/python scripts/check_site.py
```

Open `build/site/index.html`. Relative paths work under the repository prefix on Pages. The output contains only public documentation and website assets.

CI reads source links from `GITHUB_SERVER_URL`, `GITHUB_REPOSITORY` and `GITHUB_REF_NAME`. Set the same variables locally to include GitHub source and release links.

## Editing content

- `docs/user-guide.md`, `website/index.md` and `website/pages.md`: the English detailed guide, home page and deployment guide. Existing website links to `README.md` still resolve to the detailed guide.
- `publication/README.txt` and `publication/github-actions.txt`: English publication and Google Play guides.
- `website/pl/` and `website/zh-CN/`: complete website translations, including license and privacy-policy text.
- `about/README*.md`: localized website About documents; `about/app*.md` is bundled in the app.
- `PRIVACY_POLICY.md`: the English privacy policy. Keep website translations consistent with the static copies in `publication/privacy/` and app resources.
- `LICENSE.md`: the original English license. Website translations are provided for understanding; the original remains authoritative.
- `website/style.css`: shared styling.

Update all three website versions when changing shared content. Missing page translations fail the build instead of silently showing English. The link checker verifies each page's language and language-switching targets as well as files and anchors.

Repository documentation is in English. App About documents, app resources and store listings intentionally retain all supported languages.

## Sharing deployment with an F-Droid repository

GitHub Pages has one active site deployment per repository. If F-Droid publishing is added, include `fdroid/repo/` in `build/site/` before uploading the Pages artifact. The website and APK repository must deploy together: an APK-only workflow would replace the website, and a website-only deployment would remove the APK repository. The Pages workflow can include the signed repository when configured; see the repository setup guide in publication/fdroid/repository.md.
