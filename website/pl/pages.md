# Strona projektu na GitHub Pages

Witryna, dokumentacja oraz polityka prywatności powstają ze źródeł w tym repozytorium. Każda podstrona jest dostępna po angielsku, polsku i chińsku uproszczonym, wraz z nawigacją, instrukcjami publikacji oraz tłumaczeniami licencji. Odnośniki **English**, **Polski** i **简体中文** przełączają tę samą podstronę na inny język.

## Pierwsza publikacja

1. Wyślij projekt do repozytorium GitHub wraz z `.github/workflows/pages.yml`, `website/` i `scripts/build_site.py`.
2. W **Settings → Pages → Build and deployment → Source** wybierz **GitHub Actions**.
3. W **Actions → GitHub Pages → Run workflow** wybierz domyślną gałąź. Kolejne zmiany w tej gałęzi publikują stronę automatycznie.
4. Opublikowana witryna to [ekim-pl.github.io/android-fm1-flasher](https://ekim-pl.github.io/android-fm1-flasher/pl/). Forki zwykle używają adresu `https://LOGIN.github.io/REPOSITORY/`; sprawdź podsumowanie wdrożenia lub **Settings → Pages**.

Jeżeli środowisko `github-pages` ogranicza dozwolone gałęzie, dopuść domyślną gałąź projektu. Workflow buduje stronę także dla pull requestów i pozostałych gałęzi, ale publikuje tylko z domyślnej gałęzi. Nie wymaga sekretów aplikacji ani dostępu do Google Play.

## Języki i stałe adresy

- `en/`: angielska witryna, dostępna również pod głównym adresem dla zachowania istniejących odnośników.
- `pl/`: pełna polska witryna.
- `zh-CN/`: pełna witryna po chińsku uproszczonym.

Każdy język obejmuje stronę główną, instrukcję techniczną, opis aplikacji, politykę prywatności, licencję, materiały publikacyjne, instrukcję Google Play oraz poradnik wdrażania witryny. Przełącznik języka zachowuje aktualnie czytaną podstronę. Nawigacja, etykiety dostępności, tytuły, opisy i stopki są w wybranym języku.

Dotychczasowe adresy, np. `documentation.html`, `about/pl.html` i `privacy/en/`, nadal działają. Publiczne adresy polityki `privacy/pl/`, `privacy/en/` i `privacy/zh-CN/` można w dalszym ciągu podawać w Play Console.

## Budowanie lokalne

Uruchom z katalogu głównego projektu:

```sh
python3 -m venv .venv-pages
.venv-pages/bin/pip install -r website/requirements.txt
.venv-pages/bin/python scripts/build_site.py
.venv-pages/bin/python scripts/check_site.py
```

Otwórz `build/site/index.html`. Względne ścieżki działają także pod prefiksem repozytorium na Pages. Wynik budowania zawiera tylko publiczną dokumentację i zasoby witryny.

W CI adres źródeł jest pobierany z `GITHUB_SERVER_URL`, `GITHUB_REPOSITORY` i `GITHUB_REF_NAME`. Przy lokalnym budowaniu ustaw te same zmienne, aby dodać odnośniki do źródeł i wydań na GitHubie.

## Edycja treści

- `README.md`, `website/index.md` i `website/pages.md`: angielska instrukcja techniczna, strona główna i poradnik wdrażania.
- `publication/README.txt` i `publication/github-actions.txt`: angielskie instrukcje publikacji i Google Play.
- `website/pl/` i `website/zh-CN/`: pełne tłumaczenia witryny, w tym licencji i polityki prywatności.
- `about/README*.md`: istniejące tłumaczenia opisu aplikacji, używane także na stronie.
- `PRIVACY_POLICY.md`: angielska polityka prywatności. Tłumaczenia witryny muszą być zgodne ze statycznymi kopiami w `publication/privacy/` i zasobami aplikacji.
- `LICENSE.md`: oryginalna angielska licencja. Tłumaczenia witryny ułatwiają zrozumienie; rozstrzygający pozostaje oryginał.
- `website/style.css`: wspólny wygląd.

Zmieniając wspólną treść, aktualizuj wszystkie trzy wersje. Brak tłumaczenia podstrony przerywa budowanie zamiast pokazywać po cichu angielski tekst. Kontrola odnośników sprawdza język stron, cele przełączników języka, pliki i kotwice.

Dokumentacja repozytorium jest po angielsku. Opisy aplikacji, jej zasoby i materiały sklepowe celowo zachowują wszystkie obsługiwane języki.

## Wspólna publikacja z repozytorium F-Droid

GitHub Pages ma jedno aktywne wdrożenie witryny dla repozytorium. Po dodaniu publikacji F-Droid należy dołączać katalog `fdroid/repo/` do `build/site/` przed wysłaniem artefaktu Pages. Witryna i repozytorium APK muszą być publikowane razem: workflow wdrażający wyłącznie APK zastąpiłby witrynę, a wdrożenie samej witryny usunęłoby repozytorium APK. Obecny workflow publikuje witrynę i dokumentację.
