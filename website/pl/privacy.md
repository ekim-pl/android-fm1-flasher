# Polityka prywatności — FM-1 Flasher

## Wydawca i kontakt

ekim.pl@gmail.com. Data obowiązywania: 13 listopada 2026 r. Dokument opisuje sposób przetwarzania danych przez FM-1 Flasher.

## Dane lokalne

Aplikacja nie wymaga konta i nie zawiera reklam, analityki ani śledzących SDK. Ulubione, ustawienia przeglądania, motyw i język, kopie katalogu, grafiki podglądu, pliki firmware i metadane pobrań są zapisywane w prywatnej pamięci aplikacji. Dziennik zdarzeń i tożsamość połączonego instrumentu pozostają w pamięci operacyjnej podczas używania aplikacji. Dane te nie są wysyłane wydawcy aplikacji.

## Połączenia sieciowe

Po wyłączeniu trybu offline aplikacja może automatycznie pobierać katalog z thefirmwarehub.com i grafiki z serwerów wskazanych w katalogu. Metadane firmware i paczki są pobierane z GitHuba oraz serwerów autorów po działaniu użytkownika. Żądania odświeżenia katalogu są ograniczone do jednego na dobę. Wszystkie żądania aplikacji korzystają z HTTPS.

## Dane widoczne dla serwisów zewnętrznych

Operatorzy serwerów otrzymują adres żądanego zasobu, adres IP połączenia i standardowe dane techniczne HTTPS. Aplikacja wysyła ogólny techniczny nagłówek User-Agent, który nie identyfikuje konkretnej osoby ani urządzenia. Nie dołącza do żądań ulubionych, ustawień, zawartości pamięci instrumentu, tożsamości MIDI ani identyfikatora konta. Operatorzy przetwarzają logi według własnych polityk prywatności; wydawca aplikacji nie kontroluje ich przechowywania ani przetwarzania.

## USB MIDI

Aplikacja odczytuje tożsamość FM-1 wskazanego przez użytkownika i przesyła wybrany firmware przez USB MIDI po potwierdzeniu aktualizacji. Informacje o urządzeniu USB i odpowiedzi MIDI służą do obsługi tej lokalnej sesji i nie trafiają do serwisów sieciowych ani wydawcy aplikacji.

## Przechowywanie, usuwanie i offline

Dane lokalne pozostają na urządzeniu do ich usunięcia w aplikacji, wyczyszczenia danych w ustawieniach Androida lub odinstalowania aplikacji. Usunięcie paczki zachowuje ulubione. Wyczyszczenie danych lub odinstalowanie usuwa bibliotekę i preferencje. Kopia zapasowa danych aplikacji w Androidzie jest wyłączona. Nie ma konta serwerowego ani profilu użytkownika u wydawcy do usunięcia. Tryb offline wyłącza odświeżanie katalogu i pobieranie paczek oraz grafik; zapisane wcześniej treści pozostają dostępne.

## Zmiany i pytania

Zaktualizowane zasady będą dołączane do odpowiednich aktualizacji aplikacji i publikowane w repozytorium projektu. Pytania o prywatność: ekim.pl@gmail.com.
