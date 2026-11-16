# Review scalonych trzech poprawek — 2026-11-16

## Kolejność pracy

Wszystkie trzy poprawki zostały scalone lokalnie na `review/repair-base`,
a dopiero potem wykonano ten przegląd. Testy nadrzędnego agenta nie były
uruchamiane w trakcie scalania ani przed zakończeniem review.

| Zadanie | Commit agenta | Commit po scaleniu |
| --- | --- | --- |
| P1: konflikty kompatybilności | `5544730` | `7b9a76a` |
| P2: klucze i selekcja | `f2312e8` | `5c655ff` |
| P1: ciągłość MIDI | `fbfb423` | `80ef68d` |

Baza porównania: `5ea50d8`. Git automatycznie połączył wspólne zmiany
`FirmwareRepository.kt` i `PortableAppBackend.kt` bez konfliktów tekstowych.
Review objęło cały diff trzech poprawek, nowe testy, wszystkich implementatorów
`ProbeBackend` i wywołania zmienionego interfejsu oraz przekazywanie identyfikatorów
portów przez Compose.

## Ustalenie review i korekta

**P1 — test selekcji nie implementował nowego interfejsu po scaleniu.**
Agent selekcji dodał mock z `open(id: Int)`, a niezależna poprawka MIDI zmieniła
`ProbeBackend` na `open(port: ProbePort)`. Każda gałąź osobno była spójna, lecz
scalony zestaw nie skompilowałby Androidowych testów JVM.

Naprawa: dostosowano mock w `PortableSelectionTest.kt` do nowego podpisu.
Kontrolę wykonano statycznie przed uruchomieniem testów. Dodatkowo dodano
`conflictBlocksBothIdFormatsWithoutClearingSelection`, który łączy wymagania
zadań 1 i 3: oba formaty ID muszą blokować sprzeczne metadane, pozostawiając
selekcję i lokalne bajty bez zmian.

Po tej korekcie review nie wykazało dalszych blokujących ustaleń w scalonym diffie.
Nie jest to deklaracja walidacji natywnego mostu iOS na Xcode.

## Sprawdzone zachowania

- `load()` najpierw identyfikuje zapisaną paczkę, dopasowuje aktualny katalog po
  jej pełnym kluczu, wybiera metadane dla tych samych bajtów i sprawdza konflikt
  przed odczytem firmware’u. Zachowane są kontrole wycofania, rozmiaru, SHA-256
  i tożsamości produktu oraz ochrona starszego lokalnego binarium.
- USE zapisuje pełny klucz. Odczyt starych zapisów nadal akceptuje historyczne ID.
  Usunięcie lub aktualizacja czyści oba formaty selekcji tej samej paczki;
  testy sprawdzają zachowanie wyboru innego pakietu i błędne klucze.
- Destrukcyjne akcje przenośnego backendu odświeżają listę portów i sprawdzają
  aktualną generację. Ta generacja jest częścią wyboru w UI, otwarcia połączenia
  i jawnego reconnectu. Utrata ciągłości usuwa zapisaną tożsamość i deklarację chipu.
- Swift rejestruje zdarzenia dodania, usunięcia, zmiany właściwości i błędów I/O.
  Zachowuje generacje usuniętych obiektów i sprawdza aktualność otwartych połączeń
  przed I/O. Stan generacji i odczyt par endpointów są chronione tym samym zamkiem.
  Konstrukcje `ProbePort` i `Endpoint` w Swift przekazują nowe pole generacji.
- Powiadomienie zbiorcze `msgSetupChanged` jest ignorowane przy jawnej obsłudze
  szczegółowych zdarzeń, zgodnie z [dokumentacją Apple](https://developer.apple.com/documentation/coremidi/midinotificationmessageid/msgsetupchanged).
  Wątkowość callbacku porównano z [MIDINotifyBlock](https://developer.apple.com/documentation/coremidi/midinotifyblock).

## Walidacja po review

Review statyczne i ponowny przegląd drobnej korekty zakończono przed uruchomieniem
wspólnych testów. Wyniki zostaną uzupełnione po zakończeniu ich wykonania.

## Ograniczenia

Linux nie ma Swift/Xcode/CoreMIDI. Testy JVM symulują generacje i utratę ciągłości;
nie potwierdzają kompilacji mostu Swift/Kotlin ani dostarczania zdarzeń przez
rzeczywisty Core MIDI. Te dwa elementy wymagają sprawdzenia na macOS/iOS przed
natywną dystrybucją. Fizyczne flashowanie instrumentu pozostaje poza walidacją.
