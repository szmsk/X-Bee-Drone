package com.xbeedrone.controller.ui.main

import android.app.Application
import android.content.Context
import android.os.Vibrator
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xbeedrone.controller.model.DroneSettings
import com.xbeedrone.controller.model.DroneState
import com.xbeedrone.controller.model.FlightMode
import com.xbeedrone.controller.model.Waypoint
import com.xbeedrone.controller.network.DroneConnectionManager
import com.xbeedrone.controller.network.MjpegStreamManager
import com.xbeedrone.controller.utils.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)
    private var settings = prefs.loadSettings()

    val connectionManager = DroneConnectionManager(settings)
    val streamManager = MjpegStreamManager(settings.videoStreamUrl)

    val droneState: StateFlow<DroneState> = connectionManager.droneState
    val isConnected: StateFlow<Boolean> = connectionManager.isConnected
    val errorMessage: StateFlow<String?> = connectionManager.errorMessage
    val frameBitmap = streamManager.frameBitmap
    val streamFps = streamManager.fps
    val isStreaming = streamManager.isStreaming

    private val _waypoints = MutableStateFlow<List<Waypoint>>(emptyList())
    val waypoints: StateFlow<List<Waypoint>> = _waypoints

    private val _warningMessage = MutableStateFlow<String?>(null)
    val warningMessage: StateFlow<String?> = _warningMessage

    private val vibrator = application.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    init {
        monitorBatteryWarnings()
    }

    // ─── Connection ──────────────────────────────────────────────────────────

    fun connect() {
        connectionManager.connect()
        streamManager.startStream()
    }

    fun disconnect() {
        connectionManager.disconnect()
        streamManager.stopStream()
    }

    // ─── Joystick control ────────────────────────────────────────────────────

    /**
     * Mode 2 (standard): Lewa oś = throttle/yaw, Prawa oś = pitch/roll
     */
    fun updateLeftJoystick(x: Float, y: Float) {
        val sens = settings.joystickSensitivity
        val yaw = applyExpo((x * 100 * sens).toInt())
        val throttle = applyExpo((-y * 100 * sens).toInt()) // Y odwrócone
        connectionManager.updateJoystick(throttle, yaw,
            droneState.value.let { 0 },  // pitch z prawego joysticka
            droneState.value.let { 0 }   // roll z prawego joysticka
        )
        leftY = throttle; leftX = yaw
        pushCombinedJoystick()
    }

    fun updateRightJoystick(x: Float, y: Float) {
        val sens = settings.joystickSensitivity
        rightX = applyExpo((x * 100 * sens).toInt())
        rightY = applyExpo((-y * 100 * sens).toInt())
        pushCombinedJoystick()
    }

    private var leftX = 0; private var leftY = 0
    private var rightX = 0; private var rightY = 0

    private fun pushCombinedJoystick() {
        connectionManager.updateJoystick(
            throttle = leftY,
            yaw = leftX,
            pitch = rightY,
            roll = rightX
        )
    }

    private fun applyExpo(value: Int): Int {
        if (!settings.useExponentialCurve) return value
        val v = value / 100f
        return (v * v * v * 100).toInt().coerceIn(-100, 100)
    }

    // ─── Actions ─────────────────────────────────────────────────────────────

    fun arm() = connectionManager.arm()
    fun disarm() = connectionManager.disarm()
    fun takeOff() = connectionManager.takeOff()
    fun land() = connectionManager.land()
    fun returnToHome() = connectionManager.returnToHome()
    fun takePhoto() = connectionManager.takePhoto()
    fun startRecording() = connectionManager.startRecording()
    fun stopRecording() = connectionManager.stopRecording()
    fun emergencyStop() {
        vibrate(longArrayOf(0, 200, 100, 200, 100, 200))
        connectionManager.emergencyStop()
    }
    fun flip(direction: String) = connectionManager.flip(direction)

    // ─── Waypoints ───────────────────────────────────────────────────────────

    fun addWaypoint(lat: Double, lon: Double, altitude: Float = 10f) {
        val current = _waypoints.value.toMutableList()
        val wp = Waypoint(
            id = System.currentTimeMillis().toInt(),
            latitude = lat,
            longitude = lon,
            altitude = altitude
        )
        current.add(wp)
        _waypoints.value = current
    }

    fun removeWaypoint(id: Int) {
        _waypoints.value = _waypoints.value.filter { it.id != id }
    }

    fun clearWaypoints() {
        _waypoints.value = emptyList()
    }

    fun startWaypointMission() {
        if (_waypoints.value.isEmpty()) return
        // Wysyłamy punkty trasy do drona przez TCP
        viewModelScope.launch {
            val wps = _waypoints.value
            Log.d("ViewModel", "Starting waypoint mission with ${wps.size} points")
            // W rzeczywistej implementacji: serialize waypoints -> TCP command
        }
    }

    // ─── Settings ────────────────────────────────────────────────────────────

    fun getSettings() = settings

    fun updateSettings(newSettings: DroneSettings) {
        settings = newSettings
        prefs.saveSettings(newSettings)
        connectionManager.updateSettings(newSettings)
    }

    // ─── Warnings ────────────────────────────────────────────────────────────

    private fun monitorBatteryWarnings() {
        viewModelScope.launch {
            droneState.collectLatest { state ->
                when {
                    state.batteryPercent <= settings.criticalBattery && state.batteryPercent > 0 -> {
                        _warningMessage.value = "⚠️ KRYTYCZNY POZIOM BATERII: ${state.batteryPercent}%!"
                        vibrate(longArrayOf(0, 500, 200, 500, 200, 500))
                    }
                    state.batteryPercent <= settings.lowBatteryWarning && state.batteryPercent > 0 -> {
                        _warningMessage.value = "🔋 Niski poziom baterii: ${state.batteryPercent}%"
                        vibrate(longArrayOf(0, 200, 100, 200))
                    }
                    state.altitude > settings.maxAltitudeLimit -> {
                        _warningMessage.value = "⚠️ Przekroczono maksymalną wysokość!"
                        vibrate(longArrayOf(0, 300, 100, 300))
                    }
                }
            }
        }
    }

    fun clearWarning() {
        _warningMessage.value = null
    }

    @Suppress("DEPRECATION")
    private fun vibrate(pattern: LongArray) {
        if (!settings.vibrationOnWarnings) return
        vibrator?.vibrate(pattern, -1)
    }

    override fun onCleared() {
        super.onCleared()
        connectionManager.destroy()
        streamManager.destroy()
    }
}
