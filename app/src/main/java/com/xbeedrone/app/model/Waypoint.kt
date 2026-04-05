package com.xbeedrone.app.model

import org.osmdroid.util.GeoPoint

data class Waypoint(
    val id: Int,
    val geoPoint: GeoPoint,
    val altitudeM: Double = 10.0,
    var isReached: Boolean = false
)
