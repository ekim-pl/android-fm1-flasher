# FM-1 Flasher 0.10.1

Niezależny menedżer firmware dla **M-VAVE FM-1**. Android organizuje instrumenty podłączone przez hub OTG w **Urządzenia · Bibliotekę · Zadania**, zapisując firmware na jednym instrumencie naraz.

## Twoje instrumenty

Odczytaj każdy instrument w **Urządzeniach**, nadaj mu nazwę w **Szczegółach**, wskaż typ pamięci na podstawie znanego sprzętu i wybierz pobraną paczkę. **Dodaj do planu** wiąże urządzenie z paczką. Nazwy i kolory porządkują widok; nie są dowodem tożsamości fizycznego egzemplarza.

## Jeden plan instalacji

W **Zadaniach** sprawdź nazwy urządzeń, wersje i sumy paczek, a potem wybierz **Zainstaluj na N instrumentach**. To zatwierdza plan bez dodatkowego zwykłego okna potwierdzenia. Każde zadanie jest sprawdzane przed transferem. **Zatrzymaj po tym instrumencie** kończy bieżącą operację i pozostawia resztę kolejki.

Historia zapisuje wynik i etapy każdego zadania. Po utracie procesu aktywne zadanie jest niepotwierdzone; zapis nigdy nie wznawia się sam od zapisanego procentu. Zachowaj USB i zasilanie do końca weryfikacji. Firmware może zmienić ustawienia; OTA nie tworzy automatycznej kopii danych.

## Powrót przez hub

Przy wielu instrumentach powiąż powracający egzemplarz **na karcie zadania** po zaplanowanym restarcie. Pozostałe połączenia muszą zachować ciągłość, a kandydat może być tylko jeden. Reset huba, utrata sąsiada, sprzeczny serial lub niejednoznaczne porty MIDI blokują dalszy zapis.

Adres USB nie jest numerem gniazda huba. Model/wersja firmware nie identyfikuje egzemplarza, a unikalności seriali USB pomiędzy trybami nie sprawdzono sprzętowo. Automatyczne parowanie wielu urządzeń pozostaje wyłączone. Ostrożny wariant automatyczny działa tylko przy jednym dopuszczalnym punkcie MIDI i jednym urządzeniu USB widzianym przez Androida, bez sąsiadów MIDI. Po restarcie może być potrzebna ponowna zgoda systemowa USB.

Po utracie połączenia lub restarcie procesu zapisane nazwy wymagają jawnego powiązania. Odczytaj i fizycznie rozpoznaj instrument, usuń dotknięte zadania oczekujące, przypisz zapisaną nazwę i ponownie wskaż typ pamięci. Sukces oznacza sprawdzenie powrotu tożsamości firmware, a nie odczyt całej pamięci flash. Odbiór na rzeczywistym FM-1/hubie pozostaje konieczny.

## Wspólna biblioteka firmware

**Biblioteka** łączy wyszukiwanie, ulubione i pobrania. **Filtry i sortowanie** rozwijają kategorię, kolejność i źródło. Zapisane firmware działa offline. Zadanie chroni swoją paczkę przed usunięciem lub zastąpieniem. Zwykłe usunięcie poprawnej paczki można cofnąć w bieżącej sesji aplikacji.

W **Ustawieniach → Źródłach katalogu firmware** Hub jest domyślnie włączony, a Awesome FM-1 wyłączony. Własny katalog HTTPS używa obsługiwanego JSON, zatwierdzonych zakresów adresów paczek/grafik i interwału 1–168 godzin. Najpierw zapisz konfigurację; **Sprawdź teraz** to osobna akcja. Interwał obowiązuje także po błędzie (domyślnie 8 godzin w release i 1 w debug). Jawne sprawdzenie eksperymentalne może go pominąć poza Hub, który zachowuje minimum 24 godzin; opóźnienia serwera nadal obowiązują. Błąd zachowuje wcześniejszy katalog. Jawne sprawdzenie wyłączonego źródła jest możliwe, automatyczne żądania nie.

Sprawdzanie katalogów w tle jest opcjonalne i początkowo wyłączone; nie zapisuje automatycznie paczek do biblioteki ani nie instaluje firmware; niektóre źródła pobierają bajty firmware w celu identyfikacji. Offline zatrzymuje pobieranie sieciowe i sprawdzanie w tle, ale nie wykryje wycofań nieznanych lokalnemu katalogowi. Sumy z katalogu same nie uwierzytelniają wydawców.

## Awesome FM-1

[Awesome FM-1](https://github.com/ekim-pl/awesome-fm1) to społecznościowy katalog projektów firmware dla FM-1, opisów i linków do wydawców. Pliki firmware pozostają u autorów. Włącz gotowe źródło w Ustawieniach — nie trzeba wpisywać adresu. W repozytorium można zgłaszać nieaktualne wpisy i dodawać projekty.

## Narzędzia i platformy

Ustawienia udostępniają polski, angielski i chiński uproszczony, motyw jasny/ciemny/systemowy, offline, O aplikacji i prywatność. Funkcje eksperymentalne są domyślnie wyłączone. Ich włączenie pokazuje Logger MIDI i narzędzia recovery. Pasywny logger zatrzymuje się na czas łączenia/zapisu.

Włącz **Ustawienia → Funkcje eksperymentalne**, aby korzystać z diagnostyki, mapy sektorów, pełnych kopii, importu kopii i Loggera MIDI. Operacje ROM wymagają dokładnie jednego obsługiwanego FM-1 w trybie pobierania. Diagnostyka i kopie MBOOT wymagają wybranego urządzenia MIDI z obsługiwanym bootloaderem.

Pięć operacji zapisu pozwala instalować m-bootloader 2.4, przywracać fabryczny bootloader V15, instalować zgodne pobrane firmware przez ROM oraz przywracać kopię tego samego urządzenia z bootloaderem lub bez niego. Wybierz operację, **Przygotuj plan zapisu**, **Eksportuj kopię sprzed zapisu**, następnie **Zapisz sprawdzony plan** i potwierdź. Samo przygotowanie niczego nie zapisuje. Klucz fabryczny i MAC są chronione; kopia musi odpowiadać tożsamości urządzenia. Aplikacja weryfikuje sektory i całą pamięć, ale nie sprawdza uruchomienia. Nowo włączone operacje wymagają jeszcze prób sprzętowych; błąd zapisu bootloadera może wymagać sprzętowego odzyskiwania.

Android wymaga **14+**, USB Host/OTG, MIDI, przewodu danych i stabilnego zasilania. Zalecany jest zasilany hub. Port na iOS jest planowany w przyszłości. Nie ma jeszcze wydania na iOS ani ogłoszonego terminu.

## Prywatność i licencje

Aplikacja nie wymaga konta i nie zawiera reklam. Wersja F-Droid nie zawiera SDK analityki. Wersja Google Play z Google Analytics for Firebase domyślnie zbiera statystyki od pierwszego uruchomienia online. Możesz je wyłączyć w każdej chwili w Ustawienia → Statystyki użytkowania. Nazwy urządzeń, odczytane seriale i historia zadań pozostają lokalnie wraz z biblioteką. Zewnętrzne serwery katalogów/pobrań otrzymują metadane połączenia, bez dołączanej przez aplikację tożsamości instrumentu. Przeczytaj pełną [politykę prywatności](../PRIVACY_POLICY.md).

Katalog: [The Firmware Hub](https://thefirmwarehub.com), CC BY 4.0; opisy, grafiki i firmware zachowują prawa autorów. Aplikacja: [MIT](../LICENSE.md). Markdown: [CommonMark, BSD-2-Clause](../third_party/commonmark-LICENSE.txt). Biblioteki sieciowe/pracy w tle: [Apache 2.0](../third_party/Apache-2.0.txt), [informacje](../third_party/android-libraries-NOTICE.txt).

Kontakt: [ekim.pl@gmail.com](mailto:ekim.pl@gmail.com).

## Diagnostyka i czyszczenie pamięci

Aplikacja stale zapisuje lokalny log operacji (pliki dzienne, domyślnie siedem dni; do 256 KiB dziennie i 10 MiB łącznie). Log przetrwa restart aplikacji i zawiera czas, działania, zmiany portów USB/MIDI, etapy instalacji oraz przechwycone błędy. Nie zapisuje surowego ruchu MIDI ani plików firmware. Włącz **Ustawienia → Funkcje eksperymentalne**, aby użyć **Eksportuj log operacji** (JSON Lines, `.jsonl`) i **Wyczyść log operacji**. Eksport otwiera systemowy wybór pliku; log nie jest automatycznie wysyłany. Czyszczenie wymaga jednego potwierdzenia i nie usuwa wcześniej wyeksportowanych kopii. Kolejne zdarzenia są nadal zapisywane. Osobny Logger MIDI służy do podglądu surowego ruchu MIDI.

**Ustawienia → Wyczyść pobrane firmware** usuwa po jednym potwierdzeniu wszystkie pobrane paczki i metadane, w tym uszkodzone i osierocone pliki. Czyści wybór paczki i możliwość cofnięcia pojedynczego usunięcia. Zachowuje ulubione, źródła katalogu, nazwy instrumentów, historię zadań i kopie odzyskiwania. Najpierw usuń oczekujące zadania; czyszczenie jest blokowane podczas pracy oraz przy paczkach przypisanych do oczekujących/trwających zadań. Instalacja offline wymaga ponownego pobrania paczek.

**Urządzenia → Tryby i kość pamięci** rozdziela odczyt MIDI, przejście w tryb download i wykrywanie kości na czytelne karty. Wynik detekcji otwiera popup. Potem wyłącz i włącz instrument, aby wrócić do MIDI. Domyślne odłączone wpisy są tymczasowe; własne nazwy i cele kolejki pozostają. Opóźnione odłączenie poprzedniego portu USB nie przerywa kolejnego etapu tej samej aktualizacji. Utrata innego instrumentu lub huba nadal blokuje zapis.

## Pełnoekranowe ustawienia

**Ustawienia** są pełnym ekranem z kartami Interfejs, Biblioteka firmware, Funkcje eksperymentalne i Aplikacja. Język oraz motyw wybiera się bezpośrednio przyciskami radiowymi. Źródła, formularze, zgodność poszczególnych paczek i polityka prywatności otwierają się jako pełne strony. Nagłówek i systemowe Wstecz cofają o poziom; poprzednia zakładka Biblioteka/Urządzenia/Zadania i niesekretne dane formularza przetrwają odtworzenie aktywności. Wpisane hasła/tokeny nie są zapisywane w stanie aktywności. Okna pozostają przy potwierdzeniach usuwania i żądanym wyniku wykrywania kości.


Źródła katalogów na Androidzie korzystają z thefirmwarehub, awesome-fm1, wydań GitHub (repo-url) lub pliku FWSC (pkg-url). Edycja źródła obsługuje Basic, Bearer, klucz API w nagłówku oraz OAuth 2.0 (PKCE w przeglądarce lub Client Credentials). Poświadczenia są szyfrowane lokalnie. Ręczna deklaracja zgodności dotyczy wyłącznie konkretnego SHA-256. Nowe adaptery sieciowe i autoryzacja dotyczą Androida; backend Apple zachowuje dotychczasową obsługę Hub.


Historia zakończonych zadań jest domyślnie przechowywana przez 30 dni. W Ustawieniach można zmienić okres lub wyczyścić zakończoną historię. Zadania oczekujące i trwające, backupy oraz zapis nierozstrzygniętej sesji są chronione. Logi diagnostyczne można przechowywać przez 7, 14 lub 30 dni. Starsze logi są usuwane przy zapisie lub odczycie; nieużywana aplikacja sprząta przy kolejnej aktywności. Ręczne czyszczenie usuwa zachowane pliki dzienne, ale nie wyeksportowane kopie.

## Podziękowania

Dziękujemy **Juliusowi Vitkauskasowi** za [M-Flasher](https://github.com/jvitkauskas/m-flasher) i [m-bootloader](https://github.com/jvitkauskas/m-bootloader). Ich otwarty kod, dokumentacja protokołu i loadery umożliwiły powstanie tych narzędzi diagnostyki, zarządzania bootloaderem i odzyskiwania pamięci. Adaptowany kod i dołączone pliki zachowują informacje o autorze i licencję MIT; zobacz [informacje o komponencie](../third_party/m-flasher-NOTICE.md) i [licencję](../third_party/m-flasher-LICENSE.txt).
