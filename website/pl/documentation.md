# FM-1 Flasher — instrukcja

Niezależny menedżer firmware dla **M-VAVE FM-1** na Androida. Android obsługuje wiele instrumentów przez hub OTG, zapisując firmware **kolejno, na jednym instrumencie naraz**.

Dokument opisuje stabilną wersję **0.9.2** na branchu `main`. Wersja dodaje pełnoekranowe ustawienia, adaptery providerów i uwierzytelnianie HTTP przypisane do źródła. [Raport buildu](../../publication/qa/0.9.2.txt) opisuje weryfikację i ograniczenia. Właściciel potwierdził fizyczne testy FM-1 dla buildu 20; zakres testów źródeł i uwierzytelniania opisano w raporcie.

[English](../../README.md) · [简体中文](../zh-CN/documentation.md) · [Architektura](../../MIGRATION.md) · [Prywatność](privacy.md)

## Obsługiwane platformy

Wymagany jest Android 14+, USB Host/OTG i MIDI.

Identyfikator Androida to `pl.ekim.fm1_flasher`. Interfejs obsługuje polski, angielski i chiński uproszczony oraz motyw jasny, ciemny i systemowy.

<a id="getting-started"></a>
## Pierwsza instalacja i plan aktualizacji

1. Zainstaluj APK 0.9.2 na Androidzie 14 lub nowszym. Podpis debug buildu 23 odpowiada buildowi 18: instalacja jako aktualizacja, bez odinstalowania, zachowuje bibliotekę i preferencje tego forka.
2. W **Bibliotece** znajdź firmware i pobierz paczkę z jej szczegółów. Pobranie sprawdza integralność, ale nie rozpoczyna instalacji.
3. Podłącz instrumenty przewodami do transmisji danych przez hub OTG. Zalecany jest hub z osobnym zasilaniem. Aplikacja nie mierzy jakości zasilania.
4. W **Urządzeniach** wybierz **Odczytaj instrument** na każdej karcie. Udziel systemowej zgody USB, gdy będzie potrzebna. Do planu można dodać rozpoznany FM-1.
5. W **Szczegółach** nadaj czytelną nazwę. Wskaż typ pamięci na podstawie znanego sprzętu i wybierz lokalną paczkę. Paczka zgodna z obydwoma wspieranymi typami pamięci może być dopuszczona także przy nieznanym typie kości; paczki niezgodne lub o niepotwierdzonej zgodności pozostają zablokowane.
6. Wybierz **Dodaj do planu** dla kolejnych instrumentów. Urządzenie może mieć jedno oczekujące lub wykonywane zadanie. Każdy instrument może otrzymać inną paczkę.
7. W **Zadaniach** sprawdź nazwy, wersje i sumy paczek, a następnie wybierz **Zainstaluj na N instrumentach**. Ten przycisk zatwierdza plan; zwykła instalacja nie wyświetla drugiego okna potwierdzenia.

Zachowaj połączenie USB i zasilanie do zakończenia weryfikacji. Jeżeli firmware udostępnia własny eksport danych, wykonaj kopię. Aktualizacja OTA nie robi automatycznej kopii; firmware może zmienić ustawienia instrumentu.

## Biblioteka i źródła

**Biblioteka** łączy wyszukiwanie, ulubione i pobrane paczki. **Tylko pobrane** i **Ulubione** są filtrami, a nie osobnymi zakładkami Androida. **Filtry i sortowanie** rozwija kategorię, kolejność i źródło. Przy aktywnym filtrze pobranych dostępna jest akcja aktualizacji paczek.

Szczegóły paczki pokazują wersję, autora/źródło, zgodność i SHA-256. Wybranie lokalnej paczki przenosi do Urządzeń; nie uruchamia zapisu. Zwykłe usunięcie poprawnej paczki można cofnąć w bieżącej sesji aplikacji. Paczki przypisanej do oczekującego lub aktywnego zadania nie można usunąć ani zastąpić: najpierw usuń oczekujące zadanie. Usunięcie paczki zachowuje ulubione.

Otwórz **Ustawienia → Źródła katalogu firmware**:

- **The Firmware Hub** jest domyślnie włączony, a **Awesome FM-1** wyłączony. [Awesome FM-1](https://github.com/ekim-pl/awesome-fm1) jest społecznościowym katalogiem projektów i odnośników do ich wydawców.
- Własne źródło wymaga katalogu HTTPS w obsługiwanym formacie JSON Hub, nazwy, interwału 24–168 godzin i zatwierdzonych zakresów adresów paczek/grafik. **Zapisz** zapisuje konfigurację, a **Sprawdź teraz** jest osobnym działaniem. Nowe źródło jest początkowo wyłączone.
- Odświeżanie uwzględnia interwał także po nieudanej próbie. Wielokrotne kliknięcie nie omija minimalnych 24 godzin między próbami. Ekran źródeł pokazuje błędy i termin kolejnego sprawdzenia.
- Wyłączone źródło nie wysyła automatycznych żądań. Można świadomie użyć Sprawdź teraz również dla wyłączonego źródła. Błąd zachowuje jego poprzednią kopię katalogu i nie blokuje pozostałych źródeł.
- Usunięcie własnej subskrypcji zachowuje paczki, ulubione, pochodzenie i poznane wycofania. Przywrócenie ustawień domyślnych włącza Hub i wyłącza Awesome FM-1 bez usuwania własnych subskrypcji.
- Sprawdzanie katalogów w tle jest opcjonalne i początkowo wyłączone. Sprawdza włączone katalogi przy odpowiednich warunkach sieciowych; nie pobiera ani nie instaluje firmware.

Tryb offline wyłącza sieciowe pobieranie katalogów, paczek i grafik oraz wstrzymuje sprawdzanie w tle. Zapisane firmware można instalować bez żądania sieciowego. Offline nie wykryje wycofania, którego lokalny katalog jeszcze nie zna. Deklaracje katalogu i sumy kontrolne same w sobie nie potwierdzają autentyczności wydawcy.

<a id="multiple-fm1-devices-on-a-hub"></a>
## Wiele FM-1 na hubie OTG

Karta ma lokalny rekord, nazwę/kolor, odczytaną tożsamość firmware, wybrany typ pamięci i bieżące połączenie. **Adres USB nie jest numerem fizycznego gniazda huba.** Model/wersja, VID/PID i typ kości JEDEC nie identyfikują jednoznacznie egzemplarza. Odczytane numery seryjne USB są lokalne; nie potwierdzono ich unikalności pomiędzy trybami FM-1.

Tylko jedno zadanie zmienia tryb naraz. Po restarcie aplikacja obserwuje USB/MIDI, wyklucza niezmienionych sąsiadów i wymaga jednego dopuszczalnego powrotu. Pozostałe obserwowane połączenia muszą zachować ciągłość. Przy wielu instrumentach użyj **powiązania na karcie zadania** wyłącznie dla aktualizowanego egzemplarza. Nie można obejść blokady, zgadując między kilkoma kandydatami. Nie zamieniaj nierozróżnialnych instrumentów podczas przejścia.

Automatyczne parowanie wielu urządzeń pozostaje wyłączone do testów sprzętowych. Zachowano ostrożny wariant automatyczny tylko przy braku sąsiadów MIDI oraz dokładnie jednym dopuszczalnym punkcie MIDI i jednym urządzeniu USB zgłaszanym przez Androida. Sam serial nie zezwala na automatyczne dopasowanie. Po odłączeniu i ponownym wykryciu system może ponownie wymagać zgody USB.

Utrata sąsiada, reset huba, zmiana generacji połączenia, sprzeczny serial lub niejednoznaczne porty zatrzymują dalszy zapis. Wiele par portów MIDI lub wiele punktów MIDI jednego urządzenia USB blokuje zapis. Potwierdzenie wyniku oznacza sprawdzenie powrotu i oczekiwanego modelu/wersji, a nie pełny odczyt porównawczy pamięci flash.

Nazwy przetrwają restart, ale powiązania z połączeniami nie przetrwają utraty procesu ani niewiarygodnego rozłączenia. Odczytaj i fizycznie rozpoznaj instrument, usuń dotknięte oczekujące zadania, a potem użyj **Zapisane instrumenty → Przypisz tę zapisaną nazwę**. Powiązanie resetuje dawny wybór pamięci; wskaż go ponownie przed ułożeniem planu.

## Zadania, zatrzymywanie i błędy

Zadanie utrwala cel i połączenie, oczekiwaną tożsamość, typ pamięci, klucz i hash paczki. Wszystkie paczki są sprawdzane przed pierwszym zapisem; każde zadanie jest sprawdzane ponownie przed własnym transferem. Zmiana wybranej paczki w interfejsie nie przekierowuje aktywnego zadania.

- **Zatrzymaj po tym instrumencie** dokończy bieżący zapis i weryfikację powrotu. Pozostałe zadania pozostaną w kolejce. Nie ma przycisku gwałtownego przerwania zapisu.
- Błąd zadania zatrzymuje przebieg. Przed nową próbą sprawdź instrument i szczegóły zadania. Paczka nie zostanie automatycznie przypisana innemu urządzeniu.
- Postęp i wyniki są w Zadaniach. Szczegóły pokazują pełny hash i czasy etapów. Trwała historia etapów obejmuje maksymalnie 64 wpisy na zadanie.
- Po utracie procesu aktywne zadanie staje się **niepotwierdzone**. Transfer nie wznawia się automatycznie od zapisanego procentu. Zadania oczekujące pozostają widoczne, lecz ich powiązania trzeba odbudować.
- Jeżeli plan nie odpowiada połączeniom lub paczkom, usuń dotknięte zadania, odczytaj instrumenty i dodaj nowe. Odmowa zgody USB nie upoważnia do zapisu.

Usługa pierwszoplanowa i ograniczony czasowo wake lock wspierają transfer przy zgaszonym ekranie lub innej aplikacji na wierzchu. Nie gwarantują zasilania USB ani ochrony przed każdą utratą procesu. Wróć do aplikacji, gdy potrzebne będzie powiązanie lub systemowa zgoda.

## Ustawienia i funkcje eksperymentalne

Ustawienia obejmują język/motyw, offline, źródła, O aplikacji i prywatność. **Funkcje eksperymentalne** są domyślnie wyłączone. Włączają Logger MIDI i narzędzia recovery; nie można zmieniać tego przełącznika w trakcie operacji. Zwykłe instalacje Androida wykonuje się przez Urządzenia i Zadania.

Pasywny logger MIDI ma ograniczone bufory i wybrane wejścia. Zatrzymuje się automatycznie przy łączeniu/zapisie; jednoczesne logowanie i flashowanie nie jest gwarantowane.

Wykrywanie JEDEC dotyczy osobnego urządzenia USB w trybie ROM/download. Wynik nie przypisuje się automatycznie do instrumentu MIDI. Recovery ROM wymaga osobnej świadomej akcji, kopii/eksportu i przygotowanego planu zapisu. Ogranicza się do przypiętego hashem obszaru aplikacji stock V15 oraz rozpoznanych warunków boot/kości; nie zapisuje bootloadera. Nie jest uniwersalnym narzędziem naprawczym ani automatycznym zastępstwem nieudanego OTA. Brakuje fizycznej weryfikacji recovery.

<a id="building-and-testing"></a>
## Budowanie i testy

Wymagane są JDK 17, Android SDK platform 36 i Build Tools 35.0.0. Wrapper ustala Gradle; projekt ustala AGP 8.13.2, Kotlin 2.2.21 i Compose 1.9.3. Skonfiguruj SDK lokalnie przez środowisko lub narzędzia Androida.

```sh
python3 tools/sync-resources.py --check
./gradlew :shared:jvmTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Testy: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`. Katalog `dist/` jest ignorowany przez Git: przygotowana kopia buildu 23 jest lokalna i nie musi istnieć po sklonowaniu repozytorium.

Źródłem tłumaczeń interfejsu są XML w `app/src/main/res/values*`; po ich zmianie uruchom `python3 tools/sync-resources.py`. Dokumenty O aplikacji to `about/app*.md`, pakowane z licencjami przez `:app:bundleProjectDocs`. Główny README nie jest dokumentem O aplikacji.

Zobacz [architekturę i komendy testów](../../MIGRATION.md), [listę odbiorową](../../tests/android14-acceptance.txt) i [budowanie witryny](pages.md). [Odziedziczone workflow Google Play wskazują inny identyfikator aplikacji](github-actions.txt); publikacja jest osobnym działaniem.

## Planowany port na iOS

Port na iOS jest planowany w przyszłości. Nie ma jeszcze wydania na iOS ani ogłoszonego terminu.

## Zakres weryfikacji

W buildzie 23 zaliczono **191 testów jednostkowych (62 shared + 129 Android JVM)**; cztery testy zewnętrznych fixture’ów pominięto. Zaliczone są **44 testy instrumentacyjne Androida**, w tym eksport/czyszczenie logu, czyszczenie biblioteki i potwierdzenia w trzech językach. Przeszły debug, lint i kompilacja metadanych iOS. Właściciel potwierdził działanie buildu 20 na fizycznych FM-1; nowych funkcji i wszystkich kombinacji sprzętu nie zweryfikowano fizycznie.

Nie sprawdzono fizycznego FM-1/huba ani aplikacji iOS. Zasilanie, rzeczywiste przejścia trybów/deskryptorów, unikalność seriali i pełny zapis sprzętu pozostają warunkami odbioru. [Raport buildu](../../publication/qa/0.9.2.txt) zawiera identyfikację pliku i zakres prób.

## Prywatność i autorstwo

Bez konta, reklam i analityki. Biblioteka, nazwy, odczytane seriale, wybory pamięci i historia zadań pozostają lokalnie. Serwery katalogów/grafik/paczek otrzymują metadane połączenia; aplikacja nie dołącza tożsamości instrumentów do tych żądań. Szczegóły: [polityka prywatności](privacy.md).

Kod aplikacji: [MIT](../../LICENSE.md). Katalog [The Firmware Hub](https://thefirmwarehub.com): CC BY 4.0; firmware zachowuje licencje autorów. [Awesome FM-1](https://github.com/ekim-pl/awesome-fm1) jest opcjonalnym katalogiem. Informacje o zależnościach: [third_party](../../third_party/). Kontakt: [ekim.pl@gmail.com](mailto:ekim.pl@gmail.com).

## Diagnostyka i czyszczenie pamięci

Aplikacja stale zapisuje lokalny log operacji (do 256 KiB; najstarsze wpisy są usuwane). Log przetrwa restart aplikacji i zawiera czas, działania, zmiany portów USB/MIDI, etapy instalacji oraz przechwycone błędy. Nie zapisuje surowego ruchu MIDI ani plików firmware. Włącz **Ustawienia → Funkcje eksperymentalne**, aby użyć **Eksportuj log operacji** (JSON Lines, `.jsonl`) i **Wyczyść log operacji**. Eksport otwiera systemowy wybór pliku; log nie jest automatycznie wysyłany. Czyszczenie wymaga jednego potwierdzenia i nie usuwa wcześniej wyeksportowanych kopii. Kolejne zdarzenia są nadal zapisywane. Osobny Logger MIDI służy do podglądu surowego ruchu MIDI.

**Ustawienia → Wyczyść pobrane firmware** usuwa po jednym potwierdzeniu wszystkie pobrane paczki i metadane, w tym uszkodzone i osierocone pliki. Czyści wybór paczki i możliwość cofnięcia pojedynczego usunięcia. Zachowuje ulubione, źródła katalogu, nazwy instrumentów, historię zadań i kopie odzyskiwania. Najpierw usuń oczekujące zadania; czyszczenie jest blokowane podczas pracy oraz przy paczkach przypisanych do oczekujących/trwających zadań. Instalacja offline wymaga ponownego pobrania paczek.

**Urządzenia → Tryby i kość pamięci** rozdziela odczyt MIDI, przejście w tryb download i wykrywanie kości na czytelne karty. Wynik detekcji otwiera popup. Potem wyłącz i włącz instrument, aby wrócić do MIDI. Domyślne odłączone wpisy są tymczasowe; własne nazwy i cele kolejki pozostają. Opóźnione odłączenie poprzedniego portu USB nie przerywa kolejnego etapu tej samej aktualizacji. Utrata innego instrumentu lub huba nadal blokuje zapis.

## Pełnoekranowe ustawienia (0.9.2)

**Ustawienia** są pełnym ekranem z kartami Interfejs, Biblioteka firmware, Funkcje eksperymentalne i Aplikacja. Język oraz motyw wybiera się bezpośrednio przyciskami radiowymi. Źródła, formularze, zgodność poszczególnych paczek i polityka prywatności otwierają się jako pełne strony. Nagłówek i systemowe Wstecz cofają o poziom; poprzednia zakładka Biblioteka/Urządzenia/Zadania i niesekretne dane formularza przetrwają odtworzenie aktywności. Wpisane hasła/tokeny nie są zapisywane w stanie aktywności. Okna pozostają przy potwierdzeniach usuwania i żądanym wyniku wykrywania kości.

## Providery katalogów i uwierzytelnianie

Android używa adapterów wspólnego interfejsu `CatalogProvider`:

| Provider | Działanie |
| --- | --- |
| `thefirmwarehub` | Wbudowany katalog JSON Firmware Hub i dotychczasowe zasady pobierania |
| `awesome-fm1` | Wbudowany katalog JSON Awesome FM-1 i zatwierdzone zakresy URL |
| `repo-url` | Repozytorium GitHub.com, opublikowane wydania/prerelease i załączniki `.fwsc`; bez draftów |
| `pkg-url` | Bezpośredni adres HTTPS `.fwsc`, identyfikacja i suma z faktycznego pliku |

Adapter `catalog-json` zachowuje dotychczasowe niestandardowe subskrypcje JSON. Stare identyfikatory źródeł, ulubione, pobrane paczki i historia pozostają. Zapisz ręczne źródło, wybierz **Sprawdź teraz** i włącz je, aby udostępnić jego oferty. Import ma limity; błąd lub niepełne pobranie zachowuje poprzedni katalog. Paginacja i paczki GitHub respektują opóźnienia ponowienia. Duże repozytoria mogą wymagać uwierzytelniania z powodu limitów GitHub.

Gdy GitHub udostępnia SHA-256 załącznika, aplikacja używa tej sumy i odczytuje początek paczki w celu ustalenia produktu. Starsze załączniki bez sumy wymagają pobrania i walidacji paczki, aby obliczyć hash. Sprawdzanie bezpośredniego pliku również pobiera i weryfikuje paczkę. Sprawdzanie może więc przesyłać dane binarne bez oznaczania paczki jako pobranej do pracy offline. **Pobierz** nadal zapisuje ją w bibliotece; instalacja nie rozpoczyna się automatycznie.

Ręczne providery nie zgadują zgodności kości z nazw plików. Po sprawdzeniu źródła otwórz **Edytuj → Zgodność poszczególnych paczek**, potwierdź informacje autora dla konkretnego SHA-256, wróć i zapisz. Deklaracja aktualizuje metadane pasujących bajtów, także lokalnej kopii; nie obejmuje innego pliku pod tym samym URL-em ani innego wydania. Nieznana zgodność blokuje instalację. Brak deklaracji nie jest sprzecznością ze znanymi danymi; sprzeczne jawne deklaracje tożsamości/kości nadal blokują użycie.

Formularz źródła oferuje brak uwierzytelniania, HTTP Basic, Bearer, klucz API we wskazanym bezpiecznym nagłówku i OAuth 2.0. Basic przyjmuje użytkownika/hasło, Bearer sam token bez prefiksu `Bearer`. Klucz API wysyła wartość nagłówka dosłownie, w tym własny prefiks przy nagłówku `Authorization`. Hasła, tokeny i client secret są szyfrowane kluczem Android Keystore, poza metadanymi SQLite, i nie trafiają do logu. Puste pole zachowuje sekret tylko dla niezmienionego źródła/providera/tożsamości uwierzytelniania; inny URL nie dziedziczy go. Usunięcie źródła i odtworzenie ustawień wbudowanych usuwa odpowiednie sekrety.

**Migracja OAuth:** Nowy adres poniżej dotyczy kolejnego buildu Androida. Wersja 0.9.4-experimental.26 i wcześniejsze używają adresu pokazanego w ustawieniach źródła w aplikacji. Przed aktualizacją lub ponowną autoryzacją zarejestruj nowy adres u dostawcy.

OAuth obsługuje logowanie w przeglądarce Authorization Code z **PKCE S256** oraz usługowy **Client Credentials**. Skonfiguruj serwer, client ID, zakresy, endpoint tokenów i — dla przeglądarki — endpoint autoryzacji. Zarejestruj `pl.ekim.fm1-flasher://oauth/callback` w serwerze. Client Credentials wymaga client secret, publiczny klient PKCE nie. Wybierz `client_secret_basic` lub `client_secret_post` zgodnie z serwerem. Zapisz źródło i wybierz **Autoryzuj OAuth 2.0** na jego karcie. Wygasłe tokeny są odświeżane, gdy serwer zwróci refresh token; Client Credentials może uzyskać nowy token. Stan przeglądarkowego logowania jest jednorazowy, przypisany do źródła/rewizji i wygasa po dziesięciu minutach. Aplikacja nie zawiera uniwersalnego client ID ani sekretu.

Uwierzytelnienie jest ograniczone do pochodzenia źródła (API GitHub dla `repo-url`). Przy zmianie domeny w przekierowaniu, także do CDN, jest usuwane; przekierowania POST tokenów są odrzucane. Nadal obowiązuje HTTPS oraz kontrola publicznych adresów i zakresów URL. Zwykłe sprawdzanie zachowuje harmonogram 24–168 godzin. Jawna zmiana sekretu lub ukończona autoryzacja pozwala sprawdzić źródło ponownie, bez całodobowego oczekiwania po błędzie logowania.

Port na iOS jest planowany w przyszłości. Nie ma jeszcze wydania na iOS ani ogłoszonego terminu.
