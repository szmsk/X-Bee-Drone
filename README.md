# XBee Drone Pro — Aplikacja Android dla Overmax X-Bee Drone 9.5 Fold

Kompletna aplikacja Android w Kotlin, będąca zamiennikiem M RC PRO dla drona
Overmax X-Bee Drone 9.5 Fold.

---

## 📱 Funkcje

### Ekran FPV (Kamera)
- Podgląd kamery na żywo (MJPEG przez WiFi 5GHz)
- Lewy joystick: **Gaz (throttle)** + **Obrót (yaw)**
- Prawy joystick: **Przód/tył (pitch)** + **Lewo/prawo (roll)**
- Przycisk **START / LĄDUJ** (auto-takeoff/landing)
- **RTH** — powrót do punktu startowego
- **⚠ STOP** — natychmiastowe zatrzymanie silników
- **Zdjęcie 📷** — przechwytuje klatkę z kamery
- **⏺ REC** — nagrywanie wideo
- **HEADLESS** — tryb bez głowy (kierunek względem startu)
- **ALT HOLD** — automatyczne utrzymanie wysokości
- **KALIB.** — kalibracja kompasu

### Ekran Mapa GPS
- Mapa OpenStreetMap (działa bez API key, wymaga internetu do pobrania kafelków)
- Pozycja drona w czasie rzeczywistym (aktualizowana z telemetrii UDP)
- Pozycja telefonu (GPS)
- **Waypoints** — dotknij mapę (długie naciśnięcie) aby dodać punkt trasy
- Trasa waypointów (niebieska linia przerywana)
- Ślad lotu drona (pomarańczowa linia)
- Przełącznik mapa / satelita
- Wyczyść waypoints / ślad lotu

### Ekran Telemetria
- Bateria drona (%) z paskiem postępu i kolorem
- Czas lotu (licznik automatyczny)
- Wysokość (m)
- Prędkość (km/h)
- GPS: szerokość, długość, liczba satelitów
- Dystans od punktu startowego (Home)
- Siła sygnału WiFi (%)
- Status połączenia

---

## 🛠 Wymagania

- Android Studio **Hedgehog (2023.1)** lub nowszy
- Android SDK 34
- Kotlin 1.9.22
- Min. Android 7.0 (API 24)
- Telefon z WiFi 5GHz (wymagane przez dron)

---

## 🚀 Uruchomienie

### 1. Otwórz projekt w Android Studio
```
File → Open → wybierz folder XBeeDroneApp
```

### 2. Zsynchronizuj Gradle
Android Studio automatycznie pobierze zależności (wymaga internetu).

### 3. Zbuduj i zainstaluj na telefonie
```
Run → Run 'app'  lub  Shift+F10
```

### 4. Połącz się z dronem
1. Włącz drona (przytrzymaj przycisk zasilania)
2. Poczekaj aż diody LED zaczną migać
3. W Ustawieniach WiFi telefonu połącz się z siecią:
   **`Drone_XXXXXXX`** (hasło: `12345678`)
4. Wróć do aplikacji → naciśnij **POŁĄCZ**

---

## 🔧 Protokół komunikacji

Dron komunikuje się przez WiFi (IP: `192.168.0.1`):

| Typ       | Protokół | Port | Opis                        |
|-----------|----------|------|-----------------------------|
| Komendy   | UDP      | 8080 | Pakiety sterowania (50ms)   |
| Wideo     | TCP      | 8888 | Strumień MJPEG              |
| Telemetria | UDP     | 8889 | Dane o stanie drona         |

### Format pakietu UDP sterowania (11 bajtów):
```
[0]  = 0xFF  — nagłówek
[1]  = 0x04  — stały
[2]  = throttle (0x00–0xFF, środek=0x80)
[3]  = yaw      (0x00–0xFF, środek=0x80)
[4]  = pitch    (0x00–0xFF, środek=0x80)
[5]  = roll     (0x00–0xFF, środek=0x80)
[6]  = flagi trybu (0x01=headless, 0x02=alt-hold, 0x04=GPS)
[7]  = checksum (XOR bajtów 2–6)
[8-10] = 0x00
```

### ⚠ Ważna uwaga o protokole

Protokół UDP **nie jest oficjalnie udokumentowany** przez Overmax/MJX.
Powyższy format jest oparty na inżynierii wstecznej podobnych dronów tej klasy.

**Jeśli komendy nie działają**, użyj Wireshark do przechwycenia ruchu
z oryginalnej aplikacji M RC PRO:

1. Zainstaluj **tPacketCapture** (Google Play) na telefonie
2. Uruchom tPacketCapture, a następnie M RC PRO
3. Połącz i wystuj kilka komend (throttle, yaw, pitch, roll)
4. Zatrzymaj przechwytywanie i otwórz plik .pcap w Wireshark
5. Filtr: `udp && ip.dst == 192.168.0.1`
6. Zaktualizuj stałe w `DroneProtocol.kt`

---

## 📁 Struktura projektu

```
XBeeDroneApp/
├── app/src/main/
│   ├── java/com/xbeedrone/app/
│   │   ├── DroneApplication.kt       — Application class (init OSMDroid)
│   │   ├── DroneViewModel.kt         — Główny ViewModel
│   │   ├── network/
│   │   │   ├── DroneConnection.kt    — WiFi UDP/TCP komunikacja
│   │   │   ├── DroneProtocol.kt      — Stałe protokołu
│   │   │   └── TelemetryData.kt      — Model danych telemetrii
│   │   ├── model/
│   │   │   └── Waypoint.kt           — Model waypointa
│   │   ├── ui/
│   │   │   ├── MainActivity.kt       — Główna aktywność
│   │   │   ├── JoystickView.kt       — Customowy widok joysticka
│   │   │   ├── ConnectDialogFragment.kt — Dialog pomocy WiFi
│   │   │   ├── fpv/FpvFragment.kt    — Ekran FPV + sterowanie
│   │   │   ├── map/MapFragment.kt    — Ekran mapy GPS
│   │   │   └── telemetry/TelemetryFragment.kt — Ekran telemetrii
│   │   └── utils/
│   │       ├── WifiHelper.kt         — Narzędzia WiFi
│   │       └── MediaSaver.kt         — Zapis zdjęć/wideo
│   └── res/
│       ├── layout/                   — Layouty XML
│       ├── navigation/nav_graph.xml  — Nawigacja między ekranami
│       ├── menu/bottom_nav_menu.xml  — Menu dolne
│       ├── drawable/                 — Ikony wektorowe
│       └── values/                   — Kolory, style, wymiary
└── build.gradle.kts                  — Zależności projektu
```

---

## 📦 Zależności (build.gradle.kts)

| Biblioteka | Wersja | Cel |
|-----------|--------|-----|
| osmdroid-android | 6.1.18 | Mapy GPS (OpenStreetMap) |
| kotlinx-coroutines | 1.7.3 | Asynchroniczna komunikacja |
| navigation-fragment-ktx | 2.7.7 | Nawigacja między ekranami |
| material | 1.11.0 | Komponenty UI |
| lifecycle-viewmodel-ktx | 2.7.0 | ViewModel + StateFlow |

---

## 🐛 Rozwiązywanie problemów

| Problem | Rozwiązanie |
|---------|------------|
| Aplikacja nie łączy się | Sprawdź czy jesteś połączony z WiFi `Drone_XXXXXXX` |
| Brak obrazu z kamery | Upewnij się że dron jest w trybie WiFi; spróbuj port 7060 lub 554 |
| Dron nie reaguje na komendy | Użyj Wireshark do sprawdzenia protokołu UDP |
| Mapa nie wyświetla kafelków | Wymagane połączenie z internetem (lub kafelki offline) |
| Joystick nie działa | Sprawdź w logcat czy UDP jest wysyłane (`DroneConnection` tag) |

---

## ⚖️ Licencja

Projekt open-source do użytku osobistego i edukacyjnego.
Używa OpenStreetMap (© OpenStreetMap contributors, ODbL).
