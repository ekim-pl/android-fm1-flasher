# Wiele FM-1. Jeden plan instalacji.

**FM-1 Flasher 0.9.2** to stabilne wydanie z pełnoekranowymi ustawieniami, wspólnym interfejsem providerów i uwierzytelnianiem HTTP/OAuth dla źródeł Androida. Praca z wieloma FM-1 przez OTG nadal jest sekwencyjna.

[Aktualna instrukcja](documentation.md) · [O aplikacji](../../about/README.pl.md) · [Historia wydań](https://github.com/ekim-pl/android-fm1-flasher/releases)

## Urządzenia · Biblioteka · Zadania

- **Urządzenia:** odczytaj każdy instrument, nadaj mu nazwę i przypisz własną pobraną paczkę.
- **Biblioteka:** przeszukuj wiele źródeł, zachowuj ulubione i używaj zweryfikowanych paczek offline.
- **Zadania:** zatwierdź jeden plan, instaluj kolejno i zachowaj osobne wyniki. Zatrzymaj kolejkę po bieżącym instrumencie bez przerywania jego zapisu.

## Powrót po restarcie

Przy wielu instrumentach trzeba powiązać jedynego dopuszczalnego kandydata na karcie zadania. Utrata sąsiada, niejednoznaczny powrót lub reset huba blokuje dalszy zapis. Adresy USB nie oznaczają fizycznych gniazd; automatyczne parowanie wielu urządzeń jest wyłączone do testów sprzętowych. Przerwany zapis nie wznawia się automatycznie.

Używaj przewodów danych i stabilnego zasilania; zalecany jest zasilany hub. Aplikacja nie certyfikuje jakości zasilania. Port na iOS jest planowany w przyszłości. Nie ma jeszcze wydania na iOS ani ogłoszonego terminu.

## Dla deweloperów

Tworzysz firmware dla M-VAVE FM-1? Zgłoś projekt do społecznościowego katalogu Awesome FM-1 albo udostępniaj własne źródło bezpośrednio wybranym użytkownikom.

Aby dołączyć do Awesome FM-1, utwórz fork repozytorium, dodaj krótkie zgłoszenie projektu i otwórz pull request. Przy prywatnej dystrybucji opublikuj pliki `.fwsc` jako załączniki do GitHub Release i dodaj repozytorium w **Ustawienia → Źródła firmware**, używając własnego tokenu dostępu. Każdy odbiorca potrzebuje własnego dostępu do repozytorium i tokenu.

[Zgłoś projekt do Awesome FM-1](https://github.com/ekim-pl/awesome-fm1#contributing) · [Instrukcja dla deweloperów](developers.md)

## Dokumentacja

- [Instalacja i codzienne użycie](documentation.md#getting-started)
- [Wiele instrumentów przez hub](documentation.md#multiple-fm1-devices-on-a-hub)
- [Budowanie i testy](documentation.md#building-and-testing)
- [Architektura i granice platform](../../MIGRATION.md)
- [Raport wydania](../../publication/qa/0.9.2.txt)
- [Stan publikacji i ograniczenia workflow](publication.txt)
- [Budowanie witryny](pages.md)

## Prywatność i otwarty kod

Bez konta, reklam i analityki. Biblioteka, nazwy instrumentów, odczytane seriale i historia zadań pozostają lokalnie. Zewnętrzne serwisy katalogów/pobrań otrzymują metadane połączenia.

[Polityka prywatności](privacy.md) · [Licencja MIT](license.md) · [Licencja CommonMark](commonmark-license.md)

Kontakt: [ekim.pl@gmail.com](mailto:ekim.pl@gmail.com).
