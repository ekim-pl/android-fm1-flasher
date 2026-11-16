# Przegląd projektu i zmiana fixture’ów — 2026-11-16

Aktualizacja po naprawach: trzy poniższe ustalenia otrzymały poprawki w lokalnej
gałęzi `review/repair-base`. Wyniki review po scaleniu oraz testów zawiera
[raport końcowy](2026-11-16-merged-repair-review.md). Poniższy opis zachowuje
stan i dowody z pierwotnego przeglądu; walidacja natywnego iOS nadal wymaga Xcode.

Przegląd obejmuje kod Androida, wspólną logikę Kotlin, interfejs Compose, host iOS,
konfigurację buildów, workflowy publikacji, skrypty i dokumentację. Priorytetem były
granice zaufania: wybór urządzenia, kompatybilność firmware’u, recovery, pobieranie
oraz pochodzenie publikowanego artefaktu. To przegląd statyczny z testami JVM i
Androida; nie zastępuje walidacji fizycznego instrumentu ani builda Xcode.

## Otwarte ustalenia

### P1 — Lokalny firmware nie jest blokowany przy konflikcie typu pamięci na iOS

Miejsce: [shared/src/commonMain/kotlin/pl/ekim/fm1_flasher/FirmwareRepository.kt:165](/home/mike/.t3/projects/android-fm1-flasher/shared/src/commonMain/kotlin/pl/ekim/fm1_flasher/FirmwareRepository.kt:165).
`load()` wybiera lokalną/bieżącą metadaną, ale nie sprawdza
`catalog.conflicting(effective)`. Dwa wpisy opisujące ten sam SHA-256 jako `old`
i `new` pozostają więc dostępne do flashowania w przenośnym backendzie. Wpis `new`
może przejść `FlashChip.allows()` także przy nieznanym chipie. Androidowy
`LocalLibrary.localFlashEntry()` zawiera kontrolę konfliktów, więc zachowanie
platform jest różne.

Potwierdzenie: tymczasowy test JVM zapisał syntetyczny pakiet `demo`, następnie
przekazał do `load()` katalog z `demo/chip=new` oraz `conflict/chip=old` o tym samym
hashu. `catalog.conflicting(saved)` zwróciło `true`, a `assertFails { repo.load(...) }`
nie przeszło — pakiet został zaakceptowany. Log: [build/reports/project-review/repository-repro.log](/home/mike/.t3/projects/android-fm1-flasher/build/reports/project-review/repository-repro.log).

Zalecenie: przed parsowaniem i zwróceniem pakietu sprawdzić konflikt metadanych,
a następnie dodać test regresji do wspólnego repozytorium.

### P1 — iOS nie unieważnia zaufania do instrumentu po ponownym użyciu endpointów

Miejsca: `iosApp/Sources/CoreMidiBackend.swift:51`, `:179`, `:185` oraz
`composeApp/src/commonMain/kotlin/pl/ekim/fm1_flasher/PortableAppBackend.kt:80`.
Callback `MIDIClientCreateWithBlock` ignoruje zmiany topologii. Połączenie
porównuje tylko bieżącą parę endpointów i etykietę; `Endpoint` ma domyślną generację 0.
`refreshPorts()` zachowuje tożsamość i wybrany chip, dopóki numer portu nadal istnieje.

Jeżeli odłączenie i ponowne przyłączenie innego FM-1 o tej samej nazwie odtworzy
te same numery endpointów pomiędzy kontrolami, zapisane rozpoznanie chipu może
zostać zastosowane do innego instrumentu. Ponowny odczyt identyfikacji firmware’u
nie rozróżnia dwóch instrumentów z tą samą wersją. Android ma osobne generacje
połączeń i obsługę zdarzeń odłączenia.

Zalecenie: obsłużyć zdarzenia Core MIDI i generacje połączeń, unieważniając
zapisaną tożsamość/chip po utracie ciągłości. Potrzebny test wymiany urządzenia
z ponownym użyciem endpointów. Ustalenie statyczne; nie odtworzono recyklingu
endpointów na fizycznym iOS.

### P2 — Klucz `firmwarehub/...` uniemożliwia wybór lokalnego firmware’u na iOS

Miejsce: [shared/src/commonMain/kotlin/pl/ekim/fm1_flasher/FirmwareRepository.kt:163](/home/mike/.t3/projects/android-fm1-flasher/shared/src/commonMain/kotlin/pl/ekim/fm1_flasher/FirmwareRepository.kt:163).
`recordPath()` usuwa prefiks `firmwarehub/`, a `load()` akceptuje zarówno `id`,
jak i `entry.key`. Później wyszukuje jednak ofertę przez `entry.id == id`.
Interfejs przekazuje `entry.key`, więc przy istniejącym katalogu poprawnie pobrana
paczka kończy się błędem `firmware_removed`. Dotyczy to również odtwarzania
zapisanej selekcji. `remove()` porównuje zapisany wybór tylko z przekazanym kluczem,
co dodatkowo może pozostawić starszy format identyfikatora w ustawieniach.

Potwierdzenie: tymczasowy test zapisał `demo`, odświeżył syntetyczny katalog i
wywołał `repo.load(entry().key)`; test zakończył się błędem zamiast zwrócić pakiet.
Log: [build/reports/project-review/repository-repro.log](/home/mike/.t3/projects/android-fm1-flasher/build/reports/project-review/repository-repro.log).

Zalecenie: normalizować identyfikatory na wejściu albo dopasowywać ofertę do
klucza zapisanej paczki; objąć testami zarówno stary `id`, jak i pełny `key`.

Powyższe problemy istnieją w kodzie niezwiązanym z migracją fixture’ów.
Pozostawiono je jako jawne ustalenia review; tymczasowe testy reprodukcyjne
usunięto po zapisaniu wyników, a zwykły zestaw testów uruchomiono ponownie.

## Wdrożone zmiany

- Debug, GitHub prerelease i walidacja unsigned F-Droid używają syntetycznych
  pakietów i katalogu. Nie wymagają zmiennych archiwum ani dostępu do firmware’u.
- Istniejący generator pakietów obsługuje różne produkty i zawartość flash.
- Wewnętrzny silnik recovery jest wspólny dla testów i produkcji. Produkcyjne
  wywołania `Recovery` nadal mają stały hash paczki V15, hash jej regionu,
  tożsamość oraz listę rozpoznanych bootloaderów. Nie dodano opcji zmiany tej
  polityki przez konfigurację aplikacji.
- Zadanie `:app:firmwareIntegrationTest` jest wyłączone ze zwykłych testów.
  Korzysta z rzeczywistych V15/V16: sprawdza parser i integralność, recovery V15
  na obu chipach w symulacji, ochronę danych oraz odrzucanie V16 przez recovery.
- [scripts/firmware-fixtures.json](/home/mike/.t3/projects/android-fm1-flasher/scripts/firmware-fixtures.json) przypina oficjalne źródła i SHA-256.
  Pobieranie jest ograniczone rozmiarem i do HTTPS, a brak/zmiana paczki powoduje
  błąd. V15 jest przypięte do commita producenta. V16 ma zmienny URL producenta,
  ale zmiana bajtów nie przechodzi weryfikacji SHA-256.
- Tagged Google Play i podpisane wydania F-Droid wymagają integracji przed
  utworzeniem produkcyjnego artefaktu. W manifeście artefaktu zapisują commit
  oraz hashe obu testowanych paczek. Publikacja i upload sprawdzają te dane.
- Promocja Google Play wymaga teraz `tag`, `run_id` i `version_code`, żeby
  zweryfikować pochodzenie produkcyjnego bundle’a przed promocją.
- Weryfikacja raportów odrzuca brakujące, puste, pominięte i nieudane testy.
  Dodatkowo wymaga kluczowych scenariuszy integracyjnych.

## Kontrole i ograniczenia

Przeszły: 76 testów shared JVM, 143 testy Android JVM, 18 integracyjnych V15/V16,
17 testów skryptów oraz 45 testów na emulatorze Android 16 (11 klas). Testy JVM
i integracyjne mają zero pominięć. Przeszły buildy i lint APK bez Firebase,
debug z testowym Firebase oraz AAB z produkcyjnym Firebase. Sprawdzono również
podpis debug APK, brak SDK śledzących w unsigned F-Droid APK, konfigurację Gradle
obu środowisk, actionlint, zasoby i 52 strony dokumentacji. Lint zgłasza ostrzeżenia,
ale bez błędów.

Potwierdzono osobno działanie syntetycznych testów bez katalogu firmware’u oraz
błąd integracji przy jego braku; po przywróceniu paczek integracja ponownie przeszła.
Automatyczne wykrywanie klas starego runnera instrumentation zakończyło się
`Process crashed`; uruchomienie każdej klasy osobno, jak w CI, przeszło w całości.
Ręczny podgląd UI został zablokowany przez `DEVICE_IN_USE` również po jednej
próbie oczekiwania. Nie przejmowano ani nie zamykano innej sesji.

Szczegółowe wyniki znajdują się w [build/reports/project-review/validation.txt](/home/mike/.t3/projects/android-fm1-flasher/build/reports/project-review/validation.txt).
Raporty HTML testów i lint są w standardowych katalogach `app/build/reports/`
oraz `shared/build/reports/`. Reprodukcyjne testy ustaleń P1/P2 są celowo
odnotowane jako nieudane próby sprawdzenia oczekiwanego bezpiecznego zachowania,
oddzielnie od końcowego zestawu testów implementacji.

Skrypt `audit_dependencies.py` sprawdził 639 wersji z metadanych weryfikacji,
requirements i lockfile’ów; zgłosił 104 dopasowania do advisory OSV. Te metadane
obejmują również narzędzia budowania i historyczne wersje, więc wynik nie oznacza
104 podatności w APK. Wymaga osobnego przypisania do faktycznie użytych zależności
i analizy osiągalności. Log zachowano jako `dependency-audit.log`.

Nie wykonywano flashowania fizycznego FM-1, testów USB recovery na sprzęcie,
builda Xcode ani wysyłania do sklepów. iOS Simulator nie jest dostępny na tym
hoście Linux. Produkcyjnych kluczy podpisujących i kont sklepowych nie używano.
Zmiany pozostają lokalne; istniejący tag `v0.10.0` nadal wskazuje stary commit.
Wydanie ze zmienionym kodem wymaga jego zacommitowania i nowego, zgodnego tagu.
