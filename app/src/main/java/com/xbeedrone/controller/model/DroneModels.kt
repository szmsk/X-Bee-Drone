package com.xbeedrone.controller.model

/**
 * Stan drona - aktualizowany na żywo przez telemetrię WiFi.
 */
data class DroneState(
    val batteryPercent: Int = 0,          // %
    val batteryVoltage: Float = 0f,       // V
    val altitude: Float = 0f,             // m
    val speed: Float = 0f,                // km/h
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val heading: Int = 0,                 // stopnie (0-360)
    val rssi: Int = 0,                    // siła sygnału WiFi dBm
    val gpsFixCount: Int = 0,             // liczba satelitów GPS
    val isGpsFixed: Boolean = false,
    val isConnected: Boolean = false,
    val flightMode: FlightMode = FlightMode.MANUAL,
    val isMotorsArmed: Boolean = false,
    val isRecording: Boolean = false,
    val rollAngle: Float = 0f,            // stopnie
    val pitchAngle: Float = 0f,           // stopnie
    val distanceFromHome: Float = 0f,     // m
    val homeLat: Double = 0.0,
    val homeLon: Double = 0.0,
    val flightTimeSeconds: Int = 0,
    val maxAltitude: Float = 0f,
    val maxSpeed: Float = 0f
)

enum class FlightMode {
    MANUAL, GPS_HOLD, RETURN_TO_HOME, WAYPOINT, FOLLOW_ME, ALTITUDE_HOLD
}

/**
 * Komenda sterowania wysyłana do drona przez UDP/TCP.
 * Wartości joysticka: -100..100
 */
data class DroneCommand(
    val throttle: Int = 0,   // -100..100 (lewa oś Y)
    val yaw: Int = 0,         // -100..100 (lewa oś X)
    val pitch: Int = 0,       // -100..100 (prawa oś Y)
    val roll: Int = 0,        // -100..100 (prawa oś X)
    val arm: Boolean? = null,
    val takeOff: Boolean? = null,
    val land: Boolean? = null,
    val returnToHome: Boolean? = null,
    val takePhoto: Boolean? = null,
    val startRecording: Boolean? = null,
    val stopRecording: Boolean? = null,
    val flipDirection: String? = null,    // "left","right","forward","back"
    val emergencyStop: Boolean? = null
)

/**
 * Punkt trasy (waypoint) na mapie.
 */
data class Waypoint(
    val id: Int,
    val latitude: Double,
    val longitude: Double,
    val altitude: Float = 10f,
    val speed: Float = 5f,
    val action: WaypointAction = WaypointAction.HOVER
)

enum class WaypointAction {
    HOVER, TAKE_PHOTO, START_RECORDING, STOP_RECORDING, LAND
}

/**
 * Ustawienia drona konfigurowane przez użytkownika.
 */
data class DroneSettings(
    val droneIp: String = "192.168.10.1",
    val controlPort: Int = 8888,
    val telemetryPort: Int = 9999,
    val videoStreamUrl: String = "http://192.168.10.1:8080/?action=stream",
    val maxAltitudeLimit: Float = 120f,
    val maxDistanceLimit: Float = 500f,
    val returnToHomeAltitude: Float = 30f,
    val lowBatteryWarning: Int = 20,
    val criticalBattery: Int = 10,
    val joystickSensitivity: Float = 1.0f,
    val joystickMode: Int = 2,           // Mode 1 lub Mode 2
    val useExponentialCurve: Boolean = true,
    val vibrationOnWarnings: Boolean = true,
    val autoRecordOnArm: Boolean = false
)
