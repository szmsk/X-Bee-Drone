package com.xbeedrone.app.ui.fpv

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.xbeedrone.app.DroneViewModel
import com.xbeedrone.app.R
import com.xbeedrone.app.databinding.FragmentFpvBinding
import com.xbeedrone.app.network.DroneConnection
import com.xbeedrone.app.ui.JoystickView
import kotlinx.coroutines.launch

/**
 * Główny ekran sterowania:
 *  - Podgląd kamery FPV na pełnym ekranie
 *  - Lewy joystick: Throttle (góra/dół) + Yaw (obrót)
 *  - Prawy joystick: Pitch (przód/tył) + Roll (lewo/prawo)
 *  - HUD z telemetrią
 *  - Przyciski: Takeoff/Land, RTH, Foto, Nagrywanie, Stop
 */
class FpvFragment : Fragment() {

    private var _binding: FragmentFpvBinding? = null
    private val binding get() = _binding!!
    private val vm: DroneViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFpvBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupJoysticks()
        setupButtons()
        observeState()
    }

    // ─────────────────────────────────────────────────────────────────────
    //  JOYSTICKI
    // ─────────────────────────────────────────────────────────────────────

    private var throttle = 128
    private var yaw      = 128
    private var pitch    = 128
    private var roll     = 128

    private fun setupJoysticks() {
        // Lewy joystick: Y = throttle (gaz), X = yaw (obrót)
        binding.joystickLeft.label = "GAZ / OBRÓT"
        binding.joystickLeft.autoCenter = false // throttle nie wraca do środka
        binding.joystickLeft.onMoveListener = object : JoystickView.OnMoveListener {
            override fun onMove(xAxis: Float, yAxis: Float) {
                throttle = JoystickView.axisToUdp(yAxis)
                yaw      = JoystickView.axisToUdp(xAxis)
                sendControls()
            }
        }

        // Prawy joystick: Y = pitch (przód/tył), X = roll (lewo/prawo)
        binding.joystickRight.label = "PITCH / ROLL"
        binding.joystickRight.autoCenter = true
        binding.joystickRight.onMoveListener = object : JoystickView.OnMoveListener {
            override fun onMove(xAxis: Float, yAxis: Float) {
                roll  = JoystickView.axisToUdp(xAxis)
                pitch = JoystickView.axisToUdp(yAxis)
                sendControls()
            }
        }
    }

    private fun sendControls() {
        vm.setControls(throttle, yaw, pitch, roll)
    }

    // ─────────────────────────────────────────────────────────────────────
    //  PRZYCISKI
    // ─────────────────────────────────────────────────────────────────────

    private var isFlying = false
    private var isRecording = false

    private fun setupButtons() {
        // Połącz/Rozłącz
        binding.btnConnect.setOnClickListener {
            when (vm.connectionState.value) {
                DroneConnection.State.DISCONNECTED,
                DroneConnection.State.ERROR -> {
                    // Pokaż dialog pomocy jeśli nie ma połączenia z WiFi drona
                    val onDroneWifi = com.xbeedrone.app.utils.WifiHelper
                        .isConnectedToDrone(requireContext())
                    if (!onDroneWifi) {
                        ConnectDialogFragment.newInstance()
                            .show(parentFragmentManager, ConnectDialogFragment.TAG)
                    } else {
                        vm.connect()
                    }
                }
                DroneConnection.State.CONNECTED -> vm.disconnect()
                else -> {}
            }
        }

        // Długie naciśnięcie POŁĄCZ = wymuś połączenie bez sprawdzania WiFi
        binding.btnConnect.setOnLongClickListener {
            if (vm.connectionState.value != DroneConnection.State.CONNECTED) {
                vm.connect()
            }
            true
        }

        // Start / Ląduj
        binding.btnTakeoffLand.setOnClickListener {
            if (!isFlying) {
                vm.takeoff()
                isFlying = true
                binding.btnTakeoffLand.text = "LĄDUJ"
                binding.btnTakeoffLand.setBackgroundColor(Color.parseColor("#E53935"))
            } else {
                vm.land()
                isFlying = false
                binding.btnTakeoffLand.text = "START"
                binding.btnTakeoffLand.setBackgroundColor(Color.parseColor("#43A047"))
            }
        }

        // Powrót do domu (RTH)
        binding.btnRth.setOnClickListener {
            vm.returnHome()
            showToast("Powrót do punktu startowego")
        }

        // STOP ALARMOWY
        binding.btnEmergency.setOnClickListener {
            vm.emergencyStop()
            isFlying = false
            binding.btnTakeoffLand.text = "START"
            binding.btnTakeoffLand.setBackgroundColor(Color.parseColor("#43A047"))
            showToast("⚠ STOP ALARMOWY!")
        }

        // Zdjęcie
        binding.btnPhoto.setOnClickListener {
            vm.takePhoto()
            showToast("📷 Zdjęcie wykonane")
        }

        // Nagrywanie
        binding.btnRecord.setOnClickListener {
            isRecording = !isRecording
            vm.toggleRecording()
            if (isRecording) {
                binding.btnRecord.text = "⏹ STOP"
                binding.btnRecord.setBackgroundColor(Color.parseColor("#E53935"))
            } else {
                binding.btnRecord.text = "⏺ REC"
                binding.btnRecord.setBackgroundColor(Color.parseColor("#757575"))
            }
        }

        // Kalibracja
        binding.btnCalibrate.setOnClickListener {
            vm.calibrate()
            showToast("Kalibracja kompasu...")
        }

        // Tryb headless
        binding.btnHeadless.setOnClickListener {
            vm.toggleHeadless()
        }

        // Altitude hold
        binding.btnAltHold.setOnClickListener {
            vm.toggleAltitudeHold()
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  OBSERWACJA STANU
    // ─────────────────────────────────────────────────────────────────────

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            vm.connectionState.collect { state ->
                updateConnectionUI(state)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.videoFrame.collect { bitmap ->
                bitmap?.let { binding.videoView.setImageBitmap(it) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.telemetry.collect { t ->
                updateHud(t)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.errorMessage.collect { msg ->
                msg?.let { showToast(it) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.isHeadless.collect { headless ->
                binding.btnHeadless.alpha = if (headless) 1.0f else 0.5f
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.isAltitudeHold.collect { hold ->
                binding.btnAltHold.alpha = if (hold) 1.0f else 0.5f
            }
        }
    }

    private fun updateConnectionUI(state: DroneConnection.State) {
        when (state) {
            DroneConnection.State.DISCONNECTED -> {
                binding.btnConnect.text = "POŁĄCZ"
                binding.btnConnect.setBackgroundColor(Color.parseColor("#1565C0"))
                binding.statusDot.setBackgroundColor(Color.RED)
                binding.tvStatus.text = "Rozłączony"
                setControlsEnabled(false)
            }
            DroneConnection.State.CONNECTING -> {
                binding.btnConnect.text = "..."
                binding.statusDot.setBackgroundColor(Color.YELLOW)
                binding.tvStatus.text = "Łączenie..."
                setControlsEnabled(false)
            }
            DroneConnection.State.CONNECTED -> {
                binding.btnConnect.text = "ROZŁĄCZ"
                binding.btnConnect.setBackgroundColor(Color.parseColor("#757575"))
                binding.statusDot.setBackgroundColor(Color.parseColor("#43A047"))
                binding.tvStatus.text = "Połączono"
                setControlsEnabled(true)
            }
            DroneConnection.State.ERROR -> {
                binding.btnConnect.text = "POŁĄCZ"
                binding.btnConnect.setBackgroundColor(Color.parseColor("#E53935"))
                binding.statusDot.setBackgroundColor(Color.RED)
                binding.tvStatus.text = "Błąd połączenia"
                setControlsEnabled(false)
            }
        }
    }

    private fun setControlsEnabled(enabled: Boolean) {
        binding.btnTakeoffLand.isEnabled = enabled
        binding.btnRth.isEnabled = enabled
        binding.btnPhoto.isEnabled = enabled
        binding.btnRecord.isEnabled = enabled
        binding.btnEmergency.isEnabled = enabled
    }

    private fun updateHud(t: com.xbeedrone.app.network.TelemetryData) {
        binding.tvBattery.text  = "🔋 ${t.batteryPercent}%"
        binding.tvAltitude.text = "↕ ${"%.1f".format(t.altitudeM)} m"
        binding.tvSpeed.text    = "⚡ ${"%.1f".format(t.speedKmh)} km/h"
        binding.tvSatellites.text = "🛰 ${t.satellites} sat"
        binding.tvSignal.text   = "📶 ${t.signalStrength}%"

        // Kolor baterii
        val battColor = when {
            t.batteryPercent > 50 -> Color.parseColor("#43A047")
            t.batteryPercent > 20 -> Color.YELLOW
            else                  -> Color.RED
        }
        binding.tvBattery.setTextColor(battColor)
    }

    private fun showToast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
