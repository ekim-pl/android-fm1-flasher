# Kolory zapamiętanych FM1

Status: zaakceptowany; zaimplementowany na branchu poniżej.
Branch: `feat/fm1-device-colors`, utworzony z lokalnego `main`.

## Cel i zachowanie

- Użytkownik wybiera kolor FM1 w szczegółach urządzenia, także gdy jest ono odłączone. Proponowana paleta to sześć kolorów już używanych przez aplikację; bez edytora RGB/HEX.
- Wybór zapisuje się od razu. Próbki mają nazwy dostępne dla czytnika ekranu i widoczny znacznik zaznaczenia. Kolory mogą się powtarzać.
- Ten sam akcent pojawia się obok nazwy na liście urządzeń podłączonych i zapamiętanych, na kartach kolejki i historii oraz na pasku postępu flashowania.
- Nazwa urządzenia i tekstowy stan pozostają widoczne. Kolor urządzenia nie zastępuje znaczenia komunikatów błędów ani ostrzeżeń.
- Kolor pozostaje po restarcie i ręcznym przypisaniu ponownie podłączonego FM1 do zapamiętanego wpisu. Sam kolor nie służy do identyfikacji sprzętu.
- Zmiana koloru jest zablokowana podczas operacji oraz gdy urządzenie ma zadanie oczekujące lub uruchomione, zgodnie z istniejącymi zasadami edycji urządzeń.
- Historia przechowuje kolor z chwili dodania zadania, podobnie jak obecną kopię nazwy. Późniejsza zmiana koloru nie zmienia starych wpisów.

## Ustalenia z kodu

- `FleetDevice.color` i zapis pola `color` w `FleetStore` już istnieją. Nowe połączenia dostają automatycznie jeden z sześciu kolorów.
- `FleetScreen` pokazuje dziś kropkę tylko przy urządzeniach podłączonych; brak wyboru koloru i akcentów w `JobCard`.
- `FleetStore.pair` zachowuje kolor zapamiętanego wpisu.
- `FleetStore.attach` usuwa odłączone wpisy o domyślnych nazwach `FM-1 N`. Ręczny wybór koloru musi także oznaczać zapamiętanie wpisu, aby ustawienie nie znikało bez zmiany nazwy.
- Obsługa floty jest obecnie dostępna na Androidzie. Wspólny model i UI zostaną zaktualizowane, a backend przenośny zachowa obecne ograniczenia platformowe.

## Kolejność implementacji po akceptacji

1. Uporządkować istniejącą paletę w jednym miejscu, zachowując znaczenie indeksów 0–5. Zapewnić bezpieczny kolor zastępczy dla brakujących i nieprawidłowych wartości z zapisu.
2. Dodać akcję `DEVICE_COLOR` i obsługę w `ComposeActivity`, `FlasherService` oraz `FleetStore`. Walidować zakres i zapisywać atomowo z istniejącym przywracaniem stanu po błędzie zapisu.
3. Dodać trwałą informację o zapamiętaniu wpisu przez ręczny wybór koloru. Zachować dotychczasowe zapamiętywanie nazwanych urządzeń i przenosić tę informację przy parowaniu. Starsze pliki wczytywać bez utraty danych.
4. Dodać kolor do kopii danych `FlashJob` i jego zapisu. Nowe zadania kopiują kolor urządzenia; stare zadania korzystają przy odczycie z koloru powiązanego urządzenia, a przy jego braku z domyślnego.
5. Dodać wybór koloru przy urządzeniach online/offline, wspólne akcenty nazw oraz kolor paska postępu w `FleetScreen`, `TaskScreen` i `JobCard`. Uzupełnić tłumaczenia zgodnie z istniejącymi językami aplikacji.
6. Sprawdzić kompatybilność backendów i wykonać testy oraz kompilację odpowiednie dla dotkniętych modułów.

## Kryteria odbioru i weryfikacja

- Wybrany kolor przetrwa restart, odłączenie i ręczne parowanie; także przy domyślnej nazwie urządzenia.
- Starsze zapisy urządzeń i historii nadal się wczytują. Nieprawidłowy kolor nie wywołuje błędu renderowania ani utraty floty.
- Odrzucony lub nieudany zapis nie zmienia pozostałych ustawień, tożsamości urządzenia ani kolejki.
- Testy `FleetStoreTest` obejmą zapis/odczyt koloru, zapamiętanie, parowanie, blokadę edycji, kopię koloru w historii oraz błąd zapisu. Testy retencji i ciągłości floty sprawdzą regresje.
- Sprawdzić UI w jasnym i ciemnym motywie: kilka urządzeń o różnych kolorach, urządzenie offline, kolejka, postęp i historia; nazwy kolorów i zaznaczenie dostępne bez polegania wyłącznie na barwie.

## Poza zakresem

Dowolne kolory RGB/HEX, automatyczne rozpoznawanie fizycznego FM1, zmiany protokołu flashowania, równoległe flashowanie i rozszerzanie obsługi floty na kolejne platformy.


## Ręczne sprawdzenie w emulatorze

1. Uruchom emulator Android API 34+ i zainstaluj debug APK: `./gradlew :app:installDebug`.
2. Z listy aplikacji otwórz **FM1 Color Demo** (osobna ikona dostępna wyłącznie w debug).
3. W **Szczegółach** urządzenia wybierz kolor. Sprawdź także **Offline FM1** na dole listy.
4. Uruchom demo ponownie — wybrane kolory zostaną zachowane w osobnych preferencjach demonstracyjnych.
5. Przycisk **Pokaż / wyczyść zadania** tworzy cztery przykładowe stany. Otwórz **Zadania**, aby obejrzeć kolejkę, postęp 47% i historię.
6. Przełącz **Jasny / ciemny** i porównaj czytelność. Wyczyść zadania, aby ponownie zmieniać kolory.

Demo nie łączy się z USB ani usługą flashowania i nie zmienia właściwej listy FM1. Postęp jest statycznym przykładem, a nie symulacją protokołu. Zapis produkcyjny i ręczne parowanie są sprawdzane testami magazynu danych.

Test UI:
```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=pl.ekim.fm1_flasher.FleetUiTest
```

## Wynik weryfikacji

- APK debug zbudowany; 177 testów aplikacji i 107 testów modułu shared zaliczonych.
- Wszystkie 4 testy FleetUiTest zaliczone na emulatorze Android 16/API 36, w tym wybór koloru online/offline i blokada podczas zajętości.
- Ręcznie sprawdzono wybór, zachowanie koloru po ponownym uruchomieniu demo oraz akcenty urządzeń i zadań w jasnym/ciemnym motywie.
- Synchronizacja tłumaczeń i kontrola whitespace zaliczone.
- Próba shared:allTests zatrzymała się na istniejącym błędzie kompilacji iOS w FlashSession.kt:35 (niezgodny filtr @Throws przy AutoCloseable). Testy shared na Android/JVM zaliczone.
- Nie testowano rzeczywistego flashowania przez USB.
