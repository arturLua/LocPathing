<h1 align="center">LocPathing</h1>

<p align="center">
<img src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" />
<img src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" />
<img src="https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" />
<img src="https://img.shields.io/badge/Claude-D97757?style=for-the-badge&logo=claude&logoColor=white" />
</p>

###

<p>
LocPathing is a native Android application that retrieves the device's GPS position and converts raw coordinates into a human-readable address using reverse geocoding. Built entirely in Kotlin with Jetpack Compose, it follows the MVVM architecture pattern and integrates both the Google Fused Location Provider and the Android GNSS stack for real-time satellite monitoring.
</p>

<h1>Features</h1>

- **Reverse Geocoding** – Converts GPS coordinates into a structured street address (street, number, neighbourhood, city, state, country, postal code).
  
- **Real-time GNSS Monitoring** – Tracks the number of satellites in view and in fix, plus average signal strength (C/N₀ in dB-Hz).

- **Mock Location Detection** – Identifies when the device is reporting a spoofed GPS position and flags it visually with a `⚠ MOCK` warning.

- **NMEA Debug Log** – A built-in terminal (accessible via the `>_` button) that captures and displays raw NMEA sentences (`GPGGA`, `GPRMC`, `GNGGA`, `GNRMC`) in real time, keeping the last 80 entries.
  
- **Radar Animations** – Custom `Canvas`-drawn radar with independent rotating layers, a sweeping line, and a pulsing centre - idle and scanning modes have distinct visual behaviours.
  
- **Copy to Clipboard** – Copy the formatted address with a single tap.
  
## Architecture
 
The project follows **MVVM** with a clean separation of concerns:
 
```
MainActivity (Compose UI)
    └── LocationScreen
          └── LocationViewModel
                ├── FusedLocationProviderClient   (one-shot GPS fix)
                ├── keepGpsAwake()                (keeps GPS chip active)
                ├── GnssStatus.Callback           (satellite telemetry)
                ├── OnNmeaMessageListener         (raw NMEA stream)
                └── GeocoderRepository            (reverse geocoding)
                      └── GeocoderRepositoryContract (interface / testable)
```

## Tech Stack
| Layer | Library / API |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Architecture | ViewModel + StateFlow (MVVM) |
| Location | Google Play Services - Fused Location Provider |
| Geocoding | `android.location.Geocoder` (native) |
| GNSS | `GnssStatus.Callback` + `OnNmeaMessageListener` |
| Async | Kotlin Coroutines + `suspendCancellableCoroutine` |

## Roadmap
 
- [x] Reverse geocoding
- [x] Copy address to clipboard
- [x] Radar animations (idle & scanning modes)
- [x] Real-time satellite monitoring
- [x] NMEA debug log

##

> [!NOTE]
> - This project was developed for educational purposes, so it may not provide a fully polished experience.

<hr>

<p align="center">Developed in Kotlin · Android · College project</p>
