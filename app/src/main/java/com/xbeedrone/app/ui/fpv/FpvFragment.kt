package com.xbeedrone.app.ui.fpv

import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.xbeedrone.app.DroneViewModel
import com.xbeedrone.app.databinding.FragmentFpvBinding
import com.xbeedrone.app.network.DroneConnection
import com.xbeedrone.app.ui.JoystickView
import kotlinx.coroutines.launch

class FpvFragment : Fragment() {

    private var _b: FragmentFpvBinding? = null
    private val b get() = _b!!
    private val vm: DroneViewModel by activityViewModels()

    private var isFlying    = false
    private var isRecording = false
    private var throttle = 128; private var yaw   = 128
    private var pitch    = 128; private var roll  = 128

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentFpvBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupJoysticks()
        setupButtons()
        observe()
    }

    private fun setupJoysticks() {
        b.joystickLeft.label = "GAZ / OBRÓT"
        b.joystickLeft.autoCenter = false
        b.joystickLeft.onMoveListener = object : JoystickView.OnMoveListener {
            override fun onMove(x: Float, y: Float) {
                throttle = JoystickView.axisToUdp(y); yaw = JoystickView.axisToUdp(x); send()
            }
        }
        b.joystickRight.label = "PITCH / ROLL"
        b.joystickRight.autoCenter = true
        b.joystickRight.onMoveListener = object : JoystickView.OnMoveListener {
            override fun onMove(x: Float, y: Float) {
                roll = JoystickView.axisToUdp(x); pitch = JoystickView.axisToUdp(y); send()
            }
        }
    }

    private fun send() = vm.setControls(throttle, yaw, pitch, roll)

    private fun setupButtons() {
        b.btnConnect.setOnClickListener {
            when (vm.connectionState.value) {
                DroneConnection.State.DISCONNECTED,
                DroneConnection.State.ERROR -> vm.connect()
                DroneConnection.State.CONNECTED -> vm.disconnect()
                else -> {}
            }
        }
        b.btnConnect.setOnLongClickListener { vm.connect(); true }

        b.btnTakeoffLand.setOnClickListener {
            if (!isFlying) {
                vm.takeoff(); isFlying = true
                b.btnTakeoffLand.text = "LĄDUJ"
                b.btnTakeoffLand.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#E53935"))
            } else {
                vm.land(); isFlying = false
                b.btnTakeoffLand.text = "START"
                b.btnTakeoffLand.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#43A047"))
            }
        }

        b.btnRth.setOnClickListener { vm.returnHome(); toast("Powrót do domu") }

        b.btnEmergency.setOnClickListener {
            vm.emergencyStop(); isFlying = false
            b.btnTakeoffLand.text = "START"
            b.btnTakeoffLand.backgroundTintList =
                android.content.res.ColorStateList.valueOf(Color.parseColor("#43A047"))
            toast("⚠ STOP ALARMOWY!")
        }

        b.btnPhoto.setOnClickListener { vm.takePhoto(); toast("Zdjęcie!") }

        b.btnRecord.setOnClickListener {
            isRecording = !isRecording; vm.toggleRecording()
            b.btnRecord.text = if (isRecording) "⏹ STOP" else "⏺ REC"
            b.btnRecord.backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (isRecording) Color.parseColor("#E53935") else Color.parseColor("#757575")
            )
        }

        b.btnHeadless.setOnClickListener { vm.toggleHeadless() }
        b.btnAltHold.setOnClickListener  { vm.toggleAltitudeHold() }
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            vm.connectionState.collect { updateConnUI(it) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.videoFrame.collect { bmp -> bmp?.let { b.videoView.setImageBitmap(it) } }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.telemetry.collect { t ->
                b.tvBattery.text  = "🔋 ${t.batteryPercent}%"
                b.tvAltitude.text = "↕ ${"%.1f".format(t.altitudeM)}m"
                b.tvSpeed.text    = "⚡ ${"%.1f".format(t.speedKmh)}km/h"
                b.tvSatellites.text = "🛰 ${t.satellites}"
                b.tvSignal.text   = "📶 ${t.signalStrength}%"
                b.tvBattery.setTextColor(when {
                    t.batteryPercent > 50 -> Color.parseColor("#43A047")
                    t.batteryPercent > 20 -> Color.YELLOW
                    else -> Color.RED
                })
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.errorMessage.collect { msg -> msg?.let { toast(it) } }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.isHeadless.collect { b.btnHeadless.alpha = if (it) 1f else 0.45f }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.isAltitudeHold.collect { b.btnAltHold.alpha = if (it) 1f else 0.45f }
        }
    }

    private fun updateConnUI(state: DroneConnection.State) {
        when (state) {
            DroneConnection.State.DISCONNECTED -> {
                b.btnConnect.text = "POŁĄCZ"
                b.statusDot.setBackgroundColor(Color.RED)
                b.tvStatus.text = "Rozłączony"
                setCtrlEnabled(false)
            }
            DroneConnection.State.CONNECTING -> {
                b.btnConnect.text = "..."
                b.statusDot.setBackgroundColor(Color.YELLOW)
                b.tvStatus.text = "Łączenie..."
                setCtrlEnabled(false)
            }
            DroneConnection.State.CONNECTED -> {
                b.btnConnect.text = "ROZŁĄCZ"
                b.statusDot.setBackgroundColor(Color.parseColor("#43A047"))
                b.tvStatus.text = "Połączono"
                setCtrlEnabled(true)
            }
            DroneConnection.State.ERROR -> {
                b.btnConnect.text = "POŁĄCZ"
                b.statusDot.setBackgroundColor(Color.RED)
                b.tvStatus.text = "Błąd"
                setCtrlEnabled(false)
            }
        }
    }

    private fun setCtrlEnabled(on: Boolean) {
        b.btnTakeoffLand.isEnabled = on
        b.btnRth.isEnabled = on
        b.btnPhoto.isEnabled = on
        b.btnRecord.isEnabled = on
        b.btnEmergency.isEnabled = on
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
