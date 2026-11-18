# Wiele FM-1. Jeden plan instalacji.

FM-1 Flasher **0.10.2** oferuje lokalną bibliotekę firmware i sekwencyjną kolejkę instalacji. W sekcji **Urządzenia → MIDI i kopie zapasowe** możesz zapisywać i przywracać banki, ustawienia, sekwencje i presety. Narzędzia eksperymentalne umożliwiają zarządzanie bootloaderem, instalację firmware przez ROM i przywracanie pamięci tego samego urządzenia.


[Aktualna instrukcja](documentation.md) · [O aplikacji](../../about/README.pl.md) · [Historia wydań](https://github.com/ekim-pl/android-fm1-flasher/releases)

## Pierwsze kroki

[Instalacja i codzienne użycie](documentation.md#getting-started)

## Urządzenia · Biblioteka · Zadania

- **Urządzenia:** odczytaj każdy instrument, nadaj mu nazwę i przypisz własną pobraną paczkę.
- **Biblioteka:** przeszukuj wiele źródeł, zachowuj ulubione i używaj zweryfikowanych paczek offline.
- **Zadania:** zatwierdź jeden plan, instaluj kolejno i zachowaj osobne wyniki. Zatrzymaj kolejkę po bieżącym instrumencie bez przerywania jego zapisu.

<!-- end-quick-start -->

## Powrót po restarcie

Przy wielu instrumentach trzeba powiązać jedynego dopuszczalnego kandydata na karcie zadania. Utrata sąsiada, niejednoznaczny powrót lub reset huba blokuje dalszy zapis. Adresy USB nie oznaczają fizycznych gniazd; automatyczne parowanie wielu urządzeń jest wyłączone do testów sprzętowych. Przerwany zapis nie wznawia się automatycznie.

Używaj przewodów danych i stabilnego zasilania; zalecany jest zasilany hub. Aplikacja nie certyfikuje jakości zasilania. Port na iOS jest planowany w przyszłości. Nie ma jeszcze wydania na iOS ani ogłoszonego terminu.

## Dla deweloperów

Tworzysz firmware dla M-VAVE FM-1? Zgłoś projekt do społecznościowego katalogu Awesome FM-1 albo udostępniaj własne źródło bezpośrednio wybranym użytkownikom.

Aby dołączyć do Awesome FM-1, utwórz fork repozytorium, dodaj krótkie zgłoszenie projektu i otwórz pull request. Przy prywatnej dystrybucji opublikuj pliki `.fwsc` jako załączniki do GitHub Release i dodaj repozytorium w **Ustawienia → Źródła firmware**, używając własnego tokenu dostępu. Każdy odbiorca potrzebuje własnego dostępu do repozytorium i tokenu.

[Zgłoś projekt do Awesome FM-1](https://github.com/ekim-pl/awesome-fm1#contributing) · [Instrukcja dla deweloperów](developers.md)

## Dokumentacja

- [Wiele instrumentów przez hub](documentation.md#multiple-fm1-devices-on-a-hub)
- [Budowanie i testy](documentation.md#building-and-testing)
- [Architektura i granice platform](../../MIGRATION.md)
- [Informacje o wydaniu 0.10.2 i pliki APK](https://github.com/ekim-pl/android-fm1-flasher/releases/tag/v0.10.2)
- [Stan publikacji i ograniczenia workflow](publication.txt)
- [Budowanie witryny](pages.md)

## Prywatność i otwarty kod

Bez konta i reklam. Wersja F-Droid nie zawiera SDK analityki; wersja Google Play z Firebase domyślnie zbiera statystyki; wyłączysz je w Ustawienia → Statystyki użytkowania. Biblioteka, nazwy instrumentów, odczytane seriale i historia zadań pozostają lokalnie. Zewnętrzne serwisy katalogów/pobrań otrzymują metadane połączenia.

[Polityka prywatności](privacy.md) · [Licencja MIT](license.md) · [Licencja CommonMark](commonmark-license.md)

Kontakt: [ekim.pl@gmail.com](mailto:ekim.pl@gmail.com).

## Najnowszy film

<iframe src="https://www.youtube-nocookie.com/embed/VHEe8KECsPo" title="Najnowszy film" loading="lazy" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen style="width: 100%; max-width: 720px; aspect-ratio: 16 / 9; border: 0;"></iframe>

## Google Play

Już wkrótce w Google Play! Poszukuję beta testerów — [napisz do mnie](mailto:ekim.pl@gmail.com), jeśli chcesz dołączyć.

## F-Droid

[![Get it on F-Droid](https://img.shields.io/badge/Get%20it%20on-F--Droid-1976D2?style=for-the-badge&logo=fdroid&logoColor=white)](https://ekim-pl.github.io/android-fm1-flasher/fdroid/repo/)
![Google Play — coming soon](https://img.shields.io/badge/Google%20Play-Coming%20soon-777777?style=for-the-badge&logo=googleplay&logoColor=cccccc)

Dodaj [repozytorium projektu](https://ekim-pl.github.io/android-fm1-flasher/fdroid/repo/) do klienta F-Droid, aby instalować i aktualizować aplikację. Pliki APK są też dostępne w [wydaniach GitHub](https://github.com/ekim-pl/android-fm1-flasher/releases). Repozytorium jest niezależne od oficjalnego katalogu F-Droid; zmiana kanału wymaga zgodnych certyfikatów podpisu.
