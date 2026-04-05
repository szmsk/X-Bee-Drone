package com.xbeedrone.app.network

/**
 * Protokół komunikacji WiFi z dronem Overmax X-Bee 9.5 Fold
 *
 * Dron tworzy hotspot WiFi o nazwie "Drone_XXXXXXX"
 * Komunikacja oparta na UDP (komendy) + TCP (strumień wideo MJPEG)
 *
 * Adresy (standardowe dla tej klasy dronów MJX/Overmax):
 *  - IP drona:       192.168.0.1
 *  - Port komend:    UDP 8080
 *  - Port wideo:     TCP 8888
 *  - Port telemetrii: UDP 8889
 *
 * Format pakietu kontrolnego (11 bajtów, co 50ms):
 *  [0]    = 0xFF  (nagłówek)
 *  [1]    = 0x04  (stały)
 *  [2]    = throttle   (0x00–0xFF, środek=0x80)
 *  [3]    = yaw        (0x00–0xFF, środek=0x80)
 *  [4]    = pitch      (0x00–0xFF, środek=0x80)
 *  [5]    = roll       (0x00–0xFF, środek=0x80)
 *  [6]    = flagi      (tryb lotu, headless, itd.)
 *  [7]    = checksum   (XOR bajtów 2-6)
 *  [8]    = 0x00
 *  [9]    = 0x00
 *  [10]   = 0x00
 *
 * Uwaga: Jeśli Twój egzemplarz drona używa innego protokołu,
 *         użyj Wireshark lub tPacketCapture do przechwycenia
 *         oryginalnych pakietów M RC PRO i dostosuj poniższe stałe.
 */
object DroneProtocol {

    // ── Sieć ──────────────────────────────────────────────────────────────
    const val DRONE_IP              = "192.168.0.1"
    const val DRONE_IP_ALT          = "192.168.1.1"   // alternatywny (starsze firmware)
    const val PORT_CMD              = 8080             // UDP – komendy sterowania
    const val PORT_VIDEO            = 8888             // TCP – strumień MJPEG
    const val PORT_TELEMETRY        = 8889             // UDP – telemetria (opcjonalny)
    const val CMD_INTERVAL_MS       = 50L              // interwał wysyłania komend

    // ── Wartości osi joysticka ────────────────────────────────────────────
    const val AXIS_CENTER           = 0x80             // neutralna pozycja
    const val AXIS_MIN              = 0x00
    const val AXIS_MAX              = 0xFF

    // ── Bajty nagłówka pakietu ────────────────────────────────────────────
    const val PKT_HEADER_0          = 0xFF.toByte()
    const val PKT_HEADER_1          = 0x04.toByte()

    // ── Flagi trybu lotu (bajt [6]) ───────────────────────────────────────
    const val FLAG_NORMAL           = 0x00.toByte()
    const val FLAG_HEADLESS         = 0x01.toByte()
    const val FLAG_ALTITUDE_HOLD    = 0x02.toByte()
    const val FLAG_GPS_MODE         = 0x04.toByte()

    // ── Komendy specjalne (pełny pakiet) ─────────────────────────────────
    val CMD_TAKEOFF = byteArrayOf(
        0xFF.toByte(), 0x04, 0x00, 0x80.toByte(),
        0x80.toByte(), 0x80.toByte(), 0x08, 0x00, 0x00, 0x00, 0x00
    )
    val CMD_LAND = byteArrayOf(
        0xFF.toByte(), 0x04, 0x00, 0x80.toByte(),
        0x80.toByte(), 0x80.toByte(), 0x10, 0x00, 0x00, 0x00, 0x00
    )
    val CMD_RETURN_HOME = byteArrayOf(
        0xFF.toByte(), 0x04, 0x00, 0x80.toByte(),
        0x80.toByte(), 0x80.toByte(), 0x20, 0x00, 0x00, 0x00, 0x00
    )
    val CMD_EMERGENCY_STOP = byteArrayOf(
        0xFF.toByte(), 0x04, 0x00, 0x80.toByte(),
        0x80.toByte(), 0x80.toByte(), 0x40, 0x00, 0x00, 0x00, 0x00
    )
    val CMD_CALIBRATE = byteArrayOf(
        0xFF.toByte(), 0x04, 0x00, 0x80.toByte(),
        0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x00, 0x00, 0x00, 0x00
    )
    val CMD_IDLE = byteArrayOf(
        0xFF.toByte(), 0x04, 0x00, 0x80.toByte(),
        0x80.toByte(), 0x80.toByte(), FLAG_NORMAL, 0x00, 0x00, 0x00, 0x00
    )

    // ── Komenda video-stream (4 bajty, inicjuje strumień MJPEG) ──────────
    val CMD_START_VIDEO = byteArrayOf(
        0xEF.toByte(), 0x00, 0x04, 0x00
    )

    // ── Komendy kamery ────────────────────────────────────────────────────
    val CMD_TAKE_PHOTO = byteArrayOf(
        0xFF.toByte(), 0x04, 0x00, 0x80.toByte(),
        0x80.toByte(), 0x80.toByte(), 0x00, 0x01, 0x00, 0x00, 0x00
    )
    val CMD_RECORD_START = byteArrayOf(
        0xFF.toByte(), 0x04, 0x00, 0x80.toByte(),
        0x80.toByte(), 0x80.toByte(), 0x00, 0x02, 0x00, 0x00, 0x00
    )
    val CMD_RECORD_STOP = byteArrayOf(
        0xFF.toByte(), 0x04, 0x00, 0x80.toByte(),
        0x80.toByte(), 0x80.toByte(), 0x00, 0x03, 0x00, 0x00, 0x00
    )

    // ── Budowanie pakietu kontrolnego ─────────────────────────────────────
    /**
     * Tworzy pakiet UDP ze sterowaniem.
     *
     * @param throttle  Gaz:     0–255 (0 = brak, 255 = max)
     * @param yaw       Obrót:   0–255 (128 = środek)
     * @param pitch     Przód/tył: 0–255 (128 = środek)
     * @param roll      Lewo/prawo: 0–255 (128 = środek)
     * @param flags     Flagi trybu lotu
     */
    fun buildControlPacket(
        throttle: Int,
        yaw: Int,
        pitch: Int,
        roll: Int,
        flags: Byte = FLAG_NORMAL
    ): ByteArray {
        val t = throttle.coerceIn(AXIS_MIN, AXIS_MAX).toByte()
        val y = yaw.coerceIn(AXIS_MIN, AXIS_MAX).toByte()
        val p = pitch.coerceIn(AXIS_MIN, AXIS_MAX).toByte()
        val r = roll.coerceIn(AXIS_MIN, AXIS_MAX).toByte()

        // Checksum = XOR bajtów 2–6
        val checksum = (t.toInt() xor y.toInt() xor p.toInt() xor r.toInt() xor flags.toInt()).toByte()

        return byteArrayOf(
            PKT_HEADER_0, PKT_HEADER_1,
            t, y, p, r, flags, checksum,
            0x00, 0x00, 0x00
        )
    }
}
