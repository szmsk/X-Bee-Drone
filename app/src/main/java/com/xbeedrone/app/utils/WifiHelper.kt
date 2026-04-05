package com.xbeedrone.app.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager

/**
 * Narzędzie pomocnicze do zarządzania połączeniem WiFi z dronem.
 *
 * Dron X-Bee 9.5 Fold tworzy hotspot WiFi 5GHz o nazwie "Drone_XXXXXXX"
 * (gdzie XXXXXXX to unikalne ID drona).
 *
 * Użytkownik musi ręcznie połączyć się z tą siecią w Ustawieniach telefonu,
 * a następnie uruchomić aplikację.
 */
object WifiHelper {

    private const val DRONE_SSID_PREFIX = "Drone_"

    /**
     * Sprawdza czy telefon jest połączony z siecią WiFi drona.
     * Zwraca true jeśli SSID zaczyna się od "Drone_".
     */
    fun isConnectedToDrone(context: Context): Boolean {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager

        if (!wifiManager.isWifiEnabled) return false

        val connInfo = wifiManager.connectionInfo
        val ssid = connInfo.ssid?.trim('"') ?: return false

        return ssid.startsWith(DRONE_SSID_PREFIX, ignoreCase = true)
    }

    /**
     * Zwraca aktualnie połączony SSID lub null.
     */
    fun getCurrentSsid(context: Context): String? {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wifiManager.connectionInfo?.ssid?.trim('"')
    }

    /**
     * Sprawdza czy urządzenie ma aktywne połączenie sieciowe.
     */
    fun hasNetworkConnection(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
               caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * Zwraca siłę sygnału WiFi w procentach (0–100).
     */
    fun getSignalStrengthPercent(context: Context): Int {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        val rssi = wifiManager.connectionInfo?.rssi ?: return 0
        val level = WifiManager.calculateSignalLevel(rssi, 101)
        return level.coerceIn(0, 100)
    }
}
