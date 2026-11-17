# FM-1 Flasher 0.10.1

Niezależny menedżer oprogramowania sprzętowego dla **M-VAVE FM-1**. Wymaga Androida 14 lub nowszego, USB Host/OTG, MIDI, kabla do transmisji danych i stabilnego zasilania. Zalecany jest zasilany hub.

## Instalacja firmware

1. Pobierz zgodny pakiet w [Bibliotece](app://library). Sprawdź jego źródło i zgodność z typem pamięci instrumentu.
2. Podłącz i odczytaj każdy instrument w [Urządzeniach](app://devices). Zidentyfikuj go fizycznie, wybierz typ pamięci i pakiet, a następnie dodaj do planu.
3. W [Zadaniach](app://tasks) sprawdź nazwy, wersje i skróty, a następnie uruchom plan. Zapis odbywa się na jednym instrumencie naraz.

Nie odłączaj zasilania ani USB do końca weryfikacji. Firmware może zmienić ustawienia; aktualizacja nie tworzy automatycznie kopii zapasowej. **Zatrzymaj po tym instrumencie** kończy bieżącą operację i pozostawia resztę w kolejce. Po przerwaniu aktywne zadanie jest niepotwierdzone i nie wznawia się automatycznie.

## Kilka instrumentów

W **Urządzenia → Szczegóły → Kolor instrumentu** wybierz jeden z sześciu kolorów, także dla odłączonego FM1. Akcent oznacza urządzenie na listach, w zadaniach i na pasku postępu. Wybór koloru zapamiętuje wpis; restart i ręczne parowanie zachowują kolor. Zadania zachowują pierwotny kolor w historii. Edycja jest zablokowana podczas zajętości aplikacji lub oczekującego/aktywnego zadania urządzenia. Kolor nie potwierdza tożsamości sprzętu.

Po planowanym restarcie sparuj powracający instrument na jego karcie zadania. Pozostałe połączenia muszą pozostać aktywne. Niejednoznaczność lub utrata połączenia zatrzymuje dalszy zapis. Nazwy i adresy USB nie potwierdzają fizycznej tożsamości. Wynik pomyślny potwierdza tożsamość firmware po powrocie, a nie pełny odczyt pamięci flash.

## Źródła i narzędzia

Firmware Hub jest domyślnie włączony; inne źródła skonfigurujesz w [Ustawieniach → Źródła firmware](app://settings/sources). Tryb offline używa zapisanych pakietów i nie wykryje nowych wycofań. Same skróty nie potwierdzają autentyczności wydawcy. Opcjonalne dane uwierzytelniające są szyfrowane na urządzeniu.

Włącz **Ustawienia → Funkcje eksperymentalne**, aby korzystać z diagnostyki, mapy sektorów, pełnych kopii, importu kopii i Loggera MIDI. Operacje ROM wymagają dokładnie jednego obsługiwanego FM-1 w trybie pobierania. Diagnostyka i kopie MBOOT wymagają wybranego urządzenia MIDI z obsługiwanym bootloaderem.

Pięć operacji zapisu pozwala instalować m-bootloader 2.4, przywracać fabryczny bootloader V15, instalować zgodne pobrane firmware przez ROM oraz przywracać kopię tego samego urządzenia z bootloaderem lub bez niego. Wybierz operację, **Przygotuj plan zapisu**, **Eksportuj kopię sprzed zapisu**, następnie **Zapisz sprawdzony plan** i potwierdź. Samo przygotowanie niczego nie zapisuje. Klucz fabryczny i MAC są chronione; kopia musi odpowiadać tożsamości urządzenia. Aplikacja weryfikuje sektory i całą pamięć, ale nie sprawdza uruchomienia. Nowo włączone operacje wymagają jeszcze prób sprzętowych; błąd zapisu bootloadera może wymagać sprzętowego odzyskiwania.

W **Ustawienia → Źródła firmware → Kolor źródła** wybierz jeden z ośmiu kolorów lub Automatyczny dla każdego źródła. Kropka przy nazwie źródła oznacza jego firmware w bibliotece, pobranych paczkach i szczegółach. Kolory są zapisywane na tym urządzeniu i można je zmieniać offline.

## Prywatność i licencje

Aplikacja nie wymaga konta i nie zawiera reklam. Wersja F-Droid nie zawiera SDK analityki. Wersja Google Play z Google Analytics for Firebase domyślnie zbiera statystyki od pierwszego uruchomienia online. Możesz je wyłączyć w każdej chwili w Ustawienia → Statystyki użytkowania. Biblioteka, nazwy urządzeń i historia zadań są przechowywane lokalnie. Serwery katalogów i pobierania otrzymują metadane połączenia. Zobacz [politykę prywatności](../PRIVACY_POLICY.md) i [licencję MIT](../LICENSE.md); firmware i komponenty zewnętrzne mają własne licencje.

Warunki opcjonalnego SDK analityki: [informacje Firebase](../third_party/firebase-analytics-NOTICE.md).

Wydawca: **Ekim** · [Kontakt](mailto:ekim.pl@gmail.com) · [Licencje zależności](../third_party/README.md). Aplikacja bezpłatna i otwartoźródłowa.

[CommonMark (BSD-2-Clause)](../third_party/commonmark-LICENSE.txt) · [Android (Apache 2.0)](../third_party/Apache-2.0.txt) · [m-flasher (MIT)](../third_party/m-flasher-LICENSE.txt)

## Podziękowania

Dziękujemy **Juliusowi Vitkauskasowi** za [M-Flasher](https://github.com/jvitkauskas/m-flasher) i [m-bootloader](https://github.com/jvitkauskas/m-bootloader). Ich otwarty kod, dokumentacja protokołu i loadery umożliwiły powstanie tych narzędzi diagnostyki, zarządzania bootloaderem i odzyskiwania pamięci. Adaptowany kod i dołączone pliki zachowują informacje o autorze i licencję MIT; zobacz [informacje o komponencie](../third_party/m-flasher-NOTICE.md) i [licencję](../third_party/m-flasher-LICENSE.txt).
