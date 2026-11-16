# Plan naprawczy po review z 2026-11-16

Źródło: `docs/reviews/2026-11-16-fixtures-and-project-review.md`.
Trzy niezależne zadania implementacyjne; każde prowadzi osobny agent w worktree
przygotowanym przez orkiestrator z tego samego lokalnego commita bazowego.
Baza zawiera wcześniejszą migrację fixture’ów. Zadania nie publikują aplikacji.

## 1. P1 — Konflikty kompatybilności lokalnego firmware’u

Cel: `FirmwareRepository.load()` odmawia wydania lokalnej paczki, jeżeli katalog
zawiera sprzeczne deklaracje kompatybilności dla tych samych bajtów. Zachować
kontrole usunięcia, SHA-256 i produktu oraz politykę nieprzenoszenia metadanych
nowszego binarium na starszą lokalną wersję. Wzorcem jest Androidowy
`LocalLibrary.localFlashEntry()` i istniejąca metoda `Catalog.conflicting()`.

Zakres: wspólne repozytorium i nowy, osobny plik testów konfliktów. Bez naprawiania
identyfikatorów, które należą do zadania 3.

Odbiór: test reprodukcyjny przed poprawką oraz przechodzące testy po poprawce:
konflikt old/new, konflikt produktu, brak konfliktu, starsze lokalne bajty,
katalog niedostępny i pakiet wycofany. `:shared:jvmTest`; odpowiednie testy
Androida, jeżeli zmiana dotyka współdzielonych wywołań.

## 2. P1 — Ciągłość połączeń i zaufanie do instrumentu na iOS

Cel: po utracie ciągłości Core MIDI stara tożsamość, deklaracja chipu i otwarte
połączenie nie mogą zostać użyte dla innego fizycznego instrumentu nawet przy
ponownym użyciu tych samych numerów i nazw endpointów. Informację o generacji
przekazać do przenośnego backendu; nie polegać wyłącznie na odpytywaniu listy.

Zakres: host Core MIDI, minimalne potrzebne interfejsy wspólne, przenośny backend
oraz dedykowane testy ciągłości. Sprawdzić wszystkich implementatorów zmienionych
interfejsów i zachować obsługę jawnych reconnectów podczas flashowania.

Odbiór: deterministyczne testy zmiany generacji przy tym samym ID/nazwie,
unieważnienia tożsamości/chipu, odmowy użycia starego połączenia i zachowania
poprawnego reconnectu. Testy wspólne i `PortableAppBackendTest` oraz testy nowej
logiki. Jeśli dostępny jest Xcode, także kompilacja/testy natywne. Brak macOS/Core
MIDI opisać jako ograniczenie walidacji, bez deklarowania wykonania tych testów.

## 3. P2 — Identyfikatory paczek i odtwarzanie selekcji

Cel: historyczny `id` oraz pełny klucz `firmwarehub/id` wskazują tę samą lokalną
paczkę przy `load`, selekcji, restarcie, usuwaniu i aktualizacji. Dopasowanie do
katalogu nie może błędnie zgłaszać wycofania. Operacje muszą czyścić selekcję
niezależnie od zapisanego formatu i nadal odrzucać nieprawidłowe klucze.

Zakres: wspólne repozytorium i nowy, osobny plik testów kluczy. Nie zmieniać
polityki konfliktów kompatybilności z zadania 1.

Odbiór: odtworzyć błąd `load(entry.key)` przy obecnym katalogu; testy obu formatów,
restoreSelection po restarcie, usuwania i aktualizacji wybranego pakietu oraz
wycofania i błędnych kluczy. `:shared:jvmTest`; odpowiednie testy przenośnego
backendu, jeżeli dotyczą zmian w wywołaniach.

## Zasady wykonania i złożenia wyników

- Każdy agent pracuje wyłącznie w swoim automatycznie przygotowanym worktree,
  implementuje swoje zadanie, uruchamia właściwe testy i zostawia lokalny commit.
- Każdy raportuje SHA commita, zmienione pliki, wykonane komendy i wyniki,
  ograniczenia oraz pozostałe ryzyka. Bez dalszego delegowania i publikacji/PR.
- Zadania 1 i 3 dotykają tego samego pliku, więc po ich zakończeniu nadrzędny
  agent składa zmiany, rozwiązuje ewentualny konflikt i sprawdza obie poprawki
  łącznie. Osobne pliki testów ograniczają konflikty przy integracji.
- Po zakończeniu wszystkich trzech agentów najpierw scalić komplet poprawek
  i rozwiązać konflikty. Dopiero wtedy wykonać code review scalonego zestawu.
- Testy nadrzędnego agenta uruchomić dopiero po code review: shared JVM, Android
  JVM bez Firebase, testy przenośnego backendu, lint/build debug, kontrola zasobów
  i skryptów. Własne testy każdego agenta w jego worktree pozostają częścią
  indywidualnych zadań. Zabezpieczenia recovery i rozdział syntetycznych fixture’ów
  od integracji V15/V16 pozostają wymaganiami regresji.
- Integracja w lokalnej gałęzi naprawczej; push, sklepy i tagi poza tym zadaniem.
