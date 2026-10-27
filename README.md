# Night Drive

Retro endless runner na Androida: nocna szosa, uniki między autami, monety, power-upy i ranking
online. Cała grafika rysowana jest kodem na Compose Canvas - w projekcie nie ma ani jednego
sprite'a samochodu.

Kotlin · Jetpack Compose · silnik gry napisany od zera.

---

## Co jest w środku

**Własny silnik.** Symulacja chodzi ze stałym krokiem 60 Hz, niezależnie od odświeżania ekranu,
więc gra zachowuje się tak samo na 60 i 120 Hz. Świat ma stałą wirtualną wysokość 200 jednostek
(środek ekranu w zerze, oś Y do góry), a szerokość wynika z proporcji urządzenia. Cała tabela
balansu siedzi w jednym pliku [`GameConfig.kt`](app/src/main/java/wojtoteka/nightdrive/engine/GameConfig.kt) -
wartości przeniesione 1:1 z pierwotnej wersji w microScript, żeby czucie gry zostało to samo,
gdy zmieniła się architektura wokół.

**Render bez assetów.** `RetroCanvas` i `WorldRenderer` malują drogę, horyzont, auta, neony
i cały HUD kształtami Compose'a. Efekt jest wektorowy, skaluje się na każdy ekran i waży tyle,
co nic.

**Deterministyczna losowość.** Rozgrywka losuje z osobnych strumieni `Rng` (ruch, monety,
power-upy, fale), a kosmetyka z własnego. Dzięki temu przebieg da się odtworzyć i przetestować,
a trasa dnia jest identyczna u wszystkich graczy.

**Rozgrywka.** Mijanie aut o włos (near-miss) daje punkty i podtrzymuje combo. Ruch z czasem
gęstnieje i się różnicuje: ciężarówki, auta zmieniające pas z kierunkowskazem i piraci drogowi
zapowiadani strzałkami przy krawędzi ekranu. Po dwóch minutach przychodzą fale - konwój z jedną
luką albo mgła. Co minutę zaczyna się nowy etap, a kupione motywy świata zmieniają się po kolei
z płynnym przejściem. Muzyka reaguje na jazdę: narasta z combo, zwalnia w slow-mo, cichnie
po kraksie.

**Codzienne granie.** Trzy misje dnia (łatwa, średnia, trudna) z nagrodami i bonusem za komplet
oraz trasa dnia - ta sama dla wszystkich, bez tarczy z garażu i boostów, z rekordem dnia.
Ekran końca gry podsumowuje bieg i podpowiada, ile zabrakło do rekordu i które osiągnięcie
jest najbliżej.

**Sklep i progresja.** Skiny aut kupowane za monety, power-upy (magnes, slow-mo, podwojenie,
deszcz monet) z trójstopniowymi ulepszeniami czasu trwania - osobno albo pakietem.
Do tego 24 osiągnięcia z nagrodami w monetach.

**Ranking online.** Wyniki idą na własne API i są podpisywane HMAC-SHA256, więc serwer odrzuca
spreparowane zgłoszenia. Klucz i sekret nie leżą w kodzie - build wciąga je z `local.properties`.
Bez internetu gra działa w całości offline.

**Bez reklam.** Żadnego SDK reklamowego, żadnych zgód UMP - gra nie wysyła nic poza wynikami
do rankingu.

**Reszta.** Dwa języki (PL/EN, domyślnie za systemem), haptyka przy monetach i kraksach,
DataStore na profil gracza, baseline profile żeby ART skompilował ścieżkę startu z wyprzedzeniem
zamiast JIT-ować ją przy każdym zimnym starcie.

---

## Układ kodu

```
engine/    silnik: pętla symulacji, model świata, balans, RNG
render/    rysowanie świata i HUD-u na Compose Canvas
ui/        ekrany (menu, gra, sklep, osiągnięcia, ranking, ustawienia) + ViewModel
domain/    katalog skinów, power-upów i osiągnięć, profil gracza, tłumaczenia
data/      zapis stanu (DataStore)
net/       klient API rankingu, podpisywanie HMAC
audio/     muzyka i efekty
```

## Budowanie

```bash
cp local.properties.example local.properties   # uzupełnij sdk.dir
./gradlew assembleDebug
```

Ranking wymaga dwóch wartości w `local.properties` - `NIGHTDRIVE_API_KEY` i
`NIGHTDRIVE_HMAC_SECRET`, identycznych jak w `.env` serwera rankingowego. Bez nich gra działa
normalnie, tylko serwer nie przyjmie wyników. Build debug też uderza w produkcję, żeby
sideloadowane testy działały na prawdziwym telefonie; lokalny serwer podpina się przez
`NIGHTDRIVE_DEBUG_API`.

- `minSdk` 24, `targetSdk` 36, Java 17
- telefony: tryb poziomy; tablety, składane i okna desktopowe: dowolna orientacja i rozmiar okna
- release: R8 + zoptymalizowany shrink zasobów, Android Gradle Plugin 9

> Build release podpisuje się kluczem debug, żeby sideload działał bez prawdziwego keystore.
> Przed pierwszym wysłaniem na Google Play trzeba to zamienić na własny upload keystore -
> Play odrzuca artefakty podpisane debugiem.

## Licencja

Kod udostępniony do wglądu w celach portfolio.
