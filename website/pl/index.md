# Wiele FM-1. Jeden plan instalacji.

**Multiplatform FM-1 Flasher 0.9.1 / 22** to prerelease na Androida 14+ do obsługi wielu FM-1 przez hub OTG. Dodaje trwały log operacji, eksport i czyszczenie logu w trybie eksperymentalnym oraz bezpieczne czyszczenie pobranych firmware. Właściciel potwierdził udane testy buildu 20 na fizycznych instrumentach.

[Aktualna instrukcja](documentation.md) · [O aplikacji](../../about/README.pl.md) · [Historia wydań](https://github.com/ekim-pl/android-fm1-flasher/releases)

## Urządzenia · Biblioteka · Zadania

- **Urządzenia:** odczytaj każdy instrument, nadaj mu nazwę i przypisz własną pobraną paczkę.
- **Biblioteka:** przeszukuj wiele źródeł, zachowuj ulubione i używaj zweryfikowanych paczek offline.
- **Zadania:** zatwierdź jeden plan, instaluj kolejno i zachowaj osobne wyniki. Zatrzymaj kolejkę po bieżącym instrumencie bez przerywania jego zapisu.

## Powrót po restarcie

Przy wielu instrumentach trzeba powiązać jedynego dopuszczalnego kandydata na karcie zadania. Utrata sąsiada, niejednoznaczny powrót lub reset huba blokuje dalszy zapis. Adresy USB nie oznaczają fizycznych gniazd; automatyczne parowanie wielu urządzeń jest wyłączone do testów sprzętowych. Przerwany zapis nie wznawia się automatycznie.

Używaj przewodów danych i stabilnego zasilania; zalecany jest zasilany hub. Aplikacja nie certyfikuje jakości zasilania. Nie zweryfikowano fizycznego FM-1/huba ani pliku aplikacji iOS dla tego buildu. Host iOS zachowuje wcześniejszy kontroler jednego instrumentu, bez kolejki Androida.

## Dokumentacja

- [Instalacja i codzienne użycie](documentation.md#getting-started)
- [Wiele instrumentów przez hub](documentation.md#multiple-fm1-devices-on-a-hub)
- [Budowanie i testy](documentation.md#building-and-testing)
- [Architektura i granice platform](../../MIGRATION.md)
- [Raport testowego buildu](../../publication/qa/0.9.1-test22.txt)
- [Stan publikacji i ograniczenia workflow](publication.txt)
- [Budowanie witryny](pages.md)

## Prywatność i otwarty kod

Bez konta, reklam i analityki. Biblioteka, nazwy instrumentów, odczytane seriale i historia zadań pozostają lokalnie. Zewnętrzne serwisy katalogów/pobrań otrzymują metadane połączenia.

[Polityka prywatności](privacy.md) · [Licencja MIT](license.md) · [Licencja CommonMark](commonmark-license.md)

Kontakt: [ekim.pl@gmail.com](mailto:ekim.pl@gmail.com).
