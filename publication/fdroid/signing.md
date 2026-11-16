# Klucz podpisujący APK F-Droid

Ten klucz podpisuje APK FM1 Flasher publikowane w GitHub Releases. Każda
przyszła aktualizacja musi być podpisana tym samym kluczem. Utrata keystore'a
lub hasła uniemożliwi wydawanie aktualizacji dla tej instalacji aplikacji.

To nie jest klucz podpisujący indeks repozytorium F-Droid. Klucz indeksu będzie
osobny i zostanie skonfigurowany przy wdrażaniu samego repozytorium.

## 1. Wygeneruj keystore poza repozytorium

Wykonaj polecenia na zaufanym komputerze. `keytool` poprosi interaktywnie o
hasła; nie dodawaj ich jako argumentów polecenia ani do historii powłoki.

```sh
umask 077
mkdir -p "$HOME/.local/share/fm1-signing"
cd "$HOME/.local/share/fm1-signing"
keytool -genkeypair \
  -keystore fm1-fdroid-release.p12 \
  -storetype PKCS12 \
  -alias fm1-fdroid-release \
  -keyalg RSA -keysize 4096 -validity 10000
```

Zapisz osobno w menedżerze haseł hasło keystore'a, hasło klucza i alias
`fm1-fdroid-release`. Gdy `keytool` zapyta o hasło klucza, można użyć tego
samego hasła co dla keystore'a. Utrzymaj keystore i kopie zapasowe poza
katalogiem projektu oraz poza katalogiem publikowanym przez GitHub Pages.

## 2. Zrób i sprawdź kopie zapasowe

Zaszyfruj keystore przy użyciu sprawdzonego szyfrowania dysku lub archiwum.
Przechowuj dwie kopie w oddzielnych, kontrolowanych lokalizacjach. Zapisz
hasło szyfrujące i hasła `keytool` w menedżerze haseł. Nie wysyłaj keystore'a
przez e-mail, nie dodawaj go do Git i nie umieszczaj w artefaktach Actions.

Przed kontynuacją sprawdź, że można odczytać kopię i wyświetlić certyfikat:

```sh
keytool -list -v \
  -keystore "$HOME/.local/share/fm1-signing/fm1-fdroid-release.p12" \
  -alias fm1-fdroid-release
```

Zapisz fingerprint `SHA256` certyfikatu. Będzie przypięty w sekrecie
`FDROID_APP_CERT_SHA256` i pozwoli workflow odrzucić APK podpisany innym
kluczem.

## 3. Dodaj sekrety środowiska GitHub

W ustawieniach repozytorium utwórz środowisko `fdroid-release`. Dodaj do niego
poniższe sekrety. Nie wpisuj wartości do plików projektu.

| Sekret | Wartość |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Jednoliniowy Base64 pliku PKCS12 |
| `ANDROID_KEYSTORE_PASSWORD` | Hasło keystore'a |
| `ANDROID_KEY_ALIAS` | `fm1-fdroid-release` |
| `ANDROID_KEY_PASSWORD` | Hasło klucza |
| `FDROID_APP_CERT_SHA256` | Fingerprint SHA-256 bez dwukropków lub z dwukropkami |

Na macOS wygeneruj jednoliniową wartość Base64 poleceniem
`base64 -i plik | tr -d '\n'`; na Linuxie `base64 -w 0 plik`. Wklej wynik do
sekretu.
Nie wypisuj wartości w logach ani nie przesyłaj jej do czatu.

Workflow usuwa plik keystore'a z runnera po zakończeniu joba. GitHub Secrets
chroni wartość w konfiguracji Actions, ale nie zastępuje offline'owej kopii
zapasowej.

## 4. Sprawdź konfigurację przed pierwszym wydaniem

Przed uruchomieniem `.github/workflows/android-release.yml`:

1. Porównaj fingerprint sekretu `FDROID_APP_CERT_SHA256` z wynikiem `keytool`.
2. Sprawdź manifest `scripts/firmware-fixtures.json`. Produkcyjny build pobierze
   przypięte SHA-256 paczki V15/V16 i uruchomi `:app:firmwareIntegrationTest`.
   Brak paczek lub błąd testu blokuje wydanie. Debug i prerelease używają
   wyłącznie syntetycznych fixture’ów; zmienne archiwum nie są potrzebne.
3. Uruchom workflow z tagiem stabilnym, który odpowiada `versionName` w
   `app/build.gradle`. Tag musi zawierać skrypt weryfikacji APK i kod potrzebny
   do budowania wydania.
4. Zachowaj opublikowany APK i SHA-256. Repozytorium F-Droid ma później użyć
   dokładnie tego APK, bez ponownego budowania.

Nie zmieniaj ani nie odtwarzaj klucza po pierwszym wydaniu. Jeśli klucz zostanie
utracony, nie będzie można podpisać aktualizacji zgodnej z już opublikowanym
APK.

## 5. Podpis debug i produkcyjny AAB

Debug APK korzysta z testowego Firebase oraz trwałego, oddzielnego klucza.
Wygeneruj go poleceniem z punktu 1, zmieniając plik na `fm1-debug.p12`, a alias
na `fm1-debug`. Kopię zapasową przechowuj tak samo jak dla klucza F-Droid.
Nie używaj keystore'a F-Droid do podpisywania wersji debug.

W środowisku `android-debug` dodaj cztery sekrety `ANDROID_KEYSTORE_BASE64`,
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`
z danymi klucza debug oraz `DEBUG_APP_CERT_SHA256` z jego fingerprintem.
Dodaj też `FIREBASE_TEST_ANDROID_JSON`: pełną zawartość `google-services.json`
z prawdziwego projektu testowego dla `pl.ekim.fm1_flasher`. Fixture z
`tools/fixtures/google-services.test.json` nie jest działającym projektem.
Stały podpis pozwala aktualizować debug APK kolejnym debug APK. Debug i release
mają obecnie ten sam applicationId i nie mogą być zainstalowane obok siebie.

W środowisku `google-play` dodaj cztery sekrety podpisywania z kluczem uploadu,
`PLAY_UPLOAD_CERT_SHA256` oraz `FIREBASE_PRODUCTION_ANDROID_JSON`. Klucz uploadu
może być inny niż klucz APK F-Droid; klucz podpisujący APK dostarczane przez
Play jest osobną kwestią wymagającą ustawienia w Play Console.

Sekrety dla uploadu `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` umieść wyłącznie
w środowiskach `google-play-upload` i `google-play-production`. Sam build AAB
nie potrzebuje dostępu do konta Play. Instrukcje uruchamiania i odzyskiwania
publikacji: [github-actions.txt](../github-actions.txt).

Wydania F-Droid i Google Play mogą mieć niezależne terminy i wersje. Do publikacji
samego APK F-Droid wystarczy `fdroid_run_id`; `debug_run_id` jest opcjonalny.
Dla preview debug wybierz gałąź deweloperską bez tagu albo tag preview zgodny
z versionName. Debug preview pozostaje artefaktem Actions.
