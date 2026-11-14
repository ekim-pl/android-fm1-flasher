# FM‑1 Flasher dla Androida

## Funkcje eksperymentalne w 0.7.0

W **Ustawienia → Funkcje eksperymentalne** można włączyć logger MIDI, wykrywanie pamięci i recovery. Opcja jest domyślnie wyłączona, zapamiętywana i działa w debug oraz release. Wyłączenie zatrzymuje logger i usuwa plan recovery. Podczas operacji nie można jej zmienić. Wynik wykrycia pamięci nie zmienia zgodności wybranego urządzenia MIDI. Recovery nadal wymaga testów sprzętowych.

Kod źródłowy aplikacji jest udostępniany na licencji MIT — zobacz [LICENSE.md](../../LICENSE.md).
Zasady przetwarzania danych opisuje [polityka prywatności](../../PRIVACY_POLICY.md).

Eksperymentalna aplikacja Kotlin/Android 14+ do wybierania jednego M‑VAVE FM‑1 na hubie USB OTG, pobierania firmware z katalogu i instalowania go przez USB MIDI SysEx. Wcześniejsze testy użytkownika potwierdziły udane flashowanie fizycznego FM‑1. Automatyczne ponowne łączenie wymaga dalszych testów na sprzęcie.

## Język i wygląd

W **Ustawieniach** w górnym pasku wybierz język: polski, angielski, chiński uproszczony albo systemowy. Ten sam wybór jest dostępny również w systemowych ustawieniach języka aplikacji. Wszystkie trzy języki są dołączane do APK i AAB, także do zmiany bez internetu. Przetłumaczone są również ostrzeżenia, błędy, dziennik i powiadomienia; teksty pobierane od autorów firmware zachowują oryginalny język.

Motyw może być jasny, ciemny lub zgodny z systemem. Ustawienia zawierają także dostępną offline politykę prywatności.

W poziomie, przy szerokości co najmniej 600 dp, nawigacja przechodzi na lewy pasek, a filtry katalogu stoją obok listy. Flasher pokazuje urządzenie i lokalną paczkę obok siebie; szczegóły firmware mają mniejszy podgląd i osobną kolumnę działań. Obrót zachowuje widoczną pozycję katalogu i otwarte szczegóły paczki.

## O aplikacji

Zakładka **O aplikacji** korzysta z krótkich, niezależnych plików Markdown w katalogu [about](../../about): [polskiego](../../about/README.pl.md), [angielskiego](../../about/README.md) i [chińskiego uproszczonego](../../about/README.zh-Hans.md). Są dołączane automatycznie podczas budowania i dostępne offline. Treść dopasowuje się do języka oraz motywu aplikacji. Ten plik pozostaje dokumentacją techniczną projektu i nie jest dołączany do aplikacji.

Z opisu aplikacji można otworzyć licencję i politykę prywatności. Odnośniki internetowe otwierają odpowiednią aplikację systemową.

## Strona projektu i dokumentacja online

Publiczne źródła: [GitHub](https://github.com/ekim-pl/android-fm1-flasher). [Strona projektu](https://ekim-pl.github.io/android-fm1-flasher/pl/) udostępnia pełną witrynę i dokumentację po angielsku, polsku i chińsku uproszczonym. Przełączniki języka prowadzą do tej samej podstrony.

Workflow [GitHub Pages](../../.github/workflows/pages.yml) buduje witrynę, instrukcję techniczną, opisy aplikacji, poradniki publikacji, licencje i polityki prywatności w trzech językach. Pull requesty i gałęzie sprawdzają budowanie; zmiany w domyślnej gałęzi automatycznie publikują stronę. W repozytorium GitHub ustaw **Settings → Pages → Source → GitHub Actions**. [Konfiguracja, lokalne budowanie i edycja strony](../../website/pages.md).

Dokumentacja repozytorium jest utrzymywana po angielsku. Tłumaczenia witryny znajdują się w `website/pl/` i `website/zh-CN/`; tłumaczenia aplikacji i materiałów sklepowych pozostają w dotychczasowych katalogach językowych.

## Materiały publikacyjne

[Instrukcja publikacji](../../publication/README.txt) opisuje zawartość paczki i pozostałe kroki. Katalog [fastlane/metadata/android](../../fastlane/metadata/android) zawiera opisy, informacje o zmianach, ikony, grafiki wyróżniające i zrzuty w trzech językach, w formacie przydatnym dla Google Play i F-Droid. [Strony polityki prywatności](../../publication/privacy/index.html) można umieścić na publicznym hostingu HTTPS. Zrzuty źródłowe i materiały do formularzy sklepowych są w [publication](../../publication).

<a id="getting-started"></a>

## Uruchomienie

Gotowe APK testowe pobierzesz z [wydań na GitHubie](https://github.com/ekim-pl/android-fm1-flasher/releases). To APK podpisane kluczem debug, przeznaczone do testów, nie wydanie do sklepu. Lokalne materiały robocze: `dist/FM1-Flasher-debug.apk` i `dist/FM1-Flasher-publication.zip`; katalog `dist/` nie jest częścią repozytorium.

1. Zainstaluj APK na telefonie obsługującym USB Host/OTG oraz MIDI. Podłącz zasilany hub i instrumenty przewodem do transmisji danych.
2. W zakładce **Flasher** wybierz port z listy i naciśnij **Połącz z wybranym portem**. Po zgodzie Androida na USB aplikacja odczyta tożsamość tylko tego urządzenia. Nazwa MIDI nie jest kryterium wyboru: adres USB i numer MIDI rozróżniają identycznie nazwane egzemplarze.
3. Typ kości flash pozostaw jako nieznany, chyba że znasz rzeczywisty JEDEC ID tego egzemplarza. Wybór dotyczy bieżącego połączenia i resetuje się po zmianie instrumentu.
4. W katalogu otwórz podgląd firmware i wybierz **Pobierz na telefon**. Po zakończeniu pobierania wybierz **Użyj do flashowania** albo wskaż pobraną paczkę z listy w zakładce **Flasher**. Oba miejsca korzystają z tego samego, zapamiętanego wyboru. Pobieranie jest oddzielone od zapisu. Repozytorium autora jest dostępne w szczegółach.
5. Po wykonaniu kopii danych narzędziem danego firmware sprawdź docelowy port i pakiet w oknie potwierdzenia, a następnie rozpocznij aktualizację.
6. Po każdym restarcie nowy port zostanie wykryty automatycznie, jeśli Android widzi dokładnie jedno urządzenie USB i jeden nowy, dozwolony port MIDI. W razie potrzeby udziel systemowej zgody na USB. Przy kilku urządzeniach lub portach wskaż **nowy port tego samego FM‑1** i potwierdź; po końcowym restarcie aplikacja sprawdzi tożsamość zainstalowanego firmware.

<a id="multiple-fm1-devices-on-a-hub"></a>

## Kilka FM‑1 na hubie

- Nigdy nie ma domyślnie wybranego portu. Zapytania i dane trafiają wyłącznie do jawnie wskazanego urządzenia. Jednocześnie trwa jedna aktualizacja.
- Po restarcie poprzedni numer MIDI musi zniknąć. Lista ponownego połączenia wyklucza wcześniejsze numery MIDI i adresy USB pozostałych urządzeń obecnych przed danym etapem.
- Automatyczne ponowne połączenie działa tylko po zniknięciu poprzedniego numeru MIDI, gdy jest dokładnie jedno urządzenie USB i jeden dozwolony port MIDI. Przy kilku urządzeniach, także innych niż MIDI, lub kilku portach wymagany jest wybór użytkownika. Nazwa MIDI nie rozpoznaje fizycznego gniazda huba.
- Jeśli kilka instrumentów równocześnie zmieni adres albo użytkownik podłączy kolejne urządzenie, może być kilka kandydatów. Nie wybieraj na chybił trafił. Nie ma automatycznego przełączenia na inny dostępny FM‑1.
- Adres USB to identyfikator bieżącego połączenia, nie trwała nazwa instrumentu. Pierwszą identyfikację egzemplarzy można zrobić, podłączając je kolejno przed rozpoczęciem aktualizacji. Nie odłączaj urządzenia podczas zapisu.

## Biblioteka, ulubione i offline

- **Katalog, Ulubione, Pobrane, Flasher** to osobne zakładki. Karty mają podgląd, krótki opis, autora, kategorię, wersję i stan lokalnej paczki. Jeśli autor udostępnia grafikę, aplikacja ją pokazuje i zachowuje do offline; w pozostałych przypadkach pokazuje ilustrację kategorii. Pełny opis i informacje techniczne są w szczegółach.
- Kategorie: syntezatory, grooveboxy, perkusja i samplery, trackery, akordy, narzędzia, oryginalny M‑VAVE i pozostałe. Wyszukiwanie można łączyć z kategorią, **Tylko pobrane** oraz **Ulubione**, także bezpośrednio w katalogu.
- Sortowanie: nazwa A–Z, najnowsze wydania, autor A–Z, ostatnio pobrane. Ulubione i preferencje przeglądania są lokalne i pozostają po ponownym uruchomieniu aplikacji.
- Firmware zapisujemy w trwałej, prywatnej pamięci aplikacji, a nie w folderze Downloads. Paczki pozostają po zamknięciu i aktualizacji aplikacji; odinstalowanie aplikacji lub wyczyszczenie jej danych usuwa bibliotekę.
- Po pobraniu możesz włączyć przełącznik **Offline** i korzystać z **Pobranych** lub filtra **Tylko pobrane**. Do wyboru lokalnej paczki i rozpoczęcia flashowania nie jest wykonywane żadne połączenie sieciowe. Przeterminowany katalog nie blokuje już sprawdzonej lokalnej paczki. Plik na dysku przechodzi ponowną kontrolę SHA‑256 i CRC przed zapisem do urządzenia.
- Katalog jest trwale przechowywany w cache i wyświetlany od razu po otwarciu aplikacji. Przy każdym otwarciu i powrocie do aplikacji sprawdzamy dostęp do internetu; poza trybem offline przeterminowany lub brakujący katalog jest odświeżany automatycznie. Brak sieci i błąd pobrania pozostawiają poprzednią listę.
- **Przeciągnij listę w dół na samej górze** i puść po komunikacie, aby sprawdzić katalog ręcznie. Gest nie przejmuje przewijania w środku listy ani przewijania poziomego; przycisk **Sprawdź** pozostaje dostępny jako alternatywa. Oba sposoby zachowują limit źródła raz na dobę: świeży cache nie powoduje kolejnego żądania HTTP.
- Dostępną zmianę pliku pokazuje etykieta **Aktualizacja**. Można zaktualizować jedną paczkę w szczegółach lub wszystkie w zakładce **Pobrane**. Rozpoznajemy zmianę po SHA‑256, również gdy autor zachował nazwę wersji.
- Nowa paczka zastępuje starą dopiero po kompletnym pobraniu i poprawnej walidacji. Błąd aktualizacji pozostawia poprzednią wersję. Aktualizacja biblioteki nie rozpoczyna flashowania; po zmianie wybranego pliku trzeba wskazać paczkę ponownie. **Pobierz ponownie** pozwala naprawić lokalny plik. Usunięcie lokalnej paczki nie usuwa ulubionego ani nie zmienia firmware instrumentu.
- Jeśli ostatni znany katalog usunął dany firmware, zapis tej paczki jest blokowany. Dla starszej zapisanej wersji używamy informacji o zgodności należących do jej pliku, a nie do nowszej wersji. Offline nie da się poznać zmian ani wycofań, które pojawiły się po ostatnim pobraniu katalogu.

## Protokół i zgodność

- Dane katalogu: [The Firmware Hub](https://thefirmwarehub.com), [warunki wykorzystania katalogu](https://thefirmwarehub.com/kb/). Pobranie najwyżej raz na dobę, z identyfikującym User-Agent i lokalnym cache. Nowe pobrania wymagają aktualnego katalogu; lokalne, wcześniej sprawdzone paczki działają także offline.
- Automatycznie pobierane są pakiety z jednoznacznym numerem zasobu GitHub i tagiem wydania oraz przypięty oficjalny pakiet z repozytorium M‑VAVE. Pozostałe pozycje udostępniają link do autora. Aplikacja nie pobiera pakietów z mirrora Hubu.
- Przed użyciem: zgodność SHA‑256 z katalogiem, tożsamość `FM-1_<numer>`, procesor AC791N, CRC nagłówka i listy UFW, zakresy plików oraz CRC `flash.bin` i `ota.bin`. Pomocnicze zaszyfrowane pliki producenta mają CRC tekstu jawnego; chroni je suma SHA‑256 całego pakietu, nie sprawdzamy ich CRC na zaszyfrowanych bajtach.
- Nieznana/nowa kość: dopuszczane tylko pakiety oznaczone przez katalog `chip: new`. `chip: old` wymaga jawnego wyboru starszej kości. Nieznana zgodność blokuje zapis. Dla przypiętego oryginalnego V15 dostępny jest tylko wybór starszej kości, bo katalog nie deklaruje obsługi nowej. Oznaczenie w katalogu nie zastępuje testu danego firmware.
- Dwustopniowa aktualizacja, kontrola sum ramek, fragmentów SysEx, zakresów odczytów i timeoutów. Sukces wymaga odpowiedzi na końcowy sygnał programu aktualizującego oraz zgodnej tożsamości po restarcie.
- Usługa pierwszego planu i blokada usypiania utrzymują sesję przy przejściu do innej aplikacji. System nadal może przerwać proces. Nie ma przycisku przerwania zapisu.

Pozytywna kontrola tożsamości potwierdza odpowiedź urządzenia, nie odczyt kontrolny całej pamięci. Różne firmware mogą współdzielić tożsamość. Przerwanie zasilania, niewłaściwy pakiet lub błędne ponowne wskazanie instrumentu może wymagać sprzętowego odzyskania urządzenia. Automatyczne łączenie dotyczy zaplanowanych restartów w toku aktualizacji, nie dowolnej utraty połączenia podczas zapisu.

Zwykła aktualizacja MIDI nie obsługuje Bluetooth, masowego flashowania, kopii presetów/pamięci, przepisywania bootloadera, generowania sprzętowego USB_KEY, trwałego mapowania gniazd huba, importu niezweryfikowanych plików lokalnych ani pakietów o innych identyfikatorach, np. `FM1_OFFICIAL_OPEN`. Wykrywanie kości i odzyskiwanie ROM to oddzielne funkcje eksperymentalnej wersji debug opisane niżej; nie rozszerzają zgodności zwykłych aktualizacji.

<a id="building-and-testing"></a>

## Budowanie i testy

Wymagane JDK 17, Android SDK Platform 36 oraz Build Tools 35.0.0. Ustaw `ANDROID_HOME` albo `sdk.dir` w nieśledzonym `local.properties`.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.
AAB: `./gradlew :app:bundleRelease`, wynik `app/build/outputs/bundle/release/app-release.aab`. Bez konfiguracji klucza jest niepodpisany; GitHub Actions podpisuje go kluczem uploadu z sekretów.

84 testy JVM obejmują protokół i pakiety UFW, katalog, kategorie, łączenie filtrów, sortowanie, trwałość biblioteki i ustawień, aktualizacje po SHA‑256, gest odświeżania i jego anulowanie, warunki automatycznego odświeżania, zachowanie starej paczki po błędzie, korupcję pliku, zgodność starszych wersji, wycofane paczki, wykluczanie sąsiednich portów, sesje z trzema urządzeniami, logger MIDI i symulowane odzyskiwanie ROM. Dwa testy opcjonalnie czytają rzeczywiste pliki z dysku, bez dołączania firmware do repozytorium:

```sh
FM1_TEST_PACKAGES=/path/stock-v15.fwsc:/path/stock-v16.fwsc \
FM1_TEST_CATALOG=/path/catalog.json \
./gradlew :app:testDebugUnitTest
```

W najnowszej walidacji wydania wszystkie 84 testy JVM przeszły z rzeczywistymi paczkami V15/V16 oraz symulowanym odzyskiwaniem na obu typach kości; przeszły też lint i budowanie debug APK. Fizyczne odzyskiwanie USB i odbiór MIDI z wielu portów nadal wymagają testów sprzętowych. Emulator Android 16 sprawdza interfejs i sieć, nie fizyczny transport USB.

W emulatorze sprawdzono uruchomienie aplikacji, odczyt 202 pozycji katalogu, pobranie i trwały zapis Hortator 1.0.1 z kontrolą SHA‑256/CRC, zapis ulubionego, łączenie filtrów „Tylko pobrane” i „Ulubione” z kategorią, sortowanie, lokalną bibliotekę w trybie offline oraz rzeczywisty gest odświeżania. Bez urządzenia USB przycisk instalacji pozostaje zablokowany.

Użytkownik potwierdził udaną aktualizację fizycznego FM‑1. Do dalszej weryfikacji pozostają automatyczne restarty oraz kilka urządzeń na hubie: najpierw samo wykrywanie kilku instrumentów i wybór jednego, następnie kontrolowana aktualizacja z potwierdzeniem obu restartów oraz sprawdzeniem, że pozostałe instrumenty nie zmieniły firmware.

## GitHub Actions i Google Play

[Instrukcja konfiguracji](../../publication/github-actions.txt) prowadzi przez pierwsze wydanie, klucz podpisujący, konto usługi Google i sekrety GitHuba.

Automatyczna publikacja Google Play jest wyłączona w ustawieniach repozytorium do czasu konfiguracji sklepu i kluczy. Poniższe zachowanie workflow wydania dotyczy jego ponownego włączenia.

- **Android CI**: push na gałąź lub PR uruchamia testy JVM, lint i budowanie debug APK. APK i raporty są dostępne jako artefakty.
- **Google Play release**: push tagu `v<versionName>` uruchamia testy, lint wydania, budowanie podpisanego AAB i wysyłkę na tor `internal` wraz z informacjami o zmianach. Ręczne uruchomienie z wyłączonym `publish` buduje tylko AAB do pierwszego przesłania w Play Console.
- **Google Play production**: ręczne uruchomienie z wybranym `version_code` przenosi przetestowany pakiet z `internal` na `production`, bez ponownego budowania i wysyłania AAB.

Przed każdym nowym wydaniem zwiększ `versionCode`, ustaw `versionName` w `app/build.gradle` i dodaj `fastlane/metadata/android/<język>/changelogs/<versionCode>.txt`. Tag musi odpowiadać `versionName`. GitHub nie zastępuje pierwszej konfiguracji sklepu ani wymaganych testów i weryfikacji Google Play.

## Źródła techniczne i licencje

Protokół: [AL‑255/FM‑1‑RE](https://github.com/AL-255/FM-1-RE/blob/main/docs/io/11-ota-protocol.md) i jego klient `tools/fm1_ota.py` (WTFPL), [format pakietu](https://sbegueria.github.io/fm1-dev-wiki/Firmware-Package-Format.html), [Android MIDI API](https://developer.android.com/reference/android/media/midi/MidiManager). Implementacja Androida znajduje się w `app/src/main/java/pl/ekim/fm1_flasher`.

Kompilacja katalogu: The Firmware Hub, [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Aplikacja filtruje listę do obsługiwanych tożsamości, wybiera potrzebne pola i dodaje przypięty wpis stock. Opisy należą do autorów; każda pozycja pokazuje autora i link do jego repozytorium. Licencje firmware pozostają przy projektach; żaden firmware nie jest dołączony do APK ani źródeł aplikacji. Gradle Wrapper: [Apache 2.0](https://github.com/gradle/gradle/blob/v8.13.0/LICENSE).

Renderowanie Markdown: [CommonMark Java](https://github.com/commonmark/commonmark-java), [licencja BSD-2-Clause](../../third_party/commonmark-LICENSE.txt), dołączona także do aplikacji.

### Wykrywanie kości flash

Użyj **Wykryj kość (tryb download)** przy dokładnie jednym instrumencie w trybie JieLi USB download (`4C4A:8057`). Aplikacja wgrywa loader do RAM i odczytuje JEDEC ID poleceniem `FC0A`; podczas wykrywania nie kasuje ani nie programuje flash. `856014` to starsza kość FM‑1, `B36014` — nowsza. Inne identyfikatory pozostają nieznane.

Po odczycie odłącz zasilanie, uruchom instrument ponownie w zwykłym trybie MIDI, połącz się w aplikacji i wybierz wykryty typ kości. Wynik nie jest wywnioskowywany z wersji firmware ani przenoszony na inne połączenie USB. Ta ścieżka USB wymaga jeszcze sprawdzenia na fizycznym sprzęcie.

## Eksperymentalny logger MIDI dla wielu portów

Wersja debug ma pierwotną nazwę **FM-1 Flasher** i identyfikator `pl.ekim.fm1_flasher` (wersja `0.7.0-experimental`) i wymaga Androida 14+. Dane z osobnej aplikacji `.experimental` nie są przenoszone. W **MIDI Logger** wybierz wyjścia instrumentów, udziel dostępu USB i naciśnij **Start**. Logger wyłącznie odbiera dane — nie identyfikuje urządzeń, nie otwiera ich wejść MIDI ani niczego nie wysyła. Obsługuje wiele portów i urządzeń, także bez wejść MIDI.

Dziennik pokazuje czas odbioru, urządzenie/ścieżkę/port, kanał, zdekodowane MIDI oraz HEX. Przytrzymaj wpis, aby zobaczyć pełny HEX. Filtry obejmują źródło, kanał i typ; można ukryć Clock oraz Active Sensing. Eksportuje cały bufor jako UTF‑8 przez systemowy wybór pliku, niezależnie od filtrów. Bufor jest ograniczony do 5000 wpisów i 4 MiB danych; pojedynczy SysEx do 64 KiB. Utracone dane i skrócone SysEx są oznaczane, ale logger nie wykryje utraty przed dostarczeniem danych przez Androida. Zatrzymuje się po przejściu aplikacji w tło, również przy otwieraniu eksportu. Odbiór z wielu portów i 30-minutowy test obciążeniowy wymagają sprawdzenia na sprzęcie.

## Eksperymentalne odzyskiwanie ROM

Funkcja jest dostępna wyłącznie w wersji debug `0.7.0-experimental`. Odzyskiwanie przez USB nie zostało sprawdzone na fizycznym instrumencie. Otwórz **Flasher → Recovery and backups**. Odpowiadający FM‑1 można poprosić o przejście w tryb ROM download; urządzenie już w tym trybie nie wymaga połączenia MIDI. Podłącz dokładnie jedno urządzenie download (`4C4A:8057`, `WL82 UBOOT1.00`) i udziel dostępu USB. Tryb download nie jest systemem plików — nigdy go nie formatuj.

- Diagnostyka sprawdza JEDEC ID i klucz kości, dwukrotnie odczytuje całe 1 MiB, porównuje odczyty i zapisuje kopię wraz z metadanymi. Akceptowane są `856014` i `B36014` z kluczem `980F`. Nieznany obszar rozruchowy pozwala wykonać kopię, ale blokuje zapis naprawczy.
- Przygotowanie odzyskiwania dopuszcza wyłącznie oficjalny pakiet `FM-1_015` o SHA‑256 `db1642b2b6fa5c2cccb11ffd13878068bb28601678d3644049f99dc40e7edb8a`; sprawdza też hash wyodrębnionego firmware. Wymagany jest rozpoznany fabryczny obszar rozruchowy V15/V16; m-bootloader i uszkodzone obszary są odrzucane.
- Przed zapisem aplikacja pokazuje urządzenie, kość, zmieniane sektory, chronione zakresy i sumę kopii, a następnie ponownie porównuje całą pamięć z kopią. Zapisuje tylko zmienione sektory 4 KiB z zakresu `[0x4000, 0x93000)`, odczytuje je kontrolnie, a na końcu porównuje całą pamięć. Błąd transferu zatrzymuje operację; nie ma automatycznej ponownej próby ani wznowienia.
- Kopię można wyeksportować jako ZIP przez systemowy wybór pliku. Zawiera całą flash, metadane/SHA‑256 i dostępny wynik operacji. Kopie wewnętrzne znikają po odinstalowaniu aplikacji lub wyczyszczeniu jej danych — wyeksportuj kopię przed działaniem.

Zgodność bajtów nie potwierdza uruchomienia instrumentu. Po operacji odłącz zasilanie i sprawdź uruchamianie oraz połączenie MIDI. Ustawienia i presety z custom firmware lub V16 mogą nie pasować do V15. Funkcja nie naprawia bootloadera, nie przywraca dowolnych kopii ani V16. Urządzenia niewidoczne przez USB wymagają zewnętrznego sprzętu USB_KEY albo drugiego instrumentu z [M-Unbricker](https://github.com/jvitkauskas/m-unbricker).
