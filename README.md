# OTG Print Hub

Print documents, images, PDFs, and text files directly from your Android phone to any USB printer using an OTG adapter.

## Features

- **Auto USB Printer Detection** - Detects any USB printer via OTG with VID/PID identification
- **Smart Driver Discovery** - 4-layer driver search: local cache → GitHub JSON DB → OpenPrinting.org → Generic fallback
- **Multi-Protocol Print Engine** - ESC/POS, PCL5, PCL6, ESC/P, PostScript, Direct PDF, Raw
- **PDF Rendering** - Native Android PdfRenderer with per-page bitmap output
- **Image Printing** - Floyd-Steinberg dithering for monochrome, full color support
- **Print Queue** - Room-backed offline print job tracking
- **Offline-First** - Works fully offline after first driver download
- **PPD Parser** - Parses OpenPrinting.org PPD files for printer capabilities

## Supported Printer Types

| Protocol    | Printers                          |
|-------------|-----------------------------------|
| ESC/POS     | Epson TM-T88, Star TSP, Zebra, Bixolon |
| PCL5        | HP LaserJet, Samsung, OKI        |
| PCL6        | HP, Brother, Kyocera              |
| ESC/P       | Epson Stylus, EcoTank             |
| PostScript  | Xerox, Sharp MX, Generic PS       |
| Direct PDF  | Canon PIXMA G-series              |
| Raw Text    | Any USB printer (fallback)        |

## Project Structure

```
app/src/main/java/com/otgprinthub/
├── di/                     Hilt dependency injection modules
├── data/
│   ├── local/              Room database (entities, DAOs)
│   ├── remote/             Retrofit APIs (GitHub DB, OpenPrinting)
│   └── repository/         Repository implementations
├── domain/
│   ├── model/              Domain models (Printer, Driver, PrintJob, ...)
│   ├── repository/         Repository interfaces
│   └── usecase/            Business logic use cases
├── usb/                    USB Host API layer
│   ├── UsbPrinterManager   Central USB orchestrator
│   ├── UsbPrinterDetector  Interface/endpoint detection
│   ├── UsbPrinterTransport Bulk transfer with retry & progress
│   └── UsbBroadcastReceiver Attach/detach events
├── driver/                 Driver management
│   ├── DriverManager       Orchestrates driver search & caching
│   ├── PpdParser           PPD file parser for printer capabilities
│   └── VidPidDatabase      USB vendor ID lookup
├── print/
│   ├── PrintEngine         Selects adapter and orchestrates printing
│   ├── PrintForegroundService  Android foreground service
│   ├── adapters/           Protocol adapters (EscPos, PCL, PostScript, ...)
│   └── renderer/           DocumentRenderer (PDF, image, text → Bitmap)
└── ui/                     Jetpack Compose screens + ViewModels
    ├── home/               Home screen with quick-print buttons
    ├── detection/          Printer detection + driver search UI
    ├── preview/            Print preview + settings panel
    ├── queue/              Print job queue management
    ├── driver/             Driver manager screen
    ├── printerdetails/     USB descriptor + driver details
    ├── diagnostics/        Raw USB info + export
    └── settings/           App preferences
```

## Architecture

**MVVM + Clean Architecture + Hilt DI**

```
UI (Compose) → ViewModel → UseCase → Repository Interface
                                          ↓
                                   Room DB / Retrofit / USB API
```

## Driver Database Format

```json
{
  "vid": "03f0",
  "pid": "2b17",
  "brand": "HP",
  "model": "LaserJet Pro M404dn",
  "protocol": "pcl5",
  "color": false,
  "duplex": true,
  "max_dpi": 1200,
  "paper_sizes": ["A4", "Letter"],
  "init_commands": "1B451B266C304F"
}
```

## Adding New Printer Profiles

1. Add an entry to `app/src/main/assets/default_printer_profiles.json`
2. OR host a `drivers.json` at your own URL and configure it in Settings → Driver Repository URL

## Minimum Requirements

- Android 8.0 (API 26) or higher
- USB OTG support (USB Host mode)
- OTG adapter cable

## Build

```bash
./gradlew assembleDebug
```

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **DI**: Hilt
- **Database**: Room
- **Network**: Retrofit + OkHttp + Gson
- **USB**: Android USB Host API
- **PDF**: Android PdfRenderer
- **Background**: WorkManager + Foreground Service

## License

Apache 2.0
