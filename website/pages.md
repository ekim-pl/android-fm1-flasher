# Strona projektu na GitHub Pages

Strona informacyjna, dokumentacja oraz polityka prywatności powstają ze źródeł w tym repozytorium. Zmiana `README.md` lub plików `about/` aktualizuje również dokumentację internetową.

## Pierwsza publikacja

1. Wyślij projekt do repozytorium GitHub wraz z `.github/workflows/pages.yml`, `website/` i `scripts/build_site.py`.
2. W **Settings → Pages → Build and deployment → Source** wybierz **GitHub Actions**.
3. W **Actions → GitHub Pages → Run workflow** wybierz domyślną gałąź. Kolejne zmiany w tej gałęzi publikują stronę automatycznie.
4. Adres strony znajdziesz w podsumowaniu wdrożenia oraz w **Settings → Pages**. Zwykle ma postać `https://LOGIN.github.io/NAZWA-REPOZYTORIUM/`.

Jeżeli środowisko `github-pages` ogranicza dozwolone gałęzie, dopuść w nim domyślną gałąź projektu. Workflow buduje stronę także dla pull requestów i pozostałych gałęzi, ale publikuje tylko z domyślnej gałęzi. Nie wymaga sekretów aplikacji ani dostępu do Google Play.

Polityka prywatności jest dostępna pod `privacy/`, z wersjami językowymi pod `privacy/pl/`, `privacy/en/` i `privacy/zh-CN/`. Adres odpowiedniej wersji możesz podać w Play Console.

## Budowanie lokalne

Uruchom z katalogu głównego projektu:

```sh
python3 -m venv .venv-pages
.venv-pages/bin/pip install -r website/requirements.txt
.venv-pages/bin/python scripts/build_site.py
```

Otwórz `build/site/index.html`. Witryna używa względnych ścieżek i działa także pod prefiksem repozytorium na Pages. Wynik budowania zawiera tylko publiczną dokumentację i zasoby strony.

W CI adres źródeł jest pobierany z `GITHUB_SERVER_URL`, `GITHUB_REPOSITORY` i `GITHUB_REF_NAME`. Przy lokalnym budowaniu możesz ustawić te same zmienne, aby dodać odnośniki do źródeł i wydań na GitHubie.

## Edycja treści

- `website/index.md`: strona główna.
- `README.md`: pełna instrukcja i dokumentacja techniczna.
- `about/README*.md`: opisy aplikacji po polsku, angielsku i chińsku.
- `publication/privacy/`: gotowe strony polityki prywatności.
- `website/style.css`: wspólny wygląd dokumentacji.

## Wspólna publikacja z repozytorium F-Droid

GitHub Pages ma jedno aktywne wdrożenie strony dla repozytorium. Po dodaniu publikacji F-Droid należy dołączać jego katalog `fdroid/repo/` do `build/site/` przed wysłaniem artefaktu Pages. Strona i repozytorium APK muszą być publikowane razem; osobny workflow wdrażający wyłącznie APK zastąpiłby stronę, a wdrożenie samej strony usunęłoby APK. Obecny workflow publikuje stronę i dokumentację.
