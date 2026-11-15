# Multiplatform FM-1 Flasher — instrukcja

Niezależny menedżer firmware dla **M-VAVE FM-1**, napisany w Kotlin Multiplatform i Compose Multiplatform. Android obsługuje wiele instrumentów przez hub OTG, zapisując firmware **kolejno, na jednym instrumencie naraz**.

Dokument opisuje **0.9.0, build Android 19**, branch `task/multiplatform-rework` i implementację `6e9947b`. Przygotowano lokalny testowy APK; **0.9.0 nie opublikowano na GitHub Releases**. [Raport buildu](../../publication/qa/0.9.0-test19.txt) identyfikuje sprawdzony plik i sumę kontrolną. Późniejsze zmiany dokumentacji nie zmieniają wcześniej przekazanego APK.

[English](../../README.md) · [简体中文](../zh-CN/documentation.md) · [Architektura](../../MIGRATION.md) · [Prywatność](privacy.md)

## Obsługiwane platformy

| Funkcja | Android | Źródła hosta iOS |
| --- | --- | --- |
| Wymagania | Android 14+, USB Host/OTG i MIDI | Deployment target iOS 15; brak zweryfikowanego pliku aplikacji |
| Główny interfejs | Urządzenia · Biblioteka · Zadania | Poprzedni kontroler jednego instrumentu |
| Rejestr, trwała kolejka i historia | Wdrożone | Nieprzeniesione |
| Katalogi | Firmware Hub, opcjonalny Awesome FM-1, własne źródła | Repozytorium Hub |
| Sprawdzanie katalogów w tle | Opcjonalny WorkManager | Brak odpowiednika |
| Zapis przez MIDI | Sekwencyjne zadania w usłudze natywnej | Wspólna sesja z ręcznym powiązaniem po restarcie; bez testu sprzętowego |
| Wykrywanie kości i recovery ROM | Osobne narzędzia eksperymentalne | Niedostępne |

Identyfikator Androida to `pl.ekim.multiplatform_fm1_flasher`. Aplikacja instaluje się obok oryginalnej `pl.ekim.fm1_flasher` i nie importuje jej prywatnych danych. Interfejs obsługuje polski, angielski i chiński uproszczony oraz motyw jasny, ciemny i systemowy.

<a id="getting-started"></a>
## Pierwsza instalacja i plan aktualizacji

1. Zainstaluj testowy APK na Androidzie 14 lub nowszym. Podpis debug buildu 19 odpowiada buildowi 18: instalacja jako aktualizacja, bez odinstalowania, zachowuje bibliotekę i preferencje tego forka.
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

Wymagane są JDK 17, Android SDK platform 36 i Build Tools 35.0.0. Wrapper ustala Gradle; projekt ustala AGP 8.13.2, Kotlin 2.2.21 i Compose Multiplatform 1.9.3. Skonfiguruj SDK lokalnie przez środowisko lub narzędzia Androida.

```sh
python3 tools/sync-resources.py --check
./gradlew :shared:jvmTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Testy: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`. Katalog `dist/` jest ignorowany przez Git: przygotowana kopia buildu 19 jest lokalna i nie musi istnieć po sklonowaniu repozytorium.

Źródłem tłumaczeń interfejsu są XML w `app/src/main/res/values*`; po ich zmianie uruchom `python3 tools/sync-resources.py`. Dokumenty O aplikacji to `about/README*.md`, pakowane z licencjami przez `:app:bundleProjectDocs`. Główny README nie jest dokumentem O aplikacji.

Zobacz [architekturę i komendy testów](../../MIGRATION.md), [listę odbiorową](../../tests/android14-acceptance.txt) i [budowanie witryny](pages.md). [Odziedziczone workflow Google Play wskazują inny identyfikator aplikacji](github-actions.txt); publikacja jest osobnym działaniem.

## Stan iOS

Host Swift i metadane wspólnego kodu istnieją, ale nie zweryfikowano kompilacji Xcode ani aktualizacji fizycznego iPhone’a. Rejestr/kolejka i androidowy harmonogram wielu źródeł nie zostały przeniesione. Host zachowuje wcześniejszy interfejs jednego instrumentu i ręczny reconnect. Pozostaw aplikację na wierzchu; skończony czas pracy w tle nie zastępuje usługi Androida.

Skonfigurowane cele Kotlin to `iosArm64` i `iosSimulatorArm64`, bez symulatora Intel. `iosApp/project.yml` opisuje projekt XcodeGen, bundle ID `pl.ekim.multiplatform-fm1-flasher`, wersję 0.9.0/19 i deployment target iOS 15. Kroki dla macOS/Xcode zawiera [MIGRATION.md](../../MIGRATION.md). Kompilacja metadanych pod Linuksem nie tworzy aplikacji iOS.

## Zakres weryfikacji

Dla implementacji buildu 19 zaliczono **50 testów shared JVM i 120 testów Android JVM**; pominięto cztery testy wymagające zewnętrznych fixture’ów. Zaliczone są **29 testów instrumentacyjnych Androida** i końcowa powtórka trzech testów UI po zmianie filtrów/motywu. Przeszły buildy debug, lint, synchronizacja zasobów i metadane Kotlin/iOS. W dedykowanym emulatorze Android 16 zaktualizowano aplikację i sprawdzono prawdziwy lokalny katalog/interfejs. To wyniki zapisanej próby, nie deklaracja ponowienia całego zestawu przy każdej późniejszej edycji dokumentacji.

Nie sprawdzono fizycznego FM-1/huba ani aplikacji iOS. Zasilanie, rzeczywiste przejścia trybów/deskryptorów, unikalność seriali i pełny zapis sprzętu pozostają warunkami odbioru. [Raport buildu](../../publication/qa/0.9.0-test19.txt) zawiera identyfikację pliku i zakres prób.

## Prywatność i autorstwo

Bez konta, reklam i analityki. Biblioteka, nazwy, odczytane seriale, wybory pamięci i historia zadań pozostają lokalnie. Serwery katalogów/grafik/paczek otrzymują metadane połączenia; aplikacja nie dołącza tożsamości instrumentów do tych żądań. Szczegóły: [polityka prywatności](privacy.md).

Kod aplikacji: [MIT](../../LICENSE.md). Katalog [The Firmware Hub](https://thefirmwarehub.com): CC BY 4.0; firmware zachowuje licencje autorów. [Awesome FM-1](https://github.com/ekim-pl/awesome-fm1) jest opcjonalnym katalogiem. Informacje o zależnościach: [third_party](../../third_party/). Kontakt: [ekim.pl@gmail.com](mailto:ekim.pl@gmail.com).
