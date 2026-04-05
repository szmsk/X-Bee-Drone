package com.xbeedrone.controller.ui.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.xbeedrone.controller.databinding.ActivityCameraBinding
import com.xbeedrone.controller.ui.main.MainViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private lateinit var viewModel: MainViewModel

    // Czy pokazywać crosshair i siatkę
    private var showCrosshair = true
    private var showGrid = false
    private var showOverlay = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        )

        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setupButtons()
        observeStream()
        observeTelemetry()
    }

    private fun setupButtons() {
        binding.btnClose.setOnClickListener { finish() }

        binding.btnCapture.setOnClickListener {
            takeScreenshot()
            viewModel.takePhoto()
        }

        binding.btnRecord.setOnClickListener {
            val state = viewModel.droneState.value
            if (state.isRecording) {
                viewModel.stopRecording()
                binding.btnRecord.text = "⏺ REC"
                binding.btnRecord.setTextColor(Color.WHITE)
                binding.tvRecDot.visibility = View.GONE
            } else {
                viewModel.startRecording()
                binding.btnRecord.text = "⏹ STOP"
                binding.btnRecord.setTextColor(Color.RED)
                binding.tvRecDot.visibility = View.VISIBLE
            }
        }

        binding.btnCrosshair.setOnClickListener {
            showCrosshair = !showCrosshair
            binding.crosshairOverlay.visibility = if (showCrosshair) View.VISIBLE else View.GONE
        }

        binding.btnGrid.setOnClickListener {
            showGrid = !showGrid
            binding.gridOverlay.visibility = if (showGrid) View.VISIBLE else View.GONE
        }

        binding.btnOverlay.setOnClickListener {
            showOverlay = !showOverlay
            binding.telemetryOverlay.visibility = if (showOverlay) View.VISIBLE else View.GONE
        }
    }

    private fun observeStream() {
        lifecycleScope.launch {
            viewModel.frameBitmap.collect { bitmap ->
                bitmap?.let { binding.ivFullscreenFpv.setImageBitmap(it) }
            }
        }

        lifecycleScope.launch {
            viewModel.streamFps.collect { fps ->
                binding.tvCameraFps.text = "${fps}fps"
            }
        }

        lifecycleScope.launch {
            viewModel.isStreaming.collect { streaming ->
                binding.tvStreamStatus.text = if (streaming) "● LIVE" else "● OFFLINE"
                binding.tvStreamStatus.setTextColor(
                    if (streaming) Color.parseColor("#F44336") else Color.GRAY
                )
            }
        }
    }

    private fun observeTelemetry() {
        lifecycleScope.launch {
            viewModel.droneState.collect { state ->
                binding.tvCamAltitude.text = String.format("ALT: %.1fm", state.altitude)
                binding.tvCamSpeed.text = String.format("SPD: %.1fkm/h", state.speed)
                binding.tvCamBattery.text = "BAT: ${state.batteryPercent}%"
                binding.tvCamHeading.text = "${state.heading}° ${headingToCompass(state.heading)}"
                binding.tvCamGps.text = if (state.isGpsFixed)
                    "GPS ${state.gpsFixCount}sat" else "NO GPS"

                val minutes = state.flightTimeSeconds / 60
                val seconds = state.flightTimeSeconds % 60
                binding.tvCamTime.text = String.format("%02d:%02d", minutes, seconds)
            }
        }
    }

    private fun headingToCompass(heading: Int): String = when (heading) {
        in 0..22, in 338..360 -> "N"
        in 23..67 -> "NE"
        in 68..112 -> "E"
        in 113..157 -> "SE"
        in 158..202 -> "S"
        in 203..247 -> "SW"
        in 248..292 -> "W"
        in 293..337 -> "NW"
        else -> "N"
    }

    private fun takeScreenshot() {
        try {
            val bitmap = Bitmap.createBitmap(
                binding.ivFullscreenFpv.width,
                binding.ivFullscreenFpv.height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            binding.ivFullscreenFpv.draw(canvas)

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "XBee_${timestamp}.jpg"
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                "XBeeDrone"
            )
            dir.mkdirs()
            val file = File(dir, fileName)

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            Toast.makeText(this, "📸 Zapisano: $fileName", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Błąd zapisu: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
