# FM‑1 Flasher dla Androida

Eksperymentalna aplikacja Kotlin/Android 8.0+ do wybierania jednego M‑VAVE FM‑1 na hubie USB OTG, pobierania firmware z katalogu i instalowania go przez USB MIDI SysEx. Nie była testowana z fizycznym FM‑1.

## Uruchomienie

Gotowy pakiet testowy: `dist/FM1-Flasher-0.2.0-debug.apk`. To APK podpisane kluczem debug, przeznaczone do testów, nie wydanie do sklepu.

1. Zainstaluj APK na telefonie obsługującym USB Host/OTG oraz MIDI. Podłącz zasilany hub i instrumenty przewodem do transmisji danych.
2. W zakładce **Flasher** wybierz port z listy i naciśnij **Połącz z wybranym portem**. Po zgodzie Androida na USB aplikacja odczyta tożsamość tylko tego urządzenia. Nazwa MIDI nie jest kryterium wyboru: adres USB i numer MIDI rozróżniają identycznie nazwane egzemplarze.
3. Typ kości flash pozostaw jako nieznany, chyba że znasz rzeczywisty JEDEC ID tego egzemplarza. Wybór dotyczy bieżącego połączenia i resetuje się po zmianie instrumentu.
4. W katalogu otwórz podgląd firmware i wybierz **Pobierz na telefon**. Po zakończeniu pobierania wybierz **Użyj do flashowania**. Pobieranie jest oddzielone od zapisu. Repozytorium autora jest dostępne w szczegółach.
5. Po wykonaniu kopii danych narzędziem danego firmware sprawdź docelowy port i pakiet w oknie potwierdzenia, a następnie rozpocznij aktualizację.
6. Po restarcie do programu aktualizującego wybierz **nowy port tego samego FM‑1** i potwierdź. Po końcowym restarcie ponownie wskaż port, żeby odczytać tożsamość zainstalowanego firmware.

## Kilka FM‑1 na hubie

- Nigdy nie ma domyślnie wybranego portu. Zapytania i dane trafiają wyłącznie do jawnie wskazanego urządzenia. Jednocześnie trwa jedna aktualizacja.
- Po restarcie poprzedni numer MIDI musi zniknąć. Lista ponownego połączenia wyklucza wcześniejsze numery MIDI i adresy USB pozostałych urządzeń obecnych przed danym etapem.
- Aplikacja **zawsze wymaga ponownego wskazania portu** po restarcie, również gdy pojawił się tylko jeden kandydat. Nie rozpoznaje fizycznego gniazda huba i nie zakłada, że podobna nazwa lub identyczny firmware oznacza ten sam egzemplarz.
- Jeśli kilka instrumentów równocześnie zmieni adres albo użytkownik podłączy kolejne urządzenie, może być kilka kandydatów. Nie wybieraj na chybił trafił. Nie ma automatycznego przełączenia na inny dostępny FM‑1.
- Adres USB to identyfikator bieżącego połączenia, nie trwała nazwa instrumentu. Pierwszą identyfikację egzemplarzy można zrobić, podłączając je kolejno przed rozpoczęciem aktualizacji. Nie odłączaj urządzenia podczas zapisu.

## Biblioteka, ulubione i offline (0.2)

- **Katalog, Ulubione, Pobrane, Flasher** to osobne zakładki. Karty mają podgląd, krótki opis, autora, kategorię, wersję i stan lokalnej paczki. Jeśli autor udostępnia grafikę, aplikacja ją pokazuje i zachowuje do offline; w pozostałych przypadkach pokazuje ilustrację kategorii. Pełny opis i informacje techniczne są w szczegółach.
- Kategorie: syntezatory, grooveboxy, perkusja i samplery, trackery, akordy, narzędzia, oryginalny M‑VAVE i pozostałe. Wyszukiwanie można łączyć z kategorią, **Tylko pobrane** oraz **Ulubione**, także bezpośrednio w katalogu.
- Sortowanie: nazwa A–Z, najnowsze wydania, autor A–Z, ostatnio pobrane. Ulubione i preferencje przeglądania są lokalne i pozostają po ponownym uruchomieniu aplikacji.
- Firmware zapisujemy w trwałej, prywatnej pamięci aplikacji, a nie w folderze Downloads. Paczki pozostają po zamknięciu i aktualizacji aplikacji; odinstalowanie aplikacji lub wyczyszczenie jej danych usuwa bibliotekę. Wersja 0.1 trzymała pakiety tylko w RAM, więc trzeba je pobrać ponownie w 0.2.
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

Pozytywna kontrola tożsamości potwierdza odpowiedź urządzenia, nie odczyt kontrolny całej pamięci. Różne firmware mogą współdzielić tożsamość. Przerwanie zasilania, niewłaściwy pakiet lub błędne ponowne wskazanie instrumentu może wymagać sprzętowego odzyskania urządzenia. Utraconego połączenia nie próbujemy samodzielnie wznawiać ani przenosić na inny port.

Poza zakresem: aktualizacja Bluetooth, masowe flashowanie, kopie presetów/pamięci, odczyt JEDEC, niskopoziomowy tryb download/UBOOT, trwałe mapowanie gniazd huba, import niezweryfikowanych lokalnych plików i pakiety o innych identyfikatorach, np. `FM1_OFFICIAL_OPEN`.

## Budowanie i testy

Wymagane JDK 17, Android SDK Platform 35 oraz Build Tools 35.0.0. Ustaw `ANDROID_HOME` albo `sdk.dir` w nieśledzonym `local.properties`.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

51 testów JVM obejmuje protokół i pakiety UFW, katalog, kategorie, łączenie filtrów, sortowanie, trwałość biblioteki i ustawień, aktualizacje po SHA‑256, gest odświeżania i jego anulowanie, warunki automatycznego odświeżania, zachowanie starej paczki po błędzie, korupcję pliku, zgodność starszych wersji, wycofane paczki, wykluczanie sąsiednich portów i sesję z trzema urządzeniami. Dwa testy opcjonalnie czytają rzeczywiste pliki z dysku, bez dołączania firmware do repozytorium:

```sh
FM1_TEST_PACKAGES=/path/stock-v15.fwsc:/path/stock-v16.fwsc \
FM1_TEST_CATALOG=/path/catalog.json \
./gradlew :app:testDebugUnitTest
```

Zweryfikowano wszystkie 51 testów bez pominięć z katalogiem pobranym 2026‑11‑13 oraz rzeczywistymi pakietami M‑VAVE i Hortator. `lintDebug` przechodzi; pozostają ostrzeżenia dotyczące m.in. przypiętych starszych wersji narzędzi, polskich napisów i natywnych widoków. Emulator Android 16 służy do sprawdzenia UI i sieci, nie transportu fizycznego USB.

W emulatorze sprawdzono uruchomienie aplikacji, odczyt 202 pozycji katalogu, pobranie i trwały zapis Hortator 1.0.1 z kontrolą SHA‑256/CRC, zapis ulubionego, łączenie filtrów „Tylko pobrane” i „Ulubione” z kategorią, sortowanie, lokalną bibliotekę w trybie offline oraz rzeczywisty gest odświeżania. Bez urządzenia USB przycisk instalacji pozostaje zablokowany.

Przed uznaniem aplikacji za gotową do normalnego użycia potrzebny jest test na telefonie i FM‑1: najpierw samo wykrywanie kilku instrumentów i wybór jednego, następnie kontrolowana aktualizacja z potwierdzeniem obu restartów oraz sprawdzeniem, że pozostałe instrumenty nie zmieniły firmware.

## Źródła techniczne i licencje

Protokół: [AL‑255/FM‑1‑RE](https://github.com/AL-255/FM-1-RE/blob/main/docs/io/11-ota-protocol.md) i jego klient `tools/fm1_ota.py` (WTFPL), [format pakietu](https://sbegueria.github.io/fm1-dev-wiki/Firmware-Package-Format.html), [Android MIDI API](https://developer.android.com/reference/android/media/midi/MidiManager). Implementacja Androida znajduje się w `app/src/main/java/pl/fm1/flasher`.

Kompilacja katalogu: The Firmware Hub, [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Aplikacja filtruje listę do obsługiwanych tożsamości, wybiera potrzebne pola i dodaje przypięty wpis stock. Opisy należą do autorów; każda pozycja pokazuje autora i link do jego repozytorium. Licencje firmware pozostają przy projektach; żaden firmware nie jest dołączony do APK ani źródeł aplikacji. Gradle Wrapper: [Apache 2.0](https://github.com/gradle/gradle/blob/v8.13.0/LICENSE).
