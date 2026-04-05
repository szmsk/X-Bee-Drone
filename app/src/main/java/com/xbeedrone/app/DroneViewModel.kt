package com.xbeedrone.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xbeedrone.app.model.Waypoint
import com.xbeedrone.app.network.DroneConnection
import com.xbeedrone.app.network.DroneProtocol
import com.xbeedrone.app.network.TelemetryData
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

class DroneViewModel(application: Application) : AndroidViewModel(application) {

    val connection = DroneConnection()

    // ── Stany z połączenia ────────────────────────────────────────────────
    val connectionState = connection.connectionState
    val videoFrame      = connection.videoFrame
    val telemetry       = connection.telemetry
    val errorMessage    = connection.errorMessage

    // ── Waypoints ─────────────────────────────────────────────────────────
    private val _waypoints = MutableStateFlow<List<Waypoint>>(emptyList())
    val waypoints: StateFlow<List<Waypoint>> = _waypoints

    private var nextWaypointId = 1

    // ── Tryby lotu ────────────────────────────────────────────────────────
    private val _isHeadless = MutableStateFlow(false)
    val isHeadless: StateFlow<Boolean> = _isHeadless

    private val _isAltitudeHold = MutableStateFlow(true)
    val isAltitudeHold: StateFlow<Boolean> = _isAltitudeHold

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    // ── Pozycja GPS telefonu (Follow Me) ──────────────────────────────────
    private val _phoneLocation = MutableStateFlow<GeoPoint?>(null)
    val phoneLocation: StateFlow<GeoPoint?> = _phoneLocation

    // ── Drona lokalizacja (z telemetrii) ─────────────────────────────────
    val droneLocation: StateFlow<GeoPoint?> = telemetry
        .map { t ->
            if (t.latitude != 0.0 && t.longitude != 0.0)
                GeoPoint(t.latitude, t.longitude)
            else null
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    // ── Akcje połączenia ──────────────────────────────────────────────────

    fun connect() = connection.connect()
    fun disconnect() = connection.disconnect()

    // ── Sterowanie ────────────────────────────────────────────────────────

    fun setControls(throttle: Int, yaw: Int, pitch: Int, roll: Int) {
        connection.setControls(throttle, yaw, pitch, roll)
    }

    fun takeoff() = connection.sendTakeoff()
    fun land() = connection.sendLand()
    fun returnHome() = connection.sendReturnHome()
    fun emergencyStop() = connection.sendEmergencyStop()
    fun calibrate() = connection.sendCalibrate()

    fun takePhoto() = connection.sendTakePhoto()

    fun toggleRecording() {
        connection.sendRecordToggle(_isRecording.value)
        _isRecording.value = !_isRecording.value
    }

    // ── Tryby lotu ────────────────────────────────────────────────────────

    fun toggleHeadless() {
        _isHeadless.value = !_isHeadless.value
        updateFlightFlags()
    }

    fun toggleAltitudeHold() {
        _isAltitudeHold.value = !_isAltitudeHold.value
        updateFlightFlags()
    }

    private fun updateFlightFlags() {
        var flags = DroneProtocol.FLAG_NORMAL
        if (_isHeadless.value) flags = (flags.toInt() or DroneProtocol.FLAG_HEADLESS.toInt()).toByte()
        if (_isAltitudeHold.value) flags = (flags.toInt() or DroneProtocol.FLAG_ALTITUDE_HOLD.toInt()).toByte()
        connection.setFlightMode(flags)
    }

    // ── Waypoints ─────────────────────────────────────────────────────────

    fun addWaypoint(geoPoint: GeoPoint, altitudeM: Double = 10.0) {
        val wp = Waypoint(nextWaypointId++, geoPoint, altitudeM)
        _waypoints.value = _waypoints.value + wp
    }

    fun removeWaypoint(id: Int) {
        _waypoints.value = _waypoints.value.filter { it.id != id }
    }

    fun clearWaypoints() {
        _waypoints.value = emptyList()
        nextWaypointId = 1
    }

    // ── Lokalizacja telefonu ───────────────────────────────────────────────

    fun updatePhoneLocation(lat: Double, lon: Double) {
        _phoneLocation.value = GeoPoint(lat, lon)
    }

    // ─────────────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        connection.destroy()
    }
}
