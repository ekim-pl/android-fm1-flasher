# FM‑1 Flasher

Włącz **Ustawienia → Funkcje eksperymentalne**, aby pokazać logger MIDI, wykrywanie pamięci i recovery. Ustawienie jest zapamiętywane i domyślnie wyłączone. Wyłączenie zatrzymuje logger i usuwa plan recovery; podczas operacji nie można go zmienić. Recovery nadal wymaga testów na fizycznym sprzęcie.

Niezależna, eksperymentalna aplikacja do przeglądania firmware i aktualizowania **M‑VAVE FM‑1** przez USB MIDI.

## Możliwości

- Wyszukiwanie w katalogu, ulubione i pobrane paczki dostępne offline.
- Wybór pobranego firmware w katalogu lub bezpośrednio w zakładce Flasher.
- Sprawdzanie integralności i zgodności paczki przed instalacją.
- Automatyczne łączenie po planowanym restarcie przy jednym urządzeniu USB i jednym dozwolonym porcie MIDI.
- Eksperymentalna wersja debug zawiera również odbiorczy logger MIDI, wykrywanie kości flash i ograniczone odzyskiwanie ROM; odzyskiwania nie sprawdzono na fizycznym sprzęcie.
- Motyw jasny, ciemny lub systemowy; interfejs polski, angielski i chiński uproszczony.

## Źródła firmware

**Ustawienia → Źródła firmware** służą do zarządzania niezależnymi katalogami. Firmware Hub jest domyślnie włączony; [Awesome FM-1](https://github.com/ekim-pl/awesome-fm1) domyślnie wyłączony. Dodaj katalog HTTPS, sprawdź zawartość i skonfiguruj dozwolone adresy paczek oraz grafik. Wyłączone źródła nie wykonują automatycznych żądań, także po grafiki. Sprawdź teraz pozwala na jawne jednorazowe sprawdzenie źródła wyłączonego i zachowuje minimum 24 godzin między próbami.

Biblioteka SQLite zachowuje pobrane paczki, ulubione, wybór i pochodzenie. Usunięcie subskrypcji pozostawia pliki i poznane wycofania. Każde źródło ma własną aktualność, błędy i zasady pobierania; błąd jednego nie blokuje aktualizacji pozostałych. Listę można filtrować według źródła. Opcjonalne sprawdzanie katalogów w tle jest domyślnie wyłączone i nie pobiera ani nie instaluje firmware.

Katalogi nie mają zweryfikowanego podpisu wydawcy. SHA-256 i walidacja paczki pozostają obowiązkowe; nieznana zgodność, w tym początkowy wpis SHRUB, blokuje flashowanie. Ten sam identyfikator produktu nie oznacza tego samego projektu. Flashowanie offline korzysta wyłącznie z lokalnych plików i nie wykrywa wycofań opublikowanych po ostatnim sprawdzeniu.

Autor katalogu: [The Firmware Hub](https://thefirmwarehub.com), CC BY 4.0; opisy i grafiki należą do ich autorów. Dodatkowe katalogi określają własne licencje. Firmware jest pobierane od autorów, nie z mirrora paczek Huba.

Sieć i zadania w tle: OkHttp/Okio oraz AndroidX WorkManager, [Apache 2.0](../third_party/Apache-2.0.txt); [informacje o zależnościach](../third_party/android-libraries-NOTICE.txt).

## Przed aktualizacją

Telefon musi obsługiwać USB Host/OTG i MIDI. Zrób kopię danych instrumentu, zapewnij stabilne zasilanie USB oraz potwierdź właściwy instrument i paczkę.

## Prywatność i licencje

Bez konta, reklam i analityki. Zobacz [politykę prywatności](../PRIVACY_POLICY.md).

Kontakt: [ekim.pl@gmail.com](mailto:ekim.pl@gmail.com).

Aplikacja: [licencja MIT](../LICENSE.md). Parser Markdown: [CommonMark, BSD-2-Clause](../third_party/commonmark-LICENSE.txt).
