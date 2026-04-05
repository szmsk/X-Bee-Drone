package com.xbeedrone.app

import android.app.Application
import android.util.Log
import org.osmdroid.config.Configuration
import java.io.File

class DroneApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Konfiguracja OSMDroid (mapy offline)
        Configuration.getInstance().apply {
            userAgentValue = packageName
            // Cache kafelków mapy w pamięci wewnętrznej
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }

        Log.d("DroneApp", "XBee Drone App started")
    }
}
