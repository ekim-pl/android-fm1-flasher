# Dla twórców firmware

Udostępnij swoje firmware użytkownikom FM-1 Flasher ze źródła, nad którym masz kontrolę. Możesz publikować je otwarcie albo ograniczyć pobieranie do osób posiadających licencję w swoim serwisie.

[Przygotuj źródło](#przygotuj-zrodo) · [Zgłoś integrację](https://github.com/ekim-pl/android-fm1-flasher/issues/new?template=firmware-source.md)

Użytkownik może samodzielnie dodać źródło bez zgłaszania go do projektu. Propozycja dodania źródła domyślnego jest rozpatrywana osobno i nie gwarantuje przyjęcia.

## Przygotuj źródło

Aplikacja na Androida obsługuje trzy możliwości:

1. **Repozytorium GitHub:** dołącz pliki `.fwsc` do wydania i podaj adres `https://github.com/OWNER/REPO`. Uwzględniane są wydania opublikowane i prerelease, a pomijane wersje robocze oraz pliki zapisane tylko w gałęzi. Ten adapter nie obsługuje GitHub Enterprise ani innych serwerów Git.
2. **Bezpośredni pakiet:** podaj adres HTTPS jednego pliku `.fwsc`. Aplikacja odczytuje i sprawdza pakiet podczas sprawdzania źródła.
3. **Katalog JSON:** udostępnij katalog HTTPS zgodny z formatem Hub oraz pliki pakietów. Możesz w ten sposób udostępnić wiele pakietów wraz z opisem i informacjami o zgodności.

Zastąp poniższe wartości rzeczywistymi danymi. `sha256` to suma całego pliku `.fwsc` zapisana małymi literami, a `product` musi odpowiadać identyfikatorowi w pakiecie. Potwierdź właściwy chip dla sprzętu i konkretnych bajtów; `unknown` blokuje instalację do czasu potwierdzenia zgodności.

```json
{
  "updated": "2026-01-01",
  "firmwares": [{
    "id": "example-1", "name": "Example firmware", "author": "Example author",
    "version": "1.0", "what": "Example release",
    "sha256": "0000000000000000000000000000000000000000000000000000000000000000",
    "product": "FM-1_001", "chip": "unknown",
    "pkg": "https://firmware.example.com/releases/example.fwsc"
  }]
}
```

Suma w przykładzie jest fikcyjna. Identyfikatory muszą być unikalne i stałe. Wydawca odpowiada za poprawne informacje o zgodności i warunkach licencji. Sama suma wykrywa zmianę bajtów, ale nie uwierzytelnia wydawcy kontrolującego zarówno katalog, jak i sumę.

Użytkownik wybiera **Ustawienia → Źródła firmware**, dodaje typ i adres źródła, ustawia autoryzację, zapisuje, wybiera **Sprawdź teraz** i włącza źródło. Dla JSON musi zatwierdzić wymagane zakresy adresów pakietów i grafik. Dla GitHub i bezpośredniego pakietu po sprawdzeniu wybiera **Edytuj → Zgodność pakietów** i potwierdza zgodność dla każdej sumy. Nieznana zgodność blokuje instalację. Odstęp sprawdzania wynosi 24–168 godzin; zmiana danych uwierzytelniających lub autoryzacja OAuth umożliwia ponowne sprawdzenie.

## Prywatne repozytorium GitHub

Jeśli odbiorcy nie powinni czytać kodu projektu, użyj osobnego prywatnego repozytorium dystrybucyjnego bez kodu źródłowego. Odbiorca potrzebuje własnego konta GitHub z dostępem do repozytorium i własnego fine-grained personal access token ograniczonego do tego repozytorium, z uprawnieniem **Contents: read**. W ustawieniach źródła wybiera **Bearer** i podaje token bez prefiksu `Bearer`. Nie udostępniaj tokenu właściciela i nie publikuj sekretów w zgłoszeniach. Odczyt repozytorium daje dostęp także do innych treści, nie tylko plików wydań.

GitHub może zwrócić HTTP 404 dla niedostępnego prywatnego repozytorium. Sprawdź adres, dostęp konta, uprawnienia i ważność tokenu oraz wymagane zatwierdzenia organizacji. Po cofnięciu dostępu pobrane wcześniej firmware nadal jest dostępne offline.

## Autoryzacja i dostęp licencjonowany

Formularz obsługuje HTTP Basic, Bearer, klucz API w nagłówku oraz OAuth 2.0. Dla indywidualnych kont użyj OAuth Authorization Code z PKCE S256 i klienta publicznego, jeśli dostawca to umożliwia. Skonfiguruj client ID, zakres, adres autoryzacji i tokenu oraz zarejestruj dokładnie ten adres powrotu:

Poniższy adres dotyczy Androida 0.9.4-experimental.27 i nowszych. Wersja 0.9.4-experimental.26 i wcześniejsze używają adresu pokazanego w ustawieniach źródła. Zarejestruj nowy adres przed aktualizacją lub ponowną autoryzacją użytkowników.

```text
pl.ekim.fm1-flasher://oauth/callback
```

Zapisz źródło i wybierz **Autoryzuj OAuth 2.0**. Aplikacja odświeża wygasłe tokeny, jeśli dostawca udostępnia refresh token. Obsługiwane jest także Client Credentials z sekretem klienta; nie umieszczaj wspólnego sekretu w dystrybuowanej aplikacji. Nie ma wbudowanego uniwersalnego klienta OAuth. Sekrety wprowadzone w aplikacji są szyfrowane przy użyciu Android Keystore.

Twój serwer odpowiada za konta, płatności, sprawdzanie licencji i decyzje o dostępie. Może udostępniać katalog i pakiet wyłącznie uprawnionym osobom. Dane autoryzacyjne są wysyłane tylko do origin skonfigurowanego źródła i usuwane przy przekierowaniu na inny origin, także CDN. Udostępniaj chronione pakiety z tego samego origin albo użyj odpowiednio ograniczonych, krótkotrwałych adresów pobierania. Przekierowania żądań POST do endpointu tokenu są odrzucane. Adresy muszą używać HTTPS i spełniać ograniczenia aplikacji dotyczące adresów i zakresów pakietów.

**Kontrola pobierania nie kontroluje użycia pobranego pliku.** FM-1 Flasher umożliwia instalację offline; nie zapewnia DRM, licencji przypisanej do urządzenia ani cofnięcia już pobranych plików. Po nieudanym sprawdzeniu źródło może zachować katalog w pamięci podręcznej. W trybie offline aplikacja nie dowie się o wycofaniu pakietu.

## Lista kontrolna

- Sprawdź `.fwsc` na obsługiwanym sprzęcie; udokumentuj zgodność chipów, wersję i warunki licencji.
- Udostępnij rzeczywisty pakiet z poprawną sumą i przetestuj dodanie źródła, pobranie oraz instalację z docelowymi uprawnieniami.
- Sprawdź wygasłe i cofnięte dane, HTTP 401/403/404, przekierowania i działanie offline. Nigdy nie publikuj prawdziwych danych dostępowych w dokumentacji ani zgłoszeniach.
- [Zgłoś integrację](https://github.com/ekim-pl/android-fm1-flasher/issues/new?template=firmware-source.md), aby uzyskać pomoc lub zaproponować źródło domyślne. Publiczne zgłoszenie nie może zawierać prywatnych adresów ani sekretów.

GitHub Pages udostępnia tę dokumentację jako stronę statyczną. Usługa autoryzacji i licencjonowania działa osobno. Zobacz [pełną instrukcję techniczną](documentation.md).
