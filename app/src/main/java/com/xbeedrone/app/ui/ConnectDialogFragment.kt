package com.xbeedrone.app.ui

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.xbeedrone.app.R

/**
 * Dialog wyświetlany gdy użytkownik próbuje połączyć się z dronem
 * ale nie jest podłączony do sieci WiFi drona.
 *
 * Instruuje jak:
 *  1. Włączyć drona
 *  2. Połączyć się z siecią Drone_XXXXXXX w ustawieniach WiFi
 *  3. Wrócić do aplikacji
 */
class ConnectDialogFragment : DialogFragment() {

    companion object {
        const val TAG = "ConnectDialog"

        fun newInstance(): ConnectDialogFragment = ConnectDialogFragment()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext(), R.style.DroneAlertDialog)
            .setTitle("📡 Połącz z dronem")
            .setMessage(
                "Aby połączyć się z dronem X-Bee 9.5 Fold:\n\n" +
                "1️⃣  Włącz drona (przytrzymaj przycisk zasilania)\n\n" +
                "2️⃣  Poczekaj aż diody LED zaczną migać\n\n" +
                "3️⃣  Przejdź do Ustawień WiFi telefonu\n\n" +
                "4️⃣  Połącz się z siecią:\n" +
                "     📶  Drone_XXXXXXX\n" +
                "     (hasło zazwyczaj: 12345678)\n\n" +
                "5️⃣  Wróć do aplikacji i naciśnij POŁĄCZ\n\n" +
                "⚠️  Uwaga: Po połączeniu z WiFi drona\n" +
                "     stracisz dostęp do internetu."
            )
            .setPositiveButton("Otwórz Ustawienia WiFi") { _, _ ->
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
            .setNegativeButton("Anuluj") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .create()
    }
}
