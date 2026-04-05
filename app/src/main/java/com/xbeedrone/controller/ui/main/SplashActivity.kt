package com.xbeedrone.controller.ui.main

import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.xbeedrone.controller.databinding.ActivitySplashBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setupUI()
        monitorConnection()
    }

    private fun setupUI() {
        binding.btnConnect.setOnClickListener {
            val ssid = binding.etWifiSsid.text.toString()
            showConnecting(true)
            viewModel.connect()

            lifecycleScope.launch {
                delay(4000)
                if (viewModel.isConnected.value) {
                    goToMain()
                } else {
                    showConnecting(false)
                    binding.tvStatus.text = "❌ Nie można połączyć.\nSprawdź czy jesteś połączony z WiFi drona."
                }
            }
        }

        binding.btnSkip.setOnClickListener {
            goToMain()
        }

        // Pokaż aktualną sieć WiFi
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
        val wifiInfo = wifiManager?.connectionInfo
        val ssid = wifiInfo?.ssid?.replace("\"", "") ?: "Brak"
        binding.tvWifiCurrent.text = "Połączony z: $ssid"
    }

    private fun monitorConnection() {
        lifecycleScope.launch {
            viewModel.isConnected.collect { connected ->
                if (connected) {
                    binding.tvStatus.text = "✅ Połączono z dronem!"
                    delay(800)
                    goToMain()
                }
            }
        }
    }

    private fun showConnecting(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnConnect.isEnabled = !show
        binding.tvStatus.text = if (show) "⏳ Łączenie z dronem..." else "Gotowy do połączenia"
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
