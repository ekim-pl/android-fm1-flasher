# Firmware pod ręką.

FM‑1 Flasher to niezależna, eksperymentalna aplikacja na **Androida 14+** do przeglądania firmware i aktualizowania **M‑VAVE FM‑1 przez USB MIDI**.

[Przeczytaj instrukcję](documentation.md) · [O aplikacji](../../about/README.pl.md) · [Pobierz testowe APK](https://github.com/ekim-pl/android-fm1-flasher/releases)

## Od katalogu do instrumentu

- **Znajdź firmware.** Przeglądaj katalog The Firmware Hub, wyszukuj i zapisuj ulubione.
- **Przygotuj bibliotekę.** Pobierz paczki i korzystaj z nich offline. Aplikacja sprawdza integralność i zgodność przed zapisem.
- **Wybierz swój FM‑1.** Wskaż konkretny port USB MIDI, również gdy korzystasz z huba z kilkoma instrumentami.
- **Aktualizuj świadomie.** Sprawdź wybrany instrument i paczkę przed potwierdzeniem instalacji.
- **Narzędzia eksperymentalne.** Osobna wersja debug zawiera odbiorczy logger MIDI, wykrywanie kości flash i ograniczone odzyskiwanie ROM. Odzyskiwania ROM nie sprawdzono na fizycznym sprzęcie.

## Zanim rozpoczniesz

Potrzebujesz telefonu z obsługą USB Host/OTG i MIDI, przewodu do transmisji danych oraz stabilnego zasilania instrumentu. Wykonaj kopię swoich danych narzędziem danego firmware.

**Aplikacja jest eksperymentalna.** Przerwanie zasilania lub użycie niewłaściwej paczki może wymagać sprzętowego odzyskania urządzenia. Automatyczne ponowne łączenie po restartach i obsługa kilku urządzeń wymagają dalszych testów na sprzęcie.

## Dokumentacja

- [Instalacja, aktualizacja firmware i praca offline](documentation.md#getting-started)
- [Kilka instrumentów na hubie USB](documentation.md#multiple-fm1-devices-on-a-hub)
- [Budowanie aplikacji i testy](documentation.md#building-and-testing)
- [Konfiguracja publikacji w Google Play](github-actions.txt)
- [Materiały do publikacji](publication.txt)
- [Budowanie i publikacja tej strony](pages.md)

## Prywatność i otwarte źródła

Bez konta, reklam i analityki. Biblioteka, ulubione i ustawienia są przechowywane lokalnie. Pobieranie katalogu oraz firmware wymaga połączeń z serwisami zewnętrznymi.

[Polityka prywatności](privacy.md) · [Licencja MIT](license.md) · [Licencja parsera Markdown w aplikacji](commonmark-license.md)

Kontakt: [ekim.pl@gmail.com](mailto:ekim.pl@gmail.com).
