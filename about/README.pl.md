# FM‑1 Flasher

## Wiele instrumentów, jedno miejsce pracy

W Androidzie 0.9.0 sekcja **Urządzenia** pozwala odczytać instrumenty podłączone przez hub OTG, nadać im nazwy i wybrać lokalną paczkę firmware dla każdego z nich. **Zadania** pokazują cały plan i historię. Jedno zatwierdzenie uruchamia instalację sekwencyjną; zatrzymanie kolejki pozwala dokończyć bieżącą operację. **Biblioteka** łączy katalogi, pobrane paczki i ulubione.

Po restarcie przy wielu urządzeniach powiąż powracający instrument na karcie zadania. Utrata innego instrumentu blokuje dalszy zapis. Automatyczne parowanie wielu urządzeń oczekuje testów fizycznego sprzętu; adresy USB nie są numerami gniazd huba. Po utracie połączenia zapisane nazwy wymagają jawnego powiązania.

Niezależna, eksperymentalna aplikacja do przeglądania firmware i aktualizowania **M‑VAVE FM‑1** przez USB MIDI.

## Możliwości

- Przeglądanie firmware z wielu katalogów, wyszukiwanie, ulubione i pobrane paczki dostępne offline.
- Zarządzanie źródłami katalogów i filtrowanie biblioteki według źródła.
- Wybór pobranego firmware w katalogu lub bezpośrednio w zakładce Flasher.
- Sprawdzanie integralności i zgodności paczki przed instalacją.
- Automatyczne łączenie po planowanym restarcie przy jednym urządzeniu USB i jednym dozwolonym porcie MIDI.
- Opcjonalne funkcje eksperymentalne obejmują odbiorczy logger MIDI, wykrywanie kości flash i ograniczone odzyskiwanie ROM; odzyskiwania nie sprawdzono na fizycznym sprzęcie.
- Motyw jasny, ciemny lub systemowy; interfejs polski, angielski i chiński uproszczony.

Włącz **Ustawienia → Funkcje eksperymentalne**, aby pokazać logger MIDI, wykrywanie pamięci i recovery. Ustawienie jest zapamiętywane i domyślnie wyłączone. Wyłączenie zatrzymuje logger i usuwa plan recovery; podczas operacji nie można go zmienić. Recovery nadal wymaga testów na fizycznym sprzęcie.

## Zarządzanie źródłami firmware

Otwórz **Ustawienia → Źródła firmware**, aby wybrać katalogi używane przez aplikację. **The Firmware Hub jest domyślnie włączony**, a **Awesome FM-1 domyślnie wyłączony**.

- Włączaj i wyłączaj źródła, sprawdzaj ich stan, datę ostatniego udanego sprawdzenia i termin kolejnej dozwolonej próby.
- Użyj **Dodaj źródło katalogu**, aby podać adres HTTPS katalogu w tym samym formacie JSON co Firmware Hub. Nadaj mu nazwę, ustaw odstęp sprawdzania i dozwolone adresy paczek oraz grafik. **Zapisz i sprawdź** pozwala przejrzeć nowy katalog przed jego włączeniem.
- Otwórz **Szczegóły i konfiguracja**, aby edytować własne źródło lub wybrać **Sprawdź teraz**. Obowiązuje odstęp danego źródła, co najmniej 24 godziny między próbami, także nieudanymi. Błąd jednego źródła nie zatrzymuje aktualizacji pozostałych.
- Wybierz **Filtruj według źródła**, aby przeglądać konkretny katalog, jego ulubione i pobrane paczki. Usunięcie własnej subskrypcji zachowuje pobrane pliki, ulubione i znane wycofania.
- **Przywróć domyślne ustawienia źródeł** włącza Firmware Hub i wyłącza Awesome FM-1, zachowując własne subskrypcje oraz pobrane paczki.

Wyłączone źródła nie wykonują automatycznych żądań, także po grafiki. Możesz świadomie wybrać **Sprawdź teraz** dla wyłączonego źródła bez jego włączania. Opcja **Sprawdzaj katalogi w tle** jest domyślnie wyłączona; sprawdza włączone katalogi przy dostępie do sieci, zachowuje ich limity i nigdy nie pobiera ani nie instaluje firmware. Tryb offline wstrzymuje te sprawdzenia.

Lokalna biblioteka zapamiętuje pobrane paczki, ulubione, wybór i źródło każdej paczki. Flashowanie korzysta z zapisanych plików bez dostępu do sieci. Tryb offline nie pozwala wykryć wycofań opublikowanych po ostatnim udanym sprawdzeniu katalogu.

## Awesome FM-1

[Awesome FM-1](https://github.com/ekim-pl/awesome-fm1) to towarzyszący aplikacji społecznościowy katalog projektów firmware dla **M-VAVE FM-1**. Uzupełnia Firmware Hub o dodatkowe katalogi z odnośnikami do stron deweloperów, bezpośrednich plików paczek i wydań projektów na GitHubie. Repozytorium przechowuje opisy, informacje o wydaniach i adresy pobierania; pliki firmware pozostają u ich wydawców.

Źródło jest już skonfigurowane w aplikacji. Aby z niego korzystać, wyłącz tryb offline, otwórz **Ustawienia → Źródła firmware** i włącz **Awesome FM-1**. Wpisy pojawią się po udanym sprawdzeniu, z zachowaniem odstępu odświeżania źródła. Nie trzeba ręcznie wpisywać adresu katalogu.

Możesz pomóc rozwijać katalog: zgłoś nieaktualny wpis przez issue albo zaproponuj projekt w pull requeście, podając adres projektu lub paczki firmware i krótki opis. Zasady współpracy znajdziesz w [repozytorium Awesome FM-1](https://github.com/ekim-pl/awesome-fm1#contributing).

## Źródła i autorzy

Autor katalogu: [The Firmware Hub](https://thefirmwarehub.com), CC BY 4.0; opisy i grafiki należą do ich autorów. Dodatkowe katalogi określają własne licencje. Firmware jest pobierane od autorów, nie z mirrora paczek Huba.

Sieć i zadania w tle: OkHttp/Okio oraz AndroidX WorkManager, [Apache 2.0](../third_party/Apache-2.0.txt); [informacje o zależnościach](../third_party/android-libraries-NOTICE.txt).

## Przed aktualizacją

Telefon musi obsługiwać USB Host/OTG i MIDI. Zrób kopię danych instrumentu, zapewnij stabilne zasilanie USB oraz potwierdź właściwy instrument i paczkę.

## Prywatność i licencje

Bez konta, reklam i analityki. Zobacz [politykę prywatności](../PRIVACY_POLICY.md).

Kontakt: [ekim.pl@gmail.com](mailto:ekim.pl@gmail.com).

Aplikacja: [licencja MIT](../LICENSE.md). Parser Markdown: [CommonMark, BSD-2-Clause](../third_party/commonmark-LICENSE.txt).
