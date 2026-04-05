package com.xbeedrone.controller.ui.main

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.xbeedrone.controller.R
import com.xbeedrone.controller.databinding.ActivityMainBinding
import com.xbeedrone.controller.model.FlightMode
import com.xbeedrone.controller.ui.camera.CameraActivity
import com.xbeedrone.controller.ui.map.MapActivity
import com.xbeedrone.controller.utils.JoystickView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private var flightTimer: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ekran zawsze włączony, pełnoekranowy
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        )

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setupJoysticks()
        setupButtons()
        observeState()
        observeStream()
    }

    // ─── Joysticks ───────────────────────────────────────────────────────────

    private fun setupJoysticks() {
        // Lewy joystick: throttle (Y) + yaw (X) - NIE wraca do centrum (throttle)
        binding.joystickLeft.returnToCenter = false
        binding.joystickLeft.listener = object : JoystickView.JoystickListener {
            override fun onJoystickMove(x: Float, y: Float) {
                viewModel.updateLeftJoystick(x, y)
            }
            override fun onJoystickReleased() {
                viewModel.updateLeftJoystick(0f, binding.joystickLeft.axisY)
            }
        }

        // Prawy joystick: pitch (Y) + roll (X) - wraca do centrum
        binding.joystickRight.returnToCenter = true
        binding.joystickRight.listener = object : JoystickView.JoystickListener {
            override fun onJoystickMove(x: Float, y: Float) {
                viewModel.updateRightJoystick(x, y)
            }
            override fun onJoystickReleased() {
                viewModel.updateRightJoystick(0f, 0f)
            }
        }
    }

    // ─── Buttons ─────────────────────────────────────────────────────────────

    private fun setupButtons() {
        binding.btnArm.setOnClickListener {
            if (binding.btnArm.tag == "armed") {
                showDisarmConfirmation()
            } else {
                viewModel.arm()
                setArmedState(true)
            }
        }

        binding.btnTakeoff.setOnClickListener { viewModel.takeOff() }
        binding.btnLand.setOnClickListener { viewModel.land() }

        binding.btnRth.setOnClickListener {
            AlertDialog.Builder(this, R.style.DroneAlertDialog)
                .setTitle("Powrót do domu")
                .setMessage("Dron wróci do punktu startowego.")
                .setPositiveButton("RTH") { _, _ -> viewModel.returnToHome() }
                .setNegativeButton("Anuluj", null)
                .show()
        }

        binding.btnEmergency.setOnClickListener {
            AlertDialog.Builder(this, R.style.DroneAlertDialog)
                .setTitle("⚠️ ZATRZYMANIE AWARYJNE")
                .setMessage("Silniki zostaną natychmiast wyłączone! Dron spadnie!")
                .setPositiveButton("STOP!") { _, _ -> viewModel.emergencyStop() }
                .setNegativeButton("Anuluj", null)
                .show()
        }

        binding.btnPhoto.setOnClickListener {
            viewModel.takePhoto()
            flashScreen()
            Toast.makeText(this, "📸 Zdjęcie", Toast.LENGTH_SHORT).show()
        }

        binding.btnRecord.setOnClickListener {
            val state = viewModel.droneState.value
            if (state.isRecording) {
                viewModel.stopRecording()
                binding.btnRecord.setImageResource(R.drawable.ic_record_start)
                binding.tvRecIndicator.visibility = View.GONE
            } else {
                viewModel.startRecording()
                binding.btnRecord.setImageResource(R.drawable.ic_record_stop)
                binding.tvRecIndicator.visibility = View.VISIBLE
            }
        }

        binding.btnMap.setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
        }

        binding.btnCamera.setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }

        // Flips
        binding.btnFlipLeft.setOnClickListener { viewModel.flip("left") }
        binding.btnFlipRight.setOnClickListener { viewModel.flip("right") }
        binding.btnFlipFwd.setOnClickListener { viewModel.flip("forward") }
        binding.btnFlipBack.setOnClickListener { viewModel.flip("back") }
    }

    private fun showDisarmConfirmation() {
        AlertDialog.Builder(this, R.style.DroneAlertDialog)
            .setTitle("Wyłączyć silniki?")
            .setMessage("Silniki zostaną wyłączone.")
            .setPositiveButton("Wyłącz") { _, _ ->
                viewModel.disarm()
                setArmedState(false)
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    private fun setArmedState(armed: Boolean) {
        binding.btnArm.tag = if (armed) "armed" else "disarmed"
        binding.btnArm.text = if (armed) "DISARM" else "ARM"
        binding.btnArm.setBackgroundColor(
            if (armed) Color.parseColor("#F44336") else Color.parseColor("#4CAF50")
        )
    }

    // ─── Observe state ───────────────────────────────────────────────────────

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.droneState.collect { state ->
                updateTelemetry(state)
            }
        }

        lifecycleScope.launch {
            viewModel.isConnected.collect { connected ->
                binding.tvConnectionStatus.text = if (connected) "● POŁĄCZONY" else "● ROZŁĄCZONY"
                binding.tvConnectionStatus.setTextColor(
                    if (connected) Color.parseColor("#4CAF50")
                    else Color.parseColor("#F44336")
                )
            }
        }

        lifecycleScope.launch {
            viewModel.warningMessage.collect { msg ->
                if (msg != null) {
                    binding.tvWarning.text = msg
                    binding.tvWarning.visibility = View.VISIBLE
                    binding.tvWarning.postDelayed({
                        binding.tvWarning.visibility = View.GONE
                        viewModel.clearWarning()
                    }, 4000)
                }
            }
        }
    }

    private fun updateTelemetry(state: com.xbeedrone.controller.model.DroneState) {
        // Bateria
        binding.tvBattery.text = "${state.batteryPercent}%"
        binding.batteryBar.progress = state.batteryPercent
        binding.tvBattery.setTextColor(when {
            state.batteryPercent <= 10 -> Color.parseColor("#F44336")
            state.batteryPercent <= 20 -> Color.parseColor("#FF9800")
            else -> Color.parseColor("#4CAF50")
        })

        // Telemetria
        binding.tvAltitude.text = String.format("%.1f m", state.altitude)
        binding.tvSpeed.text = String.format("%.1f km/h", state.speed)
        binding.tvHeading.text = "${state.heading}°"
        binding.tvDistance.text = String.format("%.0f m", state.distanceFromHome)

        // GPS
        val gpsText = if (state.isGpsFixed) "GPS: ${state.gpsFixCount}sat" else "GPS: brak"
        binding.tvGps.text = gpsText
        binding.tvGps.setTextColor(
            if (state.isGpsFixed) Color.parseColor("#4CAF50")
            else Color.parseColor("#FF9800")
        )

        // Sygnał WiFi
        binding.tvRssi.text = "${state.rssi} dBm"

        // Tryb lotu
        binding.tvFlightMode.text = when (state.flightMode) {
            FlightMode.MANUAL -> "MANUAL"
            FlightMode.GPS_HOLD -> "GPS"
            FlightMode.ALTITUDE_HOLD -> "ALT HOLD"
            FlightMode.RETURN_TO_HOME -> "RTH"
            FlightMode.WAYPOINT -> "WAYPOINT"
            FlightMode.FOLLOW_ME -> "FOLLOW ME"
        }

        // Czas lotu
        val minutes = state.flightTimeSeconds / 60
        val seconds = state.flightTimeSeconds % 60
        binding.tvFlightTime.text = String.format("%02d:%02d", minutes, seconds)

        // Sztuczny horyzont
        binding.attitudeIndicator.setAttitude(state.rollAngle, state.pitchAngle)

        // Stan uzbrojenia
        if (state.isMotorsArmed) setArmedState(true)
    }

    private fun observeStream() {
        lifecycleScope.launch {
            viewModel.frameBitmap.collect { bitmap ->
                bitmap?.let { binding.ivFpvStream.setImageBitmap(it) }
            }
        }

        lifecycleScope.launch {
            viewModel.streamFps.collect { fps ->
                binding.tvFps.text = "$fps FPS"
            }
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun flashScreen() {
        binding.root.setBackgroundColor(Color.WHITE)
        binding.root.postDelayed({
            binding.root.setBackgroundColor(Color.BLACK)
        }, 80)
    }

    override fun onResume() {
        super.onResume()
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        )
    }

    override fun onPause() {
        super.onPause()
        // Reset joysticków gdy aplikacja schodzi w tło
        binding.joystickLeft.reset()
        binding.joystickRight.reset()
        viewModel.updateLeftJoystick(0f, 0f)
        viewModel.updateRightJoystick(0f, 0f)
    }
}
