# OTG Print Hub — Printer Protocol Guide

Yeh document L1455 ke saath humari puri development journey ka summary hai.
Future mein naye printer add karte time yahi galtiyan dobara na ho.

---

## 1. Sabse Pehle Karo: Printer Ka Protocol Pata Karo

Printer connect hone par USB GET_DEVICE_ID se IEEE 1284 string milti hai.
Yeh string batati hai ki printer kaunsa protocol support karta hai.

```
MFG:EPSON;CMD:ESCPL2,BDC,D4,D4PX,ESCPR7,END4;MDL:L1455 Series;CLS:PRINTER;
```

`CMD:` field mein dekho:

| CMD Value   | Protocol                  | Adapter            |
|-------------|---------------------------|--------------------|
| `ESCPR7`    | ESC/P-R (ESCPR) raster    | `PrintHelper` (ESCP2 route) |
| `ESCPL2`    | ESC/P-R legacy            | same               |
| `PCL`       | HP PCL                    | `PclAdapter`       |
| `PostScript`| PostScript                | `PostScriptAdapter`|
| `ESC/P`     | Old ESC/P dot matrix      | `EscPAdapter`      |

**Galti jo humne ki:** CMD field padhe bina directly ESC/P2 raster bhejte rahe.
L1455 ne silently discard kiya — koi error nahi, sirf blank page.

---

## 2. Epson ESCPR Protocol (L1455 / EcoTank / InkTank series)

### 2.1 Puri Command Sequence

```
exitPacketMode()     → 3 NUL + ESC 0x01 + "@EJL 1284.4\n@EJL     \n"
printerReset()       → ESC @
enterRemote1()       → ESC(R 08 00 00 "REMOTE1"
  timestamp()        →   TI + 6 zero bytes
  jobStart()         →   JS + 3 zero bytes
  paperPath()        →   PP + [00 01 00]
exitRemote1()        → ESC 00 00 00
enterEscprMode()     → ESC(R 06 00 00 "ESCPR"   ← CRITICAL, see section 2.2
setQuality()         → ESC q ... "setq" [9 bytes]
setJob()             → ESC j ... "setj" [22 bytes, ALL BIG-ENDIAN]
startPage()          → ESC p ... "sttp"
pageNumber(1)        → ESC p ... "setn" [01]
  sendLine(y, data)  → ESC d ... "dsnd" [header + row_data]  × H lines
endPage(0)           → ESC p ... "endp" [00]
printerReset()       → ESC @   ← endj/JE/REMOTE1 cleanup mat bhejo — blank page aata hai!
```

**CRITICAL (L1455 quirk):** `endJob()` (endj) aur REMOTE1 cleanup (LD+JE) dono blank
page eject karte hain. Correct ending sequence:

```
endPage(0)       ← page close karo (pagesLeft=0 for last copy)
printerReset()   ← ESC @ ejects cleanly, no blank page
```

`endJob` (endj) bilkul mat bhejo — isse ek extra blank page eject hota hai.
Agla job ka printerReset lingering ESCPR state handle karega.

### 2.2 Sabse Badi Galti — enterEscprMode()

**GALAT (jo humne pehle bheja):**
```
ESC ( G  01 00  01
1B  28  47  01 00  01   ← Classic ESC/P2 raster mode
```

**SAHI:**
```
ESC ( R  06 00  00  E  S  C  P  R
1B  28  52  06 00  00  45 53 43 50 52   ← ESCPR packet mode
```

L1455 `ESC(G` (classic raster) ko completely ignore karta hai.
`ESC(R ... "ESCPR"` ke baad hi raster commands accept karta hai.

Source: `python-epson/epson/escpr.py`, `epson-inkjet-printer-escpr` official driver.

### 2.3 setJob — Paper Dimensions (22 bytes, BIG-ENDIAN)

```kotlin
fun setJob(widthPx: Int, heightPx: Int, dpi: Int = 360): ByteArray {
    // ir: 0=360DPI, 1=720DPI, 2=300DPI, 3=600DPI
    // pd: 0=BIDIREC, 1=UNIDIREC
    // Layout: paperW(4BE) paperH(4BE) marginTop(2BE) marginLeft(2BE)
    //         printW(4BE) printH(4BE) ir(1) pd(1)
}
```

**Galti:** Pehle Little-Endian use kiya tha. Printer ne "printing size big" warning diya.
**Fix:** Sab fields Big-Endian.

A4 @ 360 DPI:
- `widthPx  = (210.0 / 25.4 * 360).toInt() = 2976`
- `heightPx = (297.0 / 25.4 * 360).toInt() = 4209`

### 2.4 dsnd — Per-Line Raster Data (BIG-ENDIAN header)

```
ESC d  [len_LE4]  "dsnd"  [x_off(2BE)] [y_off(2BE)] [cmode(1)] [line_len(2BE)] [pixels...]
```

**Galti:** Pehle band-based `ESC(e` use kiya tha (ESC/P2 style).
ESCPR mein har line alag `dsnd` command se bhejni hoti hai.

### 2.5 REMOTE1 Command Format

```
[name: 2 bytes] [len: 2 bytes LE = 1 + data.size] [0x00] [data]
```

### 2.6 rasterCmd Format

```
ESC [cmd: 1 byte] [data_len: 4 bytes LE] [code: 4 bytes ASCII] [data]
```

Note: `len_LE4` mein sirf data ka size aata hai, code ke 4 bytes nahi.

---

## 3. Pixel Encoding — Sabse Critical Bug

**ESCPR CM.MONOCHROME (cm=1): 1 byte per pixel**

| Byte Value | Meaning            | Output      |
|------------|--------------------|-------------|
| `0x00`     | 0 luminance = dark | **Black ink**   |
| `0xFF`     | 255 luminance = light | **No ink (white)** |

Yeh standard RGB luminance convention follow karta hai, ink density nahi.

**GALAT code (jo humne pehle likha):**
```kotlin
// WRONG — inverted!
row[x] = (255 - lum).toByte()   // White pixel → 0xFF → black ink (GALAT)
val solidBlack = ByteArray(w) { 0xFF.toByte() }  // 0xFF ≠ black in ESCPR!
```

**SAHI code:**
```kotlin
// CORRECT — luminance directly
row[x] = lum.toByte()            // White pixel (lum=255) → 0xFF → no ink ✓
val solidBlack = ByteArray(w) { 0x00 }  // 0x00 = full ink = black ✓
```

**Symptom:** Test bhejne par blank paper aaya, protocol sahi tha.
**Diagnosis:** `solidBlackInkRows()` mein `0xFF` tha — printer ne white page print kiya.

---

## 4. Android URI Permission — Document Print Bug

### Problem

`ACTION_OPEN_DOCUMENT` se mili content:// URI activity context se accessible hoti hai.
Lekin `@ApplicationContext` (ViewModel mein) se us URI ko read karne par Android deny karta hai:

```
Permission Denial: reading MediaDocumentsProvider uri
content://com.android.providers.media.documents/document/...
requires that you obtain access using ACTION_OPEN_DOCUMENT or related APIs
```

`takePersistableUriPermission` call karne ke baad bhi ApplicationContext se deny hota hai.

### Fix

**HomeScreen (Activity context) mein hi file copy karo cache mein:**

```kotlin
// HomeScreen.persistAndNavigate() — Activity context available hai yahan
val finalUri = withContext(Dispatchers.IO) {
    val dest = File(context.cacheDir, "printjob_${System.currentTimeMillis()}$ext")
    context.contentResolver.openInputStream(uri)
        ?.use { it.copyTo(dest.outputStream()) }
    if (dest.exists() && dest.length() > 0) Uri.fromFile(dest) else uri
}
// Ab file:// URI ViewModel ko bhejo — ApplicationContext se hamesha accessible
navController.navigate(Screen.PrintPreview.createRoute(encoded, fileType.name))
```

**Rule:** ViewModel ko kabhi content:// URI mat bhejo. Hamesha pehle `file://` mein convert karo.

---

## 5. USB Printer Class Initialization

Har print job se pehle yeh sequence zaruri hai:

```kotlin
// 1. claimInterface
connection.claimInterface(usbInterface, true)

// 2. GET_DEVICE_ID — printer ka IEEE 1284 string milta hai
connection.controlTransfer(0xA1, 0x00, 0, interfaceId, buf, buf.size, 5000)
// Parse: "MFG:EPSON;CMD:ESCPR7,...;MDL:L1455 Series;"

// 3. SOFT_RESET — stuck jobs clear karta hai
connection.controlTransfer(0x21, 0x02, 0, interfaceId, null, 0, 5000)
Thread.sleep(200)   // reset complete hone do

// 4. GET_PORT_STATUS — error bits clear karta hai
connection.controlTransfer(0xA1, 0x01, 0, interfaceId, buf, 1, 5000)
// bit 0x20 = paperEmpty, 0x10 = select, 0x08 = notError
```

**Galti:** Pehle directly bulkTransfer bhejte the bina initialization ke.
Result: Printer respond nahi karta tha ya blink karta tha.

---

## 6. Naye Printer Add Karte Time Checklist

- [ ] USB connect hone par GET_DEVICE_ID string log karo
- [ ] `CMD:` field se protocol identify karo
- [ ] Protocol ke liye sahi adapter/path use karo:
  - `ESCPR7` → `PrintProtocol.ESCP2` → `PrintHelper` (ESCPR path)
  - `PCL` → `PrintProtocol.PCL` → `PclAdapter`
  - `PostScript` → `PrintProtocol.POSTSCRIPT` → `PostScriptAdapter`
- [ ] Driver database mein VID+PID entry add karo with correct protocol
- [ ] T1 (ESCPR Solid) se test shuru karo — agar yeh kaam kare toh protocol sahi hai
- [ ] Pixel encoding verify karo — blank page = inverted encoding
- [ ] Paper size dimensions printer ke paper se match karo — "size big" warning = setJob galat

---

## 7. Debug Tools

### AppLogger (In-App)

```kotlin
AppLogger.i(TAG, "message")   // info
AppLogger.e(TAG, "error")     // error
AppLogger.separator("label")  // ══ label ══ separator
AppLogger.getAll()            // sab logs ek string mein
```

HomeScreen mein "View / Share Debug Logs" button se logs share karo.

### USB Device ID Parsing

Printer connect hone par log mein dekho:
```
I/UsbPrinterTransport: DeviceID: MFG:EPSON;CMD:ESCPL2,BDC,ESCPR7;MDL:L1455 Series;...
```

`CMD:` mein `ESCPR7` → ESCPR protocol.

### Port Status

```
I/UsbPrinterTransport: Port status: 0x18 (paperEmpty=false, select=true, notError=true)
```

- `paperEmpty=true` → paper nahi hai → print nahi hoga
- `select=false` → printer offline hai
- `notError=false` → printer error state mein hai

---

## 8. CI/CD — GitHub Actions

### local.properties

CI pe yeh file nahi hoti. Build step se pehle banao:

```yaml
- name: Write local.properties
  run: echo "sdk.dir=$ANDROID_HOME" > local.properties
```

### Gradle Wrapper JAR

Agar `./gradlew` fail kare with "Could not find or load main class":
- Wrapper JAR corrupt ho sakti hai
- Fix: `gradle/actions/setup-gradle@v4` use karo with `validate-wrappers: false`
  aur system `gradle` command se build karo (not `./gradlew`)

```yaml
- uses: gradle/actions/setup-gradle@v4
  with:
    gradle-version: '8.5'
    validate-wrappers: false
- run: gradle assembleDebug --stacktrace
```

---

## 9. Protocol Test Pages (Kaunsa Test Kab Use Karo)

| Test | Kab Use Karo |
|------|--------------|
| T1 ESCPR Solid | Sabse pehle — protocol aur pixel encoding check |
| T2 ESCPR Gray | T1 ke baad — ink density control check |
| T3 Text Mode | Agar T1 fail kare — basic USB connection check |
| T4 ESCPR Stripes | Band accuracy check |
| T5 ESCPR Nozzle | Nozzle clogging check (vertical lines) |
| T6 ESCPR 500 Lines | Large data transfer stability check |

**T1 blank = pixel encoding galat (0x00/0xFF swap check karo)**
**T1 blink = protocol galat (CMD field aur enterEscprMode check karo)**
**T3 print + T1 blank = ESCPR mode entry galat**

---

## 10. Confirmed Working — Epson L1455

- VID: `04b8`, PID: `1113`
- Protocol: ESCPR (CMD: `ESCPR7`)
- DPI: 360 (ir=0 in setJob)
- Paper: A4 — 2976 x 4209 px at 360 DPI
- Color mode: CM.MONOCHROME (cm=1), 1 byte/pixel
- Pixel: 0x00 = black ink, 0xFF = white (no ink)
- Confirmed working: T1 solid black stripe prints on paper
