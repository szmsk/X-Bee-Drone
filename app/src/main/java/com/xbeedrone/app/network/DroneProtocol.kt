package com.xbeedrone.app.network

object DroneProtocol {
    const val DRONE_IP          = "192.168.0.1"
    const val PORT_CMD          = 8080
    const val PORT_VIDEO        = 8888
    const val PORT_TELEMETRY    = 8889
    const val CMD_INTERVAL_MS   = 50L

    const val AXIS_CENTER = 0x80
    const val AXIS_MIN    = 0x00
    const val AXIS_MAX    = 0xFF

    val FLAG_NORMAL       = 0x00.toByte()
    val FLAG_HEADLESS     = 0x01.toByte()
    val FLAG_ALTITUDE_HOLD= 0x02.toByte()

    val CMD_IDLE = buildControlPacket(AXIS_CENTER, AXIS_CENTER, AXIS_CENTER, AXIS_CENTER)

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
        0xFF.toByte(), 0x04, 0x00, 0x00,
        0x80.toByte(), 0x80.toByte(), 0x40, 0x00, 0x00, 0x00, 0x00
    )
    val CMD_START_VIDEO = byteArrayOf(0xEF.toByte(), 0x00, 0x04, 0x00)
    val CMD_TAKE_PHOTO = byteArrayOf(
        0xFF.toByte(), 0x04, 0x00, 0x80.toByte(),
        0x80.toByte(), 0x80.toByte(), 0x00, 0x01, 0x00, 0x00, 0x00
    )

    fun buildControlPacket(
        throttle: Int,
        yaw: Int,
        pitch: Int,
        roll: Int,
        flags: Byte = 0x00
    ): ByteArray {
        val t = throttle.coerceIn(AXIS_MIN, AXIS_MAX).toByte()
        val y = yaw.coerceIn(AXIS_MIN, AXIS_MAX).toByte()
        val p = pitch.coerceIn(AXIS_MIN, AXIS_MAX).toByte()
        val r = roll.coerceIn(AXIS_MIN, AXIS_MAX).toByte()
        val checksum = (t.toInt() xor y.toInt() xor p.toInt() xor r.toInt() xor flags.toInt()).toByte()
        return byteArrayOf(0xFF.toByte(), 0x04, t, y, p, r, flags, checksum, 0x00, 0x00, 0x00)
    }
}
