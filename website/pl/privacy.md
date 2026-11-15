# Polityka prywatności — FM-1 Flasher

## Wydawca i kontakt

ekim.pl@gmail.com. Data obowiązywania: 15 listopada 2026 r. Dokument opisuje sposób przetwarzania danych przez FM-1 Flasher.

## Dane lokalne

Aplikacja nie wymaga konta i nie zawiera reklam, analityki ani śledzących SDK. Ulubione, ustawienia przeglądania, motyw i język, kopie katalogu, grafiki podglądu, pliki firmware i metadane pobrań są zapisywane w prywatnej pamięci aplikacji. Dziennik zdarzeń pozostaje w pamięci operacyjnej. Nazwy instrumentów, odczytane numery seryjne USB, wskazany typ pamięci, ostatnia tożsamość firmware i historia zadań (cele, sumy paczek, etapy i wyniki) są zapisywane lokalnie. Numery seryjne nie trafiają do zwykłego dziennika połączeń. Ostatnią usuniętą poprawną paczkę można przywrócić w czasie otwartej sesji aplikacji. Dane te nie są wysyłane wydawcy aplikacji.

## Połączenia sieciowe

W trybie online aplikacja sprawdza włączone źródła katalogów i może pobierać ich dozwolone grafiki. Firmware Hub jest domyślnie włączony; awesome-fm1 na raw.githubusercontent.com i własne źródła wymagają włączenia przez użytkownika. Wyłączone źródła nie wykonują automatycznych żądań; Sprawdź teraz oznacza jawne jednorazowe żądanie także dla źródła wyłączonego. Pobieranie paczek łączy się z usługami wydań GitHuba lub zatwierdzonymi adresami autorów po działaniu użytkownika. Każdy katalog jest odpytywany najwyżej raz na 24 godziny, także po błędzie. Opcjonalne sprawdzanie katalogów w tle używa WorkManager i jest domyślnie wyłączone; tryb offline je wstrzymuje. Wszystkie żądania korzystają z HTTPS. Firmware nigdy nie jest instalowane automatycznie.

## Dane widoczne dla serwisów zewnętrznych

Operatorzy serwerów otrzymują adres żądanego zasobu, adres IP połączenia i standardowe dane techniczne HTTPS. Aplikacja wysyła ogólny techniczny nagłówek User-Agent, który nie identyfikuje konkretnej osoby ani urządzenia. Nie dołącza do żądań ulubionych, ustawień, zawartości pamięci instrumentu, tożsamości MIDI ani identyfikatora konta. Operatorzy przetwarzają logi według własnych polityk prywatności; wydawca aplikacji nie kontroluje ich przechowywania ani przetwarzania.

## USB MIDI

Aplikacja odczytuje tożsamość FM-1 wskazanego przez użytkownika i przesyła wybrany firmware przez USB MIDI po potwierdzeniu aktualizacji. Informacje o urządzeniu USB i odpowiedzi MIDI służą do obsługi tej lokalnej sesji i nie trafiają do serwisów sieciowych ani wydawcy aplikacji.

## Przechowywanie, usuwanie i offline

Dane lokalne pozostają na urządzeniu do ich usunięcia w aplikacji, wyczyszczenia danych w ustawieniach Androida lub odinstalowania aplikacji. Usunięcie paczki zachowuje ulubione. Katalogi, zasady źródeł i metadane lokalnych paczek są przechowywane w SQLite. Usunięcie subskrypcji zachowuje pochodzenie, poznane wycofania, paczki i ulubione. Wyczyszczenie danych lub odinstalowanie usuwa bibliotekę i preferencje. Kopia zapasowa danych aplikacji w Androidzie jest wyłączona. Nie ma konta serwerowego ani profilu użytkownika u wydawcy do usunięcia. Tryb offline wyłącza odświeżanie katalogu i pobieranie paczek oraz grafik; zapisane wcześniej treści pozostają dostępne.

## Zmiany i pytania

Zaktualizowane zasady będą dołączane do odpowiednich aktualizacji aplikacji i publikowane w repozytorium projektu. Pytania o prywatność: ekim.pl@gmail.com.
