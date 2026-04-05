package com.xbeedrone.app.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.xbeedrone.app.model.TelemetryData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.*

class DroneConnection {

    companion object {
        private const val TAG = "DroneConnection"
        private const val TIMEOUT_MS = 3000
    }

    enum class State { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

    private val _connectionState = MutableStateFlow(State.DISCONNECTED)
    val connectionState: StateFlow<State> = _connectionState

    private val _videoFrame = MutableStateFlow<Bitmap?>(null)
    val videoFrame: StateFlow<Bitmap?> = _videoFrame

    private val _telemetry = MutableStateFlow(TelemetryData())
    val telemetry: StateFlow<TelemetryData> = _telemetry

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private var cmdSocket: DatagramSocket? = null
    private var videoSocket: Socket? = null

    @Volatile private var currentThrottle = DroneProtocol.AXIS_CENTER
    @Volatile private var currentYaw      = DroneProtocol.AXIS_CENTER
    @Volatile private var currentPitch    = DroneProtocol.AXIS_CENTER
    @Volatile private var currentRoll     = DroneProtocol.AXIS_CENTER
    @Volatile private var currentFlags    = DroneProtocol.FLAG_NORMAL

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var cmdJob: Job? = null
    private var videoJob: Job? = null

    fun connect(droneIp: String = DroneProtocol.DRONE_IP) {
        if (_connectionState.value == State.CONNECTED ||
            _connectionState.value == State.CONNECTING) return

        scope.launch {
            _connectionState.value = State.CONNECTING
            _errorMessage.value = null
            try {
                cmdSocket = DatagramSocket()
                sendRawUdp(DroneProtocol.CMD_IDLE, droneIp)
                _connectionState.value = State.CONNECTED
                startCommandLoop(droneIp)
                startVideoStream(droneIp)
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed: ${e.message}")
                _connectionState.value = State.ERROR
                _errorMessage.value = "Błąd: ${e.message}\nSprawdź WiFi drona (Drone_XXXXXXX)"
                cleanupSockets()
            }
        }
    }

    fun disconnect() {
        cmdJob?.cancel()
        videoJob?.cancel()
        cleanupSockets()
        _connectionState.value = State.DISCONNECTED
        _videoFrame.value = null
    }

    private fun startCommandLoop(droneIp: String) {
        cmdJob = scope.launch {
            while (isActive && _connectionState.value == State.CONNECTED) {
                try {
                    val pkt = DroneProtocol.buildControlPacket(
                        currentThrottle, currentYaw, currentPitch, currentRoll, currentFlags
                    )
                    sendRawUdp(pkt, droneIp)
                } catch (e: Exception) {
                    if (isActive) Log.w(TAG, "CMD error: ${e.message}")
                }
                delay(DroneProtocol.CMD_INTERVAL_MS)
            }
        }
    }

    private fun startVideoStream(droneIp: String) {
        videoJob = scope.launch {
            var retries = 0
            while (isActive && _connectionState.value == State.CONNECTED && retries < 5) {
                try {
                    videoSocket = Socket().apply {
                        soTimeout = TIMEOUT_MS
                        connect(InetSocketAddress(droneIp, DroneProtocol.PORT_VIDEO), TIMEOUT_MS)
                    }
                    videoSocket?.getOutputStream()?.let {
                        it.write(DroneProtocol.CMD_START_VIDEO)
                        it.flush()
                    }
                    val stream = videoSocket?.getInputStream() ?: break
                    readMjpegStream(stream)
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Video error: ${e.message}")
                    retries++
                    videoSocket?.close()
                    videoSocket = null
                    if (isActive) delay(2000)
                }
            }
        }
    }

    private suspend fun readMjpegStream(inputStream: InputStream) {
        val buffer = ByteArrayOutputStream(65536)
        val chunk = ByteArray(4096)
        var inJpeg = false
        var prev = 0

        while (isActive) {
            val n = inputStream.read(chunk)
            if (n == -1) break
            for (i in 0 until n) {
                val b = chunk[i].toInt() and 0xFF
                if (!inJpeg) {
                    if (prev == 0xFF && b == 0xD8) {
                        inJpeg = true
                        buffer.reset()
                        buffer.write(0xFF)
                        buffer.write(0xD8)
                    }
                } else {
                    buffer.write(b)
                    if (prev == 0xFF && b == 0xD9) {
                        val data = buffer.toByteArray()
                        val bmp = BitmapFactory.decodeByteArray(data, 0, data.size)
                        if (bmp != null) _videoFrame.value = bmp
                        buffer.reset()
                        inJpeg = false
                    }
                }
                prev = b
            }
        }
    }

    fun setControls(throttle: Int, yaw: Int, pitch: Int, roll: Int) {
        currentThrottle = throttle; currentYaw = yaw
        currentPitch = pitch; currentRoll = roll
    }

    fun setFlightMode(flags: Byte) { currentFlags = flags }

    fun sendTakeoff()        { sendCmd(DroneProtocol.CMD_TAKEOFF) }
    fun sendLand()           { sendCmd(DroneProtocol.CMD_LAND) }
    fun sendReturnHome()     { sendCmd(DroneProtocol.CMD_RETURN_HOME) }
    fun sendEmergencyStop()  {
        currentThrottle = 0
        currentYaw = DroneProtocol.AXIS_CENTER
        currentPitch = DroneProtocol.AXIS_CENTER
        currentRoll = DroneProtocol.AXIS_CENTER
        sendCmd(DroneProtocol.CMD_EMERGENCY_STOP)
    }
    fun sendTakePhoto()      { sendCmd(DroneProtocol.CMD_TAKE_PHOTO) }

    private fun sendCmd(cmd: ByteArray) {
        scope.launch { repeat(3) { sendRawUdp(cmd, DroneProtocol.DRONE_IP); delay(60) } }
    }

    private fun sendRawUdp(data: ByteArray, ip: String) {
        try {
            val addr = InetAddress.getByName(ip)
            cmdSocket?.send(DatagramPacket(data, data.size, addr, DroneProtocol.PORT_CMD))
        } catch (e: Exception) {
            Log.e(TAG, "UDP send: ${e.message}")
        }
    }

    private fun cleanupSockets() {
        try { cmdSocket?.close() } catch (_: Exception) {}
        try { videoSocket?.close() } catch (_: Exception) {}
        cmdSocket = null; videoSocket = null
    }

    fun destroy() { scope.cancel(); cleanupSockets() }
}
