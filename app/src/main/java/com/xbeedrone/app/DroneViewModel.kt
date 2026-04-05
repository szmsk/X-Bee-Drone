package com.xbeedrone.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xbeedrone.app.model.Waypoint
import com.xbeedrone.app.network.DroneConnection
import com.xbeedrone.app.network.DroneProtocol
import kotlinx.coroutines.flow.*
import org.osmdroid.util.GeoPoint

class DroneViewModel(application: Application) : AndroidViewModel(application) {

    val connection = DroneConnection()

    val connectionState = connection.connectionState
    val videoFrame      = connection.videoFrame
    val telemetry       = connection.telemetry
    val errorMessage    = connection.errorMessage

    private val _waypoints = MutableStateFlow<List<Waypoint>>(emptyList())
    val waypoints: StateFlow<List<Waypoint>> = _waypoints

    private val _isHeadless     = MutableStateFlow(false)
    val isHeadless: StateFlow<Boolean> = _isHeadless

    private val _isAltitudeHold = MutableStateFlow(true)
    val isAltitudeHold: StateFlow<Boolean> = _isAltitudeHold

    private val _isRecording    = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    val droneLocation: StateFlow<GeoPoint?> = telemetry
        .map { t -> if (t.latitude != 0.0 && t.longitude != 0.0) GeoPoint(t.latitude, t.longitude) else null }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private var nextWaypointId = 1

    fun connect()    = connection.connect()
    fun disconnect() = connection.disconnect()

    fun setControls(throttle: Int, yaw: Int, pitch: Int, roll: Int) =
        connection.setControls(throttle, yaw, pitch, roll)

    fun takeoff()        = connection.sendTakeoff()
    fun land()           = connection.sendLand()
    fun returnHome()     = connection.sendReturnHome()
    fun emergencyStop()  = connection.sendEmergencyStop()
    fun takePhoto()      = connection.sendTakePhoto()

    fun toggleRecording() { _isRecording.value = !_isRecording.value }

    fun toggleHeadless() {
        _isHeadless.value = !_isHeadless.value
        updateFlags()
    }

    fun toggleAltitudeHold() {
        _isAltitudeHold.value = !_isAltitudeHold.value
        updateFlags()
    }

    private fun updateFlags() {
        var f = DroneProtocol.FLAG_NORMAL.toInt()
        if (_isHeadless.value)     f = f or DroneProtocol.FLAG_HEADLESS.toInt()
        if (_isAltitudeHold.value) f = f or DroneProtocol.FLAG_ALTITUDE_HOLD.toInt()
        connection.setFlightMode(f.toByte())
    }

    fun addWaypoint(geoPoint: GeoPoint, altM: Double = 10.0) {
        _waypoints.value = _waypoints.value + Waypoint(nextWaypointId++, geoPoint, altM)
    }

    fun removeWaypoint(id: Int) {
        _waypoints.value = _waypoints.value.filter { it.id != id }
    }

    fun clearWaypoints() {
        _waypoints.value = emptyList()
        nextWaypointId = 1
    }

    override fun onCleared() {
        super.onCleared()
        connection.destroy()
    }
}
