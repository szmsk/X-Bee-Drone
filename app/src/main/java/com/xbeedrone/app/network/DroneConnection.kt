package com.xbeedrone.app.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.*

/**
 * Zarządza połączeniem WiFi z dronem X-Bee 9.5 Fold.
 *
 * Architektura:
 *  - Wątek CMD:   wysyła pakiety UDP co 50ms (sterowanie)
 *  - Wątek VIDEO: odbiera strumień TCP i dekoduje klatki MJPEG
 *  - Wątek TELEM: (opcjonalny) odbiera dane telemetryczne UDP
 */
class DroneConnection {

    companion object {
        private const val TAG = "DroneConnection"
        private const val TIMEOUT_MS = 3000
        private const val VIDEO_BUFFER_SIZE = 65536
    }

    // ── Stan połączenia ───────────────────────────────────────────────────
    enum class State { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

    private val _connectionState = MutableStateFlow(State.DISCONNECTED)
    val connectionState: StateFlow<State> = _connectionState

    private val _videoFrame = MutableStateFlow<Bitmap?>(null)
    val videoFrame: StateFlow<Bitmap?> = _videoFrame

    private val _telemetry = MutableStateFlow(TelemetryData())
    val telemetry: StateFlow<TelemetryData> = _telemetry

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    // ── Sockety ───────────────────────────────────────────────────────────
    private var cmdSocket: DatagramSocket? = null
    private var videoSocket: Socket? = null
    private var telemetrySocket: DatagramSocket? = null

    // ── Komendy sterowania ────────────────────────────────────────────────
    @Volatile private var currentThrottle = DroneProtocol.AXIS_CENTER
    @Volatile private var currentYaw      = DroneProtocol.AXIS_CENTER
    @Volatile private var currentPitch    = DroneProtocol.AXIS_CENTER
    @Volatile private var currentRoll     = DroneProtocol.AXIS_CENTER
    @Volatile private var currentFlags    = DroneProtocol.FLAG_NORMAL
    @Volatile private var isArmed         = false

    // ── Coroutine scope ───────────────────────────────────────────────────
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var cmdJob: Job? = null
    private var videoJob: Job? = null
    private var telemetryJob: Job? = null

    // ─────────────────────────────────────────────────────────────────────
    //  POŁĄCZENIE / ROZŁĄCZENIE
    // ─────────────────────────────────────────────────────────────────────

    fun connect(droneIp: String = DroneProtocol.DRONE_IP) {
        if (_connectionState.value == State.CONNECTED ||
            _connectionState.value == State.CONNECTING) return

        scope.launch {
            _connectionState.value = State.CONNECTING
            _errorMessage.value = null
            Log.d(TAG, "Connecting to drone at $droneIp")

            try {
                // UDP socket do komend
                cmdSocket = DatagramSocket().apply {
                    soTimeout = TIMEOUT_MS
                    broadcast = false
                }

                // Test połączenia – wyślij pakiet IDLE
                sendRawUdp(DroneProtocol.CMD_IDLE, droneIp)

                _connectionState.value = State.CONNECTED
                Log.d(TAG, "Connected to drone!")

                // Uruchom pętle
                startCommandLoop(droneIp)
                startVideoStream(droneIp)
                startTelemetryListener(droneIp)

            } catch (e: Exception) {
                Log.e(TAG, "Connection failed: ${e.message}")
                _connectionState.value = State.ERROR
                _errorMessage.value = "Nie można połączyć z dronem: ${e.message}\n" +
                    "Sprawdź czy jesteś połączony z siecią WiFi drona (Drone_XXXXXXX)"
                cleanupSockets()
            }
        }
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting...")
        cmdJob?.cancel()
        videoJob?.cancel()
        telemetryJob?.cancel()
        cleanupSockets()
        _connectionState.value = State.DISCONNECTED
        _videoFrame.value = null
        isArmed = false
    }

    // ─────────────────────────────────────────────────────────────────────
    //  PĘTLE ROBOCZE
    // ─────────────────────────────────────────────────────────────────────

    private fun startCommandLoop(droneIp: String) {
        cmdJob = scope.launch {
            Log.d(TAG, "Command loop started")
            while (isActive && _connectionState.value == State.CONNECTED) {
                try {
                    val packet = DroneProtocol.buildControlPacket(
                        throttle = currentThrottle,
                        yaw      = currentYaw,
                        pitch    = currentPitch,
                        roll     = currentRoll,
                        flags    = currentFlags
                    )
                    sendRawUdp(packet, droneIp)
                } catch (e: Exception) {
                    if (isActive) Log.w(TAG, "CMD send error: ${e.message}")
                }
                delay(DroneProtocol.CMD_INTERVAL_MS)
            }
            Log.d(TAG, "Command loop ended")
        }
    }

    private fun startVideoStream(droneIp: String) {
        videoJob = scope.launch {
            Log.d(TAG, "Starting video stream...")
            var retryCount = 0
            while (isActive && _connectionState.value == State.CONNECTED && retryCount < 5) {
                try {
                    videoSocket = Socket().apply {
                        soTimeout = TIMEOUT_MS
                        connect(InetSocketAddress(droneIp, DroneProtocol.PORT_VIDEO), TIMEOUT_MS)
                    }

                    // Wyślij komendę inicjującą stream
                    videoSocket?.getOutputStream()?.write(DroneProtocol.CMD_START_VIDEO)
                    videoSocket?.getOutputStream()?.flush()

                    Log.d(TAG, "Video stream connected, reading frames...")
                    val inputStream = videoSocket?.getInputStream() ?: break
                    readMjpegStream(inputStream)

                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Video stream error: ${e.message}")
                    retryCount++
                    videoSocket?.close()
                    videoSocket = null
                    if (isActive) delay(1000)
                }
            }
            Log.d(TAG, "Video job ended")
        }
    }

    /**
     * Odczytuje strumień MJPEG (sekwencja JPEG-ów oddzielona nagłówkami HTTP-like).
     * Format typowy: --boundary\r\nContent-Type: image/jpeg\r\n\r\n[JPEG data]
     * Niektóre drony wysyłają surowe JPEG bez nagłówków (szukamy SOI/EOI: FF D8 ... FF D9).
     */
    private suspend fun readMjpegStream(inputStream: InputStream) {
        val buffer = ByteArrayOutputStream(VIDEO_BUFFER_SIZE)
        val readBuf = ByteArray(4096)
        var inJpeg = false
        var prevByte = 0

        while (isActive) {
            val bytesRead = inputStream.read(readBuf)
            if (bytesRead == -1) break

            for (i in 0 until bytesRead) {
                val b = readBuf[i].toInt() and 0xFF

                if (!inJpeg) {
                    // Szukaj SOI (Start Of Image): 0xFF 0xD8
                    if (prevByte == 0xFF && b == 0xD8) {
                        inJpeg = true
                        buffer.reset()
                        buffer.write(0xFF)
                        buffer.write(0xD8)
                    }
                } else {
                    buffer.write(b)
                    // Szukaj EOI (End Of Image): 0xFF 0xD9
                    if (prevByte == 0xFF && b == 0xD9) {
                        // Mamy kompletną klatkę JPEG
                        val jpegData = buffer.toByteArray()
                        decodeAndEmitFrame(jpegData)
                        buffer.reset()
                        inJpeg = false
                    }
                }
                prevByte = b
            }
        }
    }

    private suspend fun decodeAndEmitFrame(jpegData: ByteArray) {
        try {
            val bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)
            if (bitmap != null) {
                _videoFrame.value = bitmap
            }
        } catch (e: Exception) {
            Log.w(TAG, "Frame decode error: ${e.message}")
        }
    }

    private fun startTelemetryListener(droneIp: String) {
        telemetryJob = scope.launch {
            try {
                telemetrySocket = DatagramSocket(DroneProtocol.PORT_TELEMETRY).apply {
                    soTimeout = 2000
                }
                val buf = ByteArray(128)
                val packet = DatagramPacket(buf, buf.size)

                while (isActive && _connectionState.value == State.CONNECTED) {
                    try {
                        telemetrySocket?.receive(packet)
                        parseTelemetry(buf, packet.length)
                    } catch (e: SocketTimeoutException) {
                        // Timeout – dron mógł nie wysłać telemetrii, kontynuuj
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Telemetry listener error: ${e.message}")
            }
        }
    }

    /**
     * Parsuje surowy pakiet telemetrii.
     * Format jest specyficzny dla firmware – tu używamy przykładowego układu:
     *  [0-1]  nagłówek
     *  [2]    battery %
     *  [3-4]  altitude (cm, big-endian)
     *  [5-6]  speed (cm/s)
     *  [7-10] GPS lat (int32, * 1e-7)
     *  [11-14] GPS lon (int32, * 1e-7)
     *  [15]   GPS satellite count
     *  [16]   signal strength 0-100
     */
    private fun parseTelemetry(data: ByteArray, len: Int) {
        if (len < 17) return
        try {
            val battery   = data[2].toInt() and 0xFF
            val altCm     = ((data[3].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)
            val speedCmS  = ((data[5].toInt() and 0xFF) shl 8) or (data[6].toInt() and 0xFF)
            val latRaw    = ((data[7].toInt() and 0xFF) shl 24) or
                            ((data[8].toInt() and 0xFF) shl 16) or
                            ((data[9].toInt() and 0xFF) shl 8)  or
                            (data[10].toInt() and 0xFF)
            val lonRaw    = ((data[11].toInt() and 0xFF) shl 24) or
                            ((data[12].toInt() and 0xFF) shl 16) or
                            ((data[13].toInt() and 0xFF) shl 8)  or
                            (data[14].toInt() and 0xFF)
            val satellites = data[15].toInt() and 0xFF
            val signal     = data[16].toInt() and 0xFF

            _telemetry.value = TelemetryData(
                batteryPercent = battery,
                altitudeM      = altCm / 100.0,
                speedKmh       = speedCmS * 3.6 / 100.0,
                latitude       = latRaw * 1e-7,
                longitude      = lonRaw * 1e-7,
                satellites     = satellites,
                signalStrength = signal,
                isConnected    = true
            )
        } catch (e: Exception) {
            Log.w(TAG, "Telemetry parse error: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  STEROWANIE
    // ─────────────────────────────────────────────────────────────────────

    /** Aktualizuje stan joysticków (wywoływane z UI thread) */
    fun setControls(throttle: Int, yaw: Int, pitch: Int, roll: Int) {
        currentThrottle = throttle
        currentYaw = yaw
        currentPitch = pitch
        currentRoll = roll
    }

    fun setFlightMode(flags: Byte) { currentFlags = flags }

    fun sendTakeoff() {
        scope.launch {
            repeat(3) {
                sendRawUdp(DroneProtocol.CMD_TAKEOFF, DroneProtocol.DRONE_IP)
                delay(100)
            }
            isArmed = true
        }
    }

    fun sendLand() {
        scope.launch {
            repeat(3) {
                sendRawUdp(DroneProtocol.CMD_LAND, DroneProtocol.DRONE_IP)
                delay(100)
            }
            isArmed = false
        }
    }

    fun sendReturnHome() {
        scope.launch { sendRawUdp(DroneProtocol.CMD_RETURN_HOME, DroneProtocol.DRONE_IP) }
    }

    fun sendEmergencyStop() {
        currentThrottle = 0x00
        currentYaw = DroneProtocol.AXIS_CENTER
        currentPitch = DroneProtocol.AXIS_CENTER
        currentRoll = DroneProtocol.AXIS_CENTER
        scope.launch {
            repeat(5) {
                sendRawUdp(DroneProtocol.CMD_EMERGENCY_STOP, DroneProtocol.DRONE_IP)
                delay(50)
            }
        }
    }

    fun sendTakePhoto() {
        scope.launch { sendRawUdp(DroneProtocol.CMD_TAKE_PHOTO, DroneProtocol.DRONE_IP) }
    }

    fun sendRecordToggle(isRecording: Boolean) {
        val cmd = if (isRecording) DroneProtocol.CMD_RECORD_STOP else DroneProtocol.CMD_RECORD_START
        scope.launch { sendRawUdp(cmd, DroneProtocol.DRONE_IP) }
    }

    fun sendCalibrate() {
        scope.launch { sendRawUdp(DroneProtocol.CMD_CALIBRATE, DroneProtocol.DRONE_IP) }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  NARZĘDZIA WEWNĘTRZNE
    // ─────────────────────────────────────────────────────────────────────

    private fun sendRawUdp(data: ByteArray, ip: String) {
        try {
            val addr = InetAddress.getByName(ip)
            val pkt = DatagramPacket(data, data.size, addr, DroneProtocol.PORT_CMD)
            cmdSocket?.send(pkt)
        } catch (e: Exception) {
            Log.e(TAG, "UDP send failed: ${e.message}")
        }
    }

    private fun cleanupSockets() {
        try { cmdSocket?.close() } catch (_: Exception) {}
        try { videoSocket?.close() } catch (_: Exception) {}
        try { telemetrySocket?.close() } catch (_: Exception) {}
        cmdSocket = null
        videoSocket = null
        telemetrySocket = null
    }

    fun destroy() {
        scope.cancel()
        cleanupSockets()
    }
}
