# Logger MIDI z wielu portów

Status: zaimplementowano eksperymentalny logger (2026-11-14); poniżej zachowano plan i kryteria odbioru. Testy fizycznych portów oraz obciążenia 30 min pozostają do wykonania.
Worktree pozostaje na gałęzi `plan/fm1-live-control`. Ten dokument zastępuje plan podglądu i sterowania FM1.

## Cel

Dodać zakładkę „MIDI Logger” odbierającą jednocześnie komunikaty z wielu wybranych portów USB MIDI. Logger działa pasywnie: nie wysyła zapytań identyfikacyjnych, MIDI Thru, sterowania ani komend OTA. Nie wymaga zmian firmware i nie ogranicza wyboru do FM1. Rejestruje wyłącznie komunikaty rzeczywiście wysyłane przez urządzenia; nie ujawni zmian panelu, których firmware nie wysyła przez MIDI.

Pierwsza wersja obejmuje USB MIDI byte-stream, zgodnie z obecnym zakresem aplikacji. BLE, MIDI 2.0/UMP, odtwarzanie logu i nagrywanie w tle są poza zakresem.

## Interfejs i zachowanie

- Lista urządzeń i ich portów z wielokrotnym wyborem. Monitorowane są Androidowe porty `TYPE_OUTPUT`, czyli dane wysyłane przez instrument do telefonu; opisać kierunek jednoznacznie w UI.
- Start/Stop, Wstrzymaj przewijanie, Wyczyść. Wstrzymanie przewijania nie zatrzymuje odbioru; Stop zamyka porty i zachowuje widoczny log.
- Każdy wpis: czas od rozpoczęcia sesji, nazwa urządzenia, numer/nazwa portu, typ komunikatu, kanał tam gdzie występuje, zdekodowane dane i surowe bajty HEX. Numery kanałów i nut pokazywać jednoznacznie, np. kanały 1–16, nuta jako numer MIDI.
- Filtry źródła, kanału i typu; filtry dotyczą widoku, nie zawartości bufora. Oddzielny filtr dla częstych Clock/Active Sensing z licznikami, aby nie zasłaniały nut i CC.
- Ograniczony bufor ostatnich 5000 wpisów, dodatkowo limit 4 MiB danych surowych; usuwanie najstarszych z widocznym licznikiem. Limity traktować jako początkowe wartości do sprawdzenia wydajności.
- Odłączenie lub błąd pojedynczego portu daje oznaczony wpis i status źródła; pozostałe porty nadal rejestrują. Nowo podłączone urządzenia wymagają wyboru, nie zastępują automatycznie starego źródła.
- Eksport bieżącego bufora do tekstu UTF-8 przez systemowy wybór pliku, z nagłówkiem źródeł, początkiem sesji, bajtami HEX oraz licznikami utraconych/uciętych danych. Eksport niezależny od filtrów widoku, wyraźnie opisany w UI. Bez automatycznego zapisu.

## Obecny kod i potrzebne zmiany

| Miejsce | Zmiana |
| --- | --- |
| `AndroidMidi.kt` | Osobno wyliczać dostępne porty odbiorcze. Obecne `devices()` wymaga wejścia i wyjścia i pomija urządzenia tylko nadające; `open()` otwiera pierwszą parę i jest przeznaczone dla flashera. Zachować ten kontrakt OTA. |
| Nowy `MidiMonitor.kt` | Jeden `MidiDevice` na urządzenie i po jednym odbiorniku oraz parserze na każdy wybrany output. Otwierać wyłącznie outputy; nie zajmować inputów instrumentu. Obsłużyć częściowy sukces i błędy otwierania. |
| Nowy `MidiStreamParser.kt` | Dekodować pełny strumień MIDI zamiast używać `SysExAssembler`, który pomija real-time i zwykłe komunikaty. Parser bez zależności od Androida. |
| Nowy `MidiLog.kt` | Wpisy ze źródłem, czasem, kolejnością i surowymi danymi; bufor z limitami, liczniki i snapshot eksportu. |
| `FlasherService.kt` | Właściciel monitora i jego cyklu życia. Stan loggera oddzielony od `busy` biblioteki i logów statusu flashera. |
| `MainActivity.kt`, `CatalogBrowser.kt`, nowy `MidiLoggerPanel.kt` | Nowa zakładka, wybór portów, akcje, filtry i wirtualizowana lista, np. natywny `ListView`, bez przebudowy całej listy na każdy komunikat. |
| Zasoby tekstowe i README | PL/EN/zh-CN, opisy kierunku transmisji, limitów i eksportu. Zachować styl `Ui`, motyw i układ pionowy/poziomy. |

Nie dodawać nowych bibliotek. Wykorzystać istniejący mechanizm uprawnień USB po dostosowaniu wyboru do urządzeń tylko nadających. Przy Start ponownie sprawdzić dostępność i uprawnienia każdego źródła; odmowa jednego nie blokuje reszty.

## Parser i czas

1. Obsłużyć Note On/Off, poly pressure, CC, Program Change, channel pressure i pitch bend; przechowywać oryginalną postać komunikatu. Note On z velocity 0 opisać również jako semantyczny Note Off.
2. Obsłużyć System Common, real-time i SysEx, w tym fragmentację między callbackami, wiele wiadomości w callbacku i running status. Real-time w środku wiadomości emituje osobny wpis i nie przerywa składania; System Common/SysEx resetuje running status zgodnie z MIDI. Nie sklejać danych między portami.
3. Zachować bajty i oznaczać komunikaty nieznane, nieprawidłowe oraz przerwane, zamiast zgadywać brakujące dane. Limit pojedynczego SysEx: początkowo 64 KiB, po przekroczeniu oznaczyć ucięcie i pomijać payload do końca lub nowego statusu bez nieograniczonej alokacji. Odebrane real-time nadal logować.
4. Zapisać monotoniczny czas odbioru i timestamp Android MIDI, gdy jest dostępny; przy zerowym timestampie użyć czasu odbioru. Czas ścienny początku sesji służy eksportowi. Nie deklarować dokładnej kolejności fizycznych zdarzeń między urządzeniami: stabilny porządek logu wyznacza czas odbioru i numer sekwencyjny.
5. Callback MIDI wykonuje tylko ograniczone kopiowanie do kolejki; pojedynczy worker dekoduje i aktualizuje bufor. Aktualizacje UI grupować, np. co 50 ms. Parser osobny dla każdego portu.
6. Kolejka wejścia również musi mieć limit bajtowy. Przy przepełnieniu zliczać utracone dane osobno dla źródła, oznaczyć lukę i zresetować jego parser przed kolejnymi danymi, aby nie tworzyć fałszywych wiadomości z fragmentów sprzed i po utracie. Nie zapisywać jednego wpisu błędu dla każdego utraconego pakietu.

## Cykl życia i konflikt z aktualizacją

- Logger działa przy widocznej aplikacji. Przejście do tła zatrzymuje odbiór i zwalnia porty; po powrocie użytkownik wznawia rejestrację. Rotacja zachowuje bufor i wybór, nie tworzy drugich odbiorników; ewentualna przerwa jest oznaczona.
- Zamknięcie źródła odłącza receiver i zamyka port; urządzenie zamknąć po zamknięciu ostatniego jego portu. Zabezpieczyć spóźniony callback otwierania po Stop i pakiety ze starej generacji sesji.
- Nie wykonywać blokującego odbioru na istniejącym workerze katalogu/flashowania ani na głównym wątku. `onDestroy` usługi zamyka monitor i jego worker.
- Na pierwszą wersję zatrzymać cały logger przed flashowaniem, po atomowym zablokowaniu nowego Start. Poczekać na zwolnienie portów, dopiero potem uruchomić istniejącą procedurę OTA. Błąd zatrzymania blokuje flash. Bufor pozostaje dostępny; wznowienie po flashu jest ręczne.
- Podczas flashowania i zmiany trybu USB przez wykrywanie chipu logger jest niedostępny. Nie mieszać odpowiedzi OTA z odbiorem monitora ani zmieniać istniejących zasad `ReconnectGate`.
- Usunięcie USB, zajęty port i cofnięcie uprawnień obsłużyć per źródło. Nie utożsamiać numeru portu i nazwy urządzenia z trwałym identyfikatorem fizycznego gniazda huba.

## Etapy implementacji

1. Model logu, parser i jego testy jednostkowe; ustalić tekstowy format eksportu z przykładami dla zwykłego MIDI, SysEx i luk danych.
2. Monitor wielu outputów, uprawnienia, cykl życia i wyłączność z flasherem. Testy z fałszywymi portami potwierdzają brak jakiegokolwiek wysyłania MIDI i poprawne zamykanie po częściowym otwarciu.
3. Zakładka loggera, wirtualizowany widok, filtry, liczniki, przewijanie i eksport przez systemowy wybór pliku. Snapshot eksportu nie blokuje odbioru; błąd/anulowanie eksportu nie usuwa bufora.
4. Test na fizycznym Androidzie z hubem i co najmniej dwoma źródłami, aktualizacja README. Emulator potwierdza UI, nie rzeczywistą komunikację USB MIDI.

## Testy i odbiór

- Parser: wszystkie długości wiadomości, podział każdego komunikatu na różne fragmenty, running status, real-time przeplatane z SysEx i nutami, urwany/za duży SysEx, nieznane statusy, reset po utracie danych. Dwa porty z różnymi running statusami nie wpływają na siebie.
- Model: limity liczby wpisów i bajtów, deterministyczny porządek, filtry bez usuwania danych, liczniki strat, eksport HEX i oznaczanie ucięć.
- Monitor: wiele portów jednego urządzenia i wiele urządzeń, odmowa uprawnienia, błąd jednego otwarcia, Stop podczas otwierania, odłączenie, brak automatycznego przejęcia sąsiedniego urządzenia, ponowny Start bez starych callbacków.
- Integracja UI: rotacja, przejście do tła, pauza przewijania, czyszczenie podczas odbioru, eksport i jego anulowanie, PL/EN/zh-CN, motywy i duża czcionka.
- Regresja: logger zatrzymany przed OTA, flasher zachowuje dotychczasową identyfikację i reconnect; pobieranie katalogu nie zatrzymuje rejestracji.
- Sprzęt: co najmniej dwa wybrane porty równocześnie dostarczają poprawnie oznaczone nuty, CC, Clock i SysEx. Logger niczego nie wysyła; zmiany niewysyłane przez urządzenie nie są oczekiwane w logu.
- Próba obciążenia 30 min: zużycie pamięci stabilizuje się na limitach, UI pozostaje responsywne, odłączenie źródła nie zatrzymuje innych. Wszystkie straty w kolejce aplikacji są jawne; logger nie gwarantuje wykrycia strat przed callbackiem Androida.
- Po implementacji uruchomić `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` oraz testy sprzętowe. Przy zmianie wyłącznie tego planu nie uruchamiano testów aplikacji.

## Ograniczenia

Nie potrzeba ingerencji w firmware. Dostępność danych zależy od tego, co urządzenie nadaje przez wybrany port. Nazwa nowego pliku odpowiada aktualnemu zakresowi; nazwa gałęzi pozostaje po poprzednim planie. Implementacja eksperymentalna jest dostępna w tym worktree. Nie wykonano weryfikacji na fizycznym FM1.
