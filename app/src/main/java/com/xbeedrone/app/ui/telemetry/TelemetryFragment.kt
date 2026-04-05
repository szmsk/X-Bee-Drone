package com.xbeedrone.app.ui.telemetry

import android.graphics.Color
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.xbeedrone.app.DroneViewModel
import com.xbeedrone.app.databinding.FragmentTelemetryBinding
import kotlinx.coroutines.launch

/**
 * Ekran szczegółowej telemetrii.
 * Wyświetla wszystkie dane z drona:
 *  - Bateria (dron i kontroler)
 *  - Wysokość
 *  - Prędkość
 *  - GPS (współrzędne, liczba satelitów, dokładność)
 *  - Siła sygnału WiFi
 *  - Czas lotu
 *  - Dystans od punktu startowego
 */
class TelemetryFragment : Fragment() {

    private var _binding: FragmentTelemetryBinding? = null
    private val binding get() = _binding!!
    private val vm: DroneViewModel by activityViewModels()

    private var flightStartTime = 0L
    private var isFlightTimerRunning = false
    private var homeLat = 0.0
    private var homeLon = 0.0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTelemetryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeState()
        setupButtons()
    }

    private fun setupButtons() {
        // Ustaw punkt startowy (Home)
        binding.btnSetHome.setOnClickListener {
            val t = vm.telemetry.value
            if (t.latitude != 0.0 && t.longitude != 0.0) {
                homeLat = t.latitude
                homeLon = t.longitude
                binding.tvHomeSet.text = "Home: ${"%.5f".format(homeLat)}, ${"%.5f".format(homeLon)}"
            }
        }

        // Reset licznika lotu
        val resetAction = {
            flightStartTime = System.currentTimeMillis()
        }
        binding.btnResetTimer.setOnClickListener { resetAction() }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            vm.telemetry.collect { t ->
                // Bateria
                binding.batteryBar.progress = t.batteryPercent
                binding.tvBatteryValue.text = "${t.batteryPercent}%"
                binding.batteryBar.progressTintList =
                    android.content.res.ColorStateList.valueOf(
                        when {
                            t.batteryPercent > 50 -> Color.parseColor("#43A047")
                            t.batteryPercent > 20 -> Color.YELLOW
                            else -> Color.RED
                        }
                    )

                // Wysokość
                binding.tvAltValue.text = "${"%.2f".format(t.altitudeM)} m"

                // Prędkość
                binding.tvSpeedValue.text = "${"%.1f".format(t.speedKmh)} km/h"
                binding.speedBar.progress = t.speedKmh.toInt().coerceIn(0, 40)

                // GPS
                binding.tvLatValue.text = "${"%.6f".format(t.latitude)}°"
                binding.tvLonValue.text = "${"%.6f".format(t.longitude)}°"
                binding.tvSatValue.text = "${t.satellites}"
                binding.tvSatValue.setTextColor(
                    if (t.satellites >= 8) Color.parseColor("#43A047")
                    else if (t.satellites >= 5) Color.YELLOW
                    else Color.RED
                )

                // Sygnał WiFi
                binding.signalBar.progress = t.signalStrength
                binding.tvSignalValue.text = "${t.signalStrength}%"

                // Dystans od Home
                if (homeLat != 0.0 && t.latitude != 0.0) {
                    val dist = calculateDistance(homeLat, homeLon, t.latitude, t.longitude)
                    binding.tvDistValue.text = "${"%.1f".format(dist)} m"
                }

                // Czas lotu
                if (t.isConnected && !isFlightTimerRunning) {
                    flightStartTime = System.currentTimeMillis()
                    isFlightTimerRunning = true
                }
                if (isFlightTimerRunning) {
                    val elapsed = (System.currentTimeMillis() - flightStartTime) / 1000
                    val minutes = elapsed / 60
                    val seconds = elapsed % 60
                    binding.tvFlightTime.text = "%02d:%02d".format(minutes, seconds)
                }

                // Status połączenia
                binding.tvConnectionStatus.text = if (t.isConnected) "✅ Połączono" else "❌ Brak połączenia"
                binding.tvConnectionStatus.setTextColor(
                    if (t.isConnected) Color.parseColor("#43A047") else Color.RED
                )
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.connectionState.collect { state ->
                if (state == com.xbeedrone.app.network.DroneConnection.State.DISCONNECTED) {
                    isFlightTimerRunning = false
                }
            }
        }
    }

    /** Oblicza dystans w metrach między dwoma punktami GPS (wzór Haversine) */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
