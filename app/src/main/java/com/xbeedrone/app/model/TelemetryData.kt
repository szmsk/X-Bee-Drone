package com.xbeedrone.app.model

data class TelemetryData(
    val batteryPercent: Int = 0,
    val altitudeM: Double = 0.0,
    val speedKmh: Double = 0.0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val satellites: Int = 0,
    val signalStrength: Int = 0,
    val isConnected: Boolean = false
)
