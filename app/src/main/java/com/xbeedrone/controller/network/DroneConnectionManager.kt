package com.xbeedrone.controller.network

import android.util.Log
import com.xbeedrone.controller.model.DroneCommand
import com.xbeedrone.controller.model.DroneSettings
import com.xbeedrone.controller.model.DroneState
import com.xbeedrone.controller.model.FlightMode
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.*

/**
 * Manages WiFi communication with XBee Drone 9.5 Fold.
 *
 * Protocol:
 *  - Control commands: UDP packets to drone IP:controlPort
 *  - Telemetry data:   TCP socket from drone IP:telemetryPort (JSON lines)
 *  - Video stream:     HTTP MJPEG at videoStreamUrl (handled separately)
 *
 * Command format (JSON over UDP):
 * {"cmd":"ctrl","thr":0,"yaw":0,"pit":0,"rol":0}
 */
class DroneConnectionManager(private var settings: DroneSettings) {

    companion object {
        private const val TAG = "DroneConnection"
        private const val COMMAND_INTERVAL_MS = 50L   // 20Hz
        private const val TELEMETRY_RECONNECT_DELAY = 2000L
        private const val UDP_TIMEOUT_MS = 1000
        private const val TCP_TIMEOUT_MS = 3000
    }

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Aktualny stan drona
    private val _droneState = MutableStateFlow(DroneState())
    val droneState: StateFlow<DroneState> = _droneState

    // Połączenie
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    // Błędy
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    // Aktualny obiekt komendy (aktualizowany przez joystick)
    @Volatile private var currentCommand = DroneCommand()

    // Sockety
    private var udpSocket: DatagramSocket? = null
    private var telemetrySocket: Socket? = null

    // Jobs
    private var commandJob: Job? = null
    private var telemetryJob: Job? = null
    private var connectionCheckJob: Job? = null

    /**
     * Próbuje nawiązać połączenie z dronem.
     */
    fun connect() {
        scope.launch {
            try {
                Log.d(TAG, "Connecting to drone at ${settings.droneIp}")

                // Inicjalizuj UDP do wysyłania komend
                udpSocket?.close()
                udpSocket = DatagramSocket().apply {
                    soTimeout = UDP_TIMEOUT_MS
                    broadcast = false
                }

                // Uruchom pętlę wysyłania komend
                startCommandLoop()

                // Połącz się z telemetrią TCP
                startTelemetryLoop()

                // Sprawdzanie połączenia
                startConnectionCheck()

                _isConnected.value = true
                _droneState.value = _droneState.value.copy(isConnected = true)
                Log.d(TAG, "Connected successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Connection failed: ${e.message}")
                _errorMessage.value = "Błąd połączenia: ${e.message}"
                _isConnected.value = false
            }
        }
    }

    /**
     * Rozłącza od drona.
     */
    fun disconnect() {
        commandJob?.cancel()
        telemetryJob?.cancel()
        connectionCheckJob?.cancel()

        scope.launch {
            try {
                udpSocket?.close()
                telemetrySocket?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error during disconnect: ${e.message}")
            }
        }

        udpSocket = null
        telemetrySocket = null
        _isConnected.value = false
        _droneState.value = _droneState.value.copy(isConnected = false)
    }

    /**
     * Aktualizuje komendę joysticka (wątek UI).
     */
    fun updateJoystick(throttle: Int, yaw: Int, pitch: Int, roll: Int) {
        currentCommand = currentCommand.copy(
            throttle = throttle.coerceIn(-100, 100),
            yaw = yaw.coerceIn(-100, 100),
            pitch = pitch.coerceIn(-100, 100),
            roll = roll.coerceIn(-100, 100)
        )
    }

    /**
     * Wysyła jednorazową komendę akcji.
     */
    fun sendAction(command: DroneCommand) {
        scope.launch { sendUdpCommand(command) }
    }

    fun arm() = sendAction(DroneCommand(arm = true))
    fun disarm() = sendAction(DroneCommand(arm = false))
    fun takeOff() = sendAction(DroneCommand(takeOff = true))
    fun land() = sendAction(DroneCommand(land = true))
    fun returnToHome() = sendAction(DroneCommand(returnToHome = true))
    fun takePhoto() = sendAction(DroneCommand(takePhoto = true))
    fun startRecording() = sendAction(DroneCommand(startRecording = true))
    fun stopRecording() = sendAction(DroneCommand(stopRecording = true))
    fun emergencyStop() = sendAction(DroneCommand(emergencyStop = true))

    fun flip(direction: String) = sendAction(DroneCommand(flipDirection = direction))

    fun updateSettings(newSettings: DroneSettings) {
        settings = newSettings
    }

    // ─── Private helpers ────────────────────────────────────────────────────

    private fun startCommandLoop() {
        commandJob?.cancel()
        commandJob = scope.launch {
            while (isActive) {
                sendUdpCommand(currentCommand)
                delay(COMMAND_INTERVAL_MS)
            }
        }
    }

    private fun startTelemetryLoop() {
        telemetryJob?.cancel()
        telemetryJob = scope.launch {
            while (isActive) {
                try {
                    Log.d(TAG, "Connecting telemetry TCP...")
                    telemetrySocket = Socket().apply {
                        connect(
                            InetSocketAddress(settings.droneIp, settings.telemetryPort),
                            TCP_TIMEOUT_MS
                        )
                        soTimeout = TCP_TIMEOUT_MS
                    }

                    val reader = BufferedReader(
                        InputStreamReader(telemetrySocket!!.getInputStream())
                    )

                    // Czytaj linie JSON z danymi telemetrycznymi
                    while (isActive && telemetrySocket?.isConnected == true) {
                        val line = reader.readLine() ?: break
                        parseTelemetry(line)
                    }

                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Telemetry error: ${e.message}, reconnecting...")
                    _droneState.value = _droneState.value.copy(isConnected = false)
                }

                if (isActive) {
                    delay(TELEMETRY_RECONNECT_DELAY)
                }
            }
        }
    }

    private fun startConnectionCheck() {
        connectionCheckJob?.cancel()
        connectionCheckJob = scope.launch {
            while (isActive) {
                delay(5000)
                // Ping drone - wyślij heartbeat UDP
                try {
                    val ping = JSONObject().apply {
                        put("cmd", "ping")
                    }
                    sendRawUdp(ping.toString())
                } catch (e: Exception) {
                    Log.w(TAG, "Ping failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Parsuje JSON z telemetrii drona.
     * Format X-Bee Drone (przykładowy):
     * {"bat":85,"volt":3.82,"alt":12.5,"spd":4.2,"lat":54.352,"lon":18.646,
     *  "hdg":270,"rssi":-62,"sat":8,"fix":1,"rol":0.5,"pit":-1.2,"mode":1}
     */
    private fun parseTelemetry(json: String) {
        try {
            val obj = JSONObject(json)
            val current = _droneState.value
            _droneState.value = current.copy(
                batteryPercent = obj.optInt("bat", current.batteryPercent),
                batteryVoltage = obj.optDouble("volt", current.batteryVoltage.toDouble()).toFloat(),
                altitude = obj.optDouble("alt", current.altitude.toDouble()).toFloat(),
                speed = obj.optDouble("spd", current.speed.toDouble()).toFloat(),
                latitude = obj.optDouble("lat", current.latitude),
                longitude = obj.optDouble("lon", current.longitude),
                heading = obj.optInt("hdg", current.heading),
                rssi = obj.optInt("rssi", current.rssi),
                gpsFixCount = obj.optInt("sat", current.gpsFixCount),
                isGpsFixed = obj.optInt("fix", 0) == 1,
                rollAngle = obj.optDouble("rol", current.rollAngle.toDouble()).toFloat(),
                pitchAngle = obj.optDouble("pit", current.pitchAngle.toDouble()).toFloat(),
                isMotorsArmed = obj.optInt("armed", 0) == 1,
                isRecording = obj.optInt("rec", 0) == 1,
                flightTimeSeconds = obj.optInt("time", current.flightTimeSeconds),
                distanceFromHome = obj.optDouble("dist", current.distanceFromHome.toDouble()).toFloat(),
                isConnected = true,
                flightMode = parseFlightMode(obj.optInt("mode", 0))
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse telemetry: $json")
        }
    }

    private fun parseFlightMode(mode: Int) = when (mode) {
        0 -> FlightMode.MANUAL
        1 -> FlightMode.GPS_HOLD
        2 -> FlightMode.ALTITUDE_HOLD
        3 -> FlightMode.RETURN_TO_HOME
        4 -> FlightMode.WAYPOINT
        5 -> FlightMode.FOLLOW_ME
        else -> FlightMode.MANUAL
    }

    private suspend fun sendUdpCommand(command: DroneCommand) {
        try {
            val json = buildCommandJson(command)
            sendRawUdp(json)
        } catch (e: Exception) {
            // Ignoruj błędy UDP - drone może być chwilowo niedostępny
        }
    }

    private fun buildCommandJson(cmd: DroneCommand): String {
        val obj = JSONObject()
        obj.put("cmd", "ctrl")
        obj.put("thr", cmd.throttle)
        obj.put("yaw", cmd.yaw)
        obj.put("pit", cmd.pitch)
        obj.put("rol", cmd.roll)
        cmd.arm?.let { obj.put("arm", if (it) 1 else 0) }
        cmd.takeOff?.let { if (it) obj.put("act", "takeoff") }
        cmd.land?.let { if (it) obj.put("act", "land") }
        cmd.returnToHome?.let { if (it) obj.put("act", "rth") }
        cmd.takePhoto?.let { if (it) obj.put("act", "photo") }
        cmd.startRecording?.let { if (it) obj.put("act", "rec_start") }
        cmd.stopRecording?.let { if (it) obj.put("act", "rec_stop") }
        cmd.flipDirection?.let { obj.put("act", "flip_$it") }
        cmd.emergencyStop?.let { if (it) obj.put("act", "estop") }
        return obj.toString()
    }

    private fun sendRawUdp(data: String) {
        val bytes = data.toByteArray()
        val address = InetAddress.getByName(settings.droneIp)
        val packet = DatagramPacket(bytes, bytes.size, address, settings.controlPort)
        udpSocket?.send(packet)
    }

    fun destroy() {
        disconnect()
        scope.cancel()
    }
}
