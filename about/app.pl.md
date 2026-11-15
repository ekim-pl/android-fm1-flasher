# FM-1 Flasher

Niezależny menedżer oprogramowania sprzętowego dla **M-VAVE FM-1**. Wymaga Androida 14 lub nowszego, USB Host/OTG, MIDI, kabla do transmisji danych i stabilnego zasilania. Zalecany jest zasilany hub.

## Instalacja firmware

1. Pobierz zgodny pakiet w [Bibliotece](app://library). Sprawdź jego źródło i zgodność z typem pamięci instrumentu.
2. Podłącz i odczytaj każdy instrument w [Urządzeniach](app://devices). Zidentyfikuj go fizycznie, wybierz typ pamięci i pakiet, a następnie dodaj do planu.
3. W [Zadaniach](app://tasks) sprawdź nazwy, wersje i skróty, a następnie uruchom plan. Zapis odbywa się na jednym instrumencie naraz.

Nie odłączaj zasilania ani USB do końca weryfikacji. Firmware może zmienić ustawienia; aktualizacja nie tworzy automatycznie kopii zapasowej. **Zatrzymaj po tym instrumencie** kończy bieżącą operację i pozostawia resztę w kolejce. Po przerwaniu aktywne zadanie jest niepotwierdzone i nie wznawia się automatycznie.

## Kilka instrumentów

Po planowanym restarcie sparuj powracający instrument na jego karcie zadania. Pozostałe połączenia muszą pozostać aktywne. Niejednoznaczność lub utrata połączenia zatrzymuje dalszy zapis. Nazwy i adresy USB nie potwierdzają fizycznej tożsamości. Wynik pomyślny potwierdza tożsamość firmware po powrocie, a nie pełny odczyt pamięci flash.

## Źródła i narzędzia

Firmware Hub jest domyślnie włączony; inne źródła skonfigurujesz w [Ustawieniach → Źródła firmware](app://settings/sources). Tryb offline używa zapisanych pakietów i nie wykryje nowych wycofań. Same skróty nie potwierdzają autentyczności wydawcy. Opcjonalne dane uwierzytelniające są szyfrowane na urządzeniu.

Funkcje eksperymentalne obejmują rejestrator MIDI i oddzielne narzędzia odzyskiwania. Fizyczne odzyskiwanie ROM nie zostało zweryfikowane i nie jest automatyczną metodą po nieudanej aktualizacji.

## Prywatność i licencje

Aplikacja nie wymaga konta i nie zawiera reklam. Standardowe kompilacje nie zawierają SDK analityki. Opcjonalne kompilacje z analityką zawierają Google Analytics for Firebase, wyłączone do czasu wyraźnej zgody. Biblioteka, nazwy urządzeń i historia zadań są przechowywane lokalnie. Serwery katalogów i pobierania otrzymują metadane połączenia. Zobacz [politykę prywatności](../PRIVACY_POLICY.md) i [licencję MIT](../LICENSE.md); firmware i komponenty zewnętrzne mają własne licencje.

Warunki opcjonalnego SDK analityki: [informacje Firebase](../third_party/firebase-analytics-NOTICE.md).
