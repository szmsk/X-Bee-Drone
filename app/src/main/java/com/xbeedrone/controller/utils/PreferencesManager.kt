package com.xbeedrone.controller.utils

import android.content.Context
import com.xbeedrone.controller.model.DroneSettings
import com.google.gson.Gson

class PreferencesManager(context: Context) {
    private val prefs = context.getSharedPreferences("xbee_drone_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveSettings(settings: DroneSettings) {
        prefs.edit().putString("settings", gson.toJson(settings)).apply()
    }

    fun loadSettings(): DroneSettings {
        val json = prefs.getString("settings", null) ?: return DroneSettings()
        return try {
            gson.fromJson(json, DroneSettings::class.java)
        } catch (e: Exception) {
            DroneSettings()
        }
    }
}
