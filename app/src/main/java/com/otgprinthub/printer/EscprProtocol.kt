package com.otgprinthub.printer

import android.util.Log

/**
 * ESC/P-R (ESCPR) command builder — protocol confirmed from:
 *   python-epson/epson/escpr.py (ezrec, MIT)
 *   epson-inkjet-printer-escpr official Epson driver source
 *
 * Critical differences from classic ESC/P2:
 *   - Raster mode entry: ESC(R 06 00 00 "ESCPR"  (NOT ESC(G)
 *   - Raster data: per-line "dsnd" command         (NOT ESC(e bands)
 *   - Pixel format: 1 byte/pixel for CM.MONO       (NOT 1bpp packed)
 *   - setq/setj replace ESC(U / ESC(C / ESC(D
 *
 * Full job sequence (L1455 confirmed):
 *   exitPacketMode → REMOTE1(TI+JS+PP) → exitRemote1
 *   → enterEscprMode → setQuality → setJob
 *   → [per copy: startPage → pageNumber → sendLine×H → endPage(pagesLeft)]
 *   → endJob
 *
 *   NOTE: printerReset (ESC @) is intentionally OMITTED at job start.
 *   ESC @ performs a form-feed which ejects any paper pre-loaded by the previous
 *   endJob, producing a blank page. SOFT_RESET via USB control request + exitPacketMode
 *   handle state cleanup without paper advance.
 *   REMOTE1(LD+JE) cleanup at end also omitted — causes blank page on L1455.
 */
object EscprProtocol {

    private const val TAG = "ESCPR"
    private val ESC = 0x1B.toByte()

    // ── Phase 1: Exit packet mode ─────────────────────────────────────────────

    /**
     * EJL sequence: exits any packet/EJL mode the printer may be stuck in.
     * Always send this first, before ESC @.
     * Source: python-epson escp.py _exit_packet_mode()
     */
    fun exitPacketMode(): ByteArray {
        Log.d(TAG, "exitPacketMode")
        // 3 NUL + ESC 0x01 + "@EJL 1284.4\n@EJL     \n"
        val ejl = "@EJL 1284.4\n@EJL     \n"
        val result = ByteArray(3 + 1 + ejl.length)
        result[3] = ESC
        ejl.toByteArray(Charsets.ISO_8859_1).copyInto(result, 4)
        return result
    }

    // ── Phase 2: Printer reset ────────────────────────────────────────────────

    fun printerReset(): ByteArray {
        Log.d(TAG, "printerReset: ESC @")
        return byteArrayOf(ESC, 0x40)
    }

    // ── Phase 3: REMOTE1 commands ─────────────────────────────────────────────

    /** ESC(R 08 00 00 "REMOTE1" */
    fun enterRemote1(): ByteArray {
        Log.d(TAG, "enterRemote1")
        return byteArrayOf(
            ESC, 0x28, 0x52, 0x08, 0x00, 0x00,
            'R'.code.toByte(), 'E'.code.toByte(), 'M'.code.toByte(), 'O'.code.toByte(),
            'T'.code.toByte(), 'E'.code.toByte(), '1'.code.toByte()
        )
    }

    /**
     * REMOTE1 command format (per python-epson escp.py _remote1_cmd):
     *   [name 2][len_LE2 = 1 + data.size][0x00][data]
     * The 0x00 is the "response requested" flag byte, counted in len.
     */
    fun remoteCmd(name: String, data: ByteArray = byteArrayOf()): ByteArray {
        Log.d(TAG, "remoteCmd: $name (${data.size} bytes)")
        val len = 1 + data.size
        val out = ByteArray(2 + 2 + 1 + data.size)
        out[0] = name[0].code.toByte()
        out[1] = name[1].code.toByte()
        out[2] = (len and 0xFF).toByte()
        out[3] = ((len shr 8) and 0xFF).toByte()
        out[4] = 0x00  // response byte
        data.copyInto(out, 5)
        return out
    }

    /** TI — timestamp (6 bytes: year, month, day, hour, min, sec = 0 = ignored) */
    fun timestamp() = remoteCmd("TI", byteArrayOf(0, 0, 0, 0, 0, 0))

    /** JS — Job Start */
    fun jobStart() = remoteCmd("JS", byteArrayOf(0, 0, 0))

    /** PP — Paper Path (rear tray = source 0x01) */
    fun paperPath() = remoteCmd("PP", byteArrayOf(0x00, 0x01, 0x00))

    /** ESC 00 00 00 — exit REMOTE1 */
    fun exitRemote1(): ByteArray {
        Log.d(TAG, "exitRemote1")
        return byteArrayOf(ESC, 0x00, 0x00, 0x00)
    }

    // ── Phase 4: ESC/P-R raster mode ─────────────────────────────────────────

    /**
     * ESC(R 06 00 00 "ESCPR" — enter ESC/P-R raster mode.
     *
     * THIS IS THE KEY COMMAND. Old wrong code sent ESC(G (classic ESC/P2).
     * L1455 requires this ESCPR packet mode to accept raster data.
     */
    fun enterEscprMode(): ByteArray {
        Log.i(TAG, "enterEscprMode: 1B 28 52 06 00 00 ESCPR")
        return byteArrayOf(
            ESC, 0x28, 0x52, 0x06, 0x00, 0x00,
            'E'.code.toByte(), 'S'.code.toByte(), 'C'.code.toByte(),
            'P'.code.toByte(), 'R'.code.toByte()
        )
    }

    /**
     * Generic ESCPR raster command.
     * Format: ESC + cmd(1) + len_LE4(data.size only) + code(4) + data
     * Source: python-epson escpr.py _raster_cmd()
     */
    fun rasterCmd(cmd: Byte, code: String, data: ByteArray = byteArrayOf()): ByteArray {
        val len = data.size
        val header = byteArrayOf(
            ESC, cmd,
            (len          and 0xFF).toByte(),
            ((len shr  8) and 0xFF).toByte(),
            ((len shr 16) and 0xFF).toByte(),
            ((len shr 24) and 0xFF).toByte()
        )
        val codeBytes = code.toByteArray(Charsets.US_ASCII)
        val result = ByteArray(header.size + codeBytes.size + data.size)
        header.copyInto(result)
        codeBytes.copyInto(result, header.size)
        data.copyInto(result, header.size + codeBytes.size)
        return result
    }

    /**
     * setq — media type, quality, color mode.
     * @param mtid  0=PLAIN, 1=INKJET360, 5=MATTE, 6=PHOTO
     * @param mqid  0=DRAFT, 1=NORMAL, 2=HIGH
     * @param cm    0=COLOR (3 bytes/px RGB), 1=MONOCHROME (1 byte/px)
     *
     * Data layout (9 bytes):
     *   mtid(1) mqid(1) cm(1) brightness(1) contrast(1) saturation(1) cp(1) palette_len_BE(2)
     */
    fun setQuality(mtid: Int = 0, mqid: Int = 1, cm: Int = 1): ByteArray {
        Log.i(TAG, "setQuality: mtid=$mtid mqid=$mqid cm=${if (cm == 0) "COLOR" else "MONO"}")
        val data = byteArrayOf(
            mtid.toByte(), mqid.toByte(), cm.toByte(),
            0, 0, 0,  // brightness, contrast, saturation
            0,        // cp = FULLCOLOR
            0, 0      // palette length (BE) = 0
        )
        return rasterCmd('q'.code.toByte(), "setq", data)
    }

    /**
     * setj — paper dimensions and resolution.
     * All dimension fields are BIG-ENDIAN (python-epson struct.pack(">LLHHLLBB")).
     * Borderless: printableWidth = paperWidth, margins = 0.
     *
     * ir: 0=360DPI, 1=720DPI, 2=300DPI, 3=600DPI
     * pd: 0=BIDIREC, 1=UNIDIREC
     *
     * Data layout (22 bytes):
     *   paperWidth(4BE) paperHeight(4BE) marginTop(2BE) marginLeft(2BE)
     *   printableW(4BE) printableH(4BE) ir(1) pd(1)
     */
    fun setJob(widthPx: Int, heightPx: Int, dpi: Int = 360, unidirec: Boolean = false): ByteArray {
        val ir = when (dpi) { 720 -> 1; 300 -> 2; 600 -> 3; else -> 0 }
        val pd = if (unidirec) 1 else 0
        Log.i(TAG, "setJob: ${widthPx}x${heightPx}px @${dpi}DPI ir=$ir pd=${if (unidirec) "UNIDIREC" else "BIDIREC"}")

        fun int32BE(v: Int) = byteArrayOf(
            ((v shr 24) and 0xFF).toByte(), ((v shr 16) and 0xFF).toByte(),
            ((v shr  8) and 0xFF).toByte(), (v          and 0xFF).toByte()
        )

        val data = ByteArray(22)
        int32BE(widthPx).copyInto(data, 0)    // paperWidth
        int32BE(heightPx).copyInto(data, 4)   // paperHeight
        // data[8..11] = marginTop(2BE) + marginLeft(2BE) = 0x00
        int32BE(widthPx).copyInto(data, 12)   // printableWidth = paperWidth
        int32BE(heightPx).copyInto(data, 16)  // printableHeight
        data[20] = ir.toByte()
        data[21] = pd.toByte()

        return rasterCmd('j'.code.toByte(), "setj", data)
    }

    /** sttp — start page */
    fun startPage(): ByteArray {
        Log.d(TAG, "startPage: sttp")
        return rasterCmd('p'.code.toByte(), "sttp")
    }

    /** setn — page number (1-based) */
    fun pageNumber(n: Int): ByteArray {
        Log.d(TAG, "pageNumber: $n")
        return rasterCmd('p'.code.toByte(), "setn", byteArrayOf(n.toByte()))
    }

    /**
     * dsnd — send one raster line.
     *
     * Inner header is BIG-ENDIAN (python-epson struct.pack(">HHBH")):
     *   x_offset(2BE)  y_offset(2BE)  cmode(1)  line_len(2BE)
     *
     * Pixel values (luminance convention, same for COLOR and MONO on L1455):
     *   0x00 = 0 luminance = dark  → black ink
     *   0xFF = 255 luminance = light → no ink (white)
     */
    fun sendLine(y: Int, lineData: ByteArray): ByteArray {
        val lineLen = lineData.size
        val header = ByteArray(7)
        header[0] = 0x00; header[1] = 0x00                           // x_offset = 0 (BE)
        header[2] = ((y      shr 8) and 0xFF).toByte()
        header[3] = (y       and 0xFF).toByte()                       // y_offset (BE)
        header[4] = 0x00                                              // cmode = uncompressed
        header[5] = ((lineLen shr 8) and 0xFF).toByte()
        header[6] = (lineLen  and 0xFF).toByte()                      // line_len (BE)

        val payload = ByteArray(header.size + lineData.size)
        header.copyInto(payload)
        lineData.copyInto(payload, header.size)
        return rasterCmd('d'.code.toByte(), "dsnd", payload)
    }

    /** endp — end page (pagesLeft = pages still to print, 0 = this is the last) */
    fun endPage(pagesLeft: Int = 0): ByteArray {
        Log.d(TAG, "endPage: pagesLeft=$pagesLeft")
        return rasterCmd('p'.code.toByte(), "endp", byteArrayOf(pagesLeft.toByte()))
    }

    /** endj — end print job */
    fun endJob(): ByteArray {
        Log.d(TAG, "endJob")
        return rasterCmd('j'.code.toByte(), "endj")
    }

    // ── Cleanup REMOTE1 commands ──────────────────────────────────────────────

    /** LD — load printer defaults */
    fun loadDefaults() = remoteCmd("LD")

    /** JE — job end (sent in final REMOTE1 phase) */
    fun jobEnd() = remoteCmd("JE", byteArrayOf(0, 0, 0))
}
