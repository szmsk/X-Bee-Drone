package com.xbeedrone.app.ui.telemetry

import android.graphics.Color
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.xbeedrone.app.DroneViewModel
import com.xbeedrone.app.databinding.FragmentTelemetryBinding
import com.xbeedrone.app.network.DroneConnection
import kotlinx.coroutines.launch

class TelemetryFragment : Fragment() {

    private var _b: FragmentTelemetryBinding? = null
    private val b get() = _b!!
    private val vm: DroneViewModel by activityViewModels()

    private var flightStartTime = 0L
    private var timerRunning = false
    private var homeLat = 0.0
    private var homeLon = 0.0

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentTelemetryBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        b.btnSetHome.setOnClickListener {
            val t = vm.telemetry.value
            if (t.latitude != 0.0) { homeLat = t.latitude; homeLon = t.longitude }
        }
        b.btnResetTimer.setOnClickListener { flightStartTime = System.currentTimeMillis() }
        b.btnResetTimer2.setOnClickListener { flightStartTime = System.currentTimeMillis() }

        observe()
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            vm.connectionState.collect { state ->
                b.tvConnectionStatus.text = when (state) {
                    DroneConnection.State.CONNECTED -> "✅ Połączono"
                    DroneConnection.State.CONNECTING -> "🔄 Łączenie..."
                    DroneConnection.State.ERROR -> "❌ Błąd"
                    else -> "❌ Brak połączenia"
                }
                b.tvConnectionStatus.setTextColor(
                    if (state == DroneConnection.State.CONNECTED)
                        Color.parseColor("#43A047") else Color.RED
                )
                if (state == DroneConnection.State.DISCONNECTED) timerRunning = false
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.telemetry.collect { t ->
                // Bateria
                b.batteryBar.progress = t.batteryPercent
                b.tvBatteryValue.text = "${t.batteryPercent}%"
                b.batteryBar.progressTintList =
                    android.content.res.ColorStateList.valueOf(when {
                        t.batteryPercent > 50 -> Color.parseColor("#43A047")
                        t.batteryPercent > 20 -> Color.YELLOW
                        else -> Color.RED
                    })

                // Wysokość i prędkość
                b.tvAltValue.text = "${"%.2f".format(t.altitudeM)} m"
                b.tvSpeedValue.text = "${"%.1f".format(t.speedKmh)} km/h"
                b.speedBar.progress = t.speedKmh.toInt().coerceIn(0, 40)

                // GPS
                b.tvLatValue.text = "${"%.6f".format(t.latitude)}°"
                b.tvLonValue.text = "${"%.6f".format(t.longitude)}°"
                b.tvSatValue.text = "${t.satellites}"
                b.tvSatValue.setTextColor(when {
                    t.satellites >= 8 -> Color.parseColor("#43A047")
                    t.satellites >= 5 -> Color.YELLOW
                    else -> Color.RED
                })

                // Sygnał
                b.signalBar.progress = t.signalStrength
                b.tvSignalValue.text = "${t.signalStrength}%"

                // Dystans od home
                if (homeLat != 0.0 && t.latitude != 0.0) {
                    val d = haversine(homeLat, homeLon, t.latitude, t.longitude)
                    b.tvDistValue.text = "${"%.1f".format(d)} m"
                }

                // Timer lotu
                if (t.isConnected && !timerRunning) {
                    flightStartTime = System.currentTimeMillis(); timerRunning = true
                }
                if (timerRunning) {
                    val sec = (System.currentTimeMillis() - flightStartTime) / 1000
                    b.tvFlightTime.text = "%02d:%02d".format(sec / 60, sec % 60)
                }
            }
        }
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2).let { it * it } +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2).let { it * it }
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
