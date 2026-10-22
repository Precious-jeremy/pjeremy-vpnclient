package com.example.vpnclient

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Step 3a: real SSH connection (over SSL/stunnel), no more fake toggle.
 *
 * Tapping the character now actually attempts to connect — the image only
 * swaps to the "connected" art once authentication genuinely succeeds. On
 * failure it stays on the idle art and shows an error.
 *
 * NOTE: this proves server connectivity but does not yet route all device
 * traffic through the tunnel — that's the next, harder step.
 */
class MainActivity : AppCompatActivity() {

    // Hardcoded test server for now — config UI (import/export, multiple
    // profiles) comes in a later step once the core connection works.
    private val testConfig = TunnelConfig(
        host = "nl1.sshweb.site",
        sslPort = 443,
        username = "sshocean-lizzy",
        password = "65777",
        sniHostname = "mtnplay.com"
    )

    private var isBusy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.statusText)
        val shieldImage = findViewById<ImageView>(R.id.shieldImage)

        // Continuous gentle floating animation, runs regardless of connection state.
        ObjectAnimator.ofFloat(shieldImage, "translationY", -14f, 14f).apply {
            duration = 1600
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        shieldImage.setOnClickListener {
            if (isBusy) return@setOnClickListener

            if (TunnelManager.isConnected) {
                // Disconnect
                isBusy = true
                statusText.text = "Disconnecting..."
                TunnelManager.disconnect {
                    runOnUiThread {
                        isBusy = false
                        statusText.text = getString(R.string.status_disconnected)
                        shieldImage.setImageResource(R.drawable.ic_character_idle)
                    }
                }
            } else {
                // Connect
                isBusy = true
                statusText.text = "Connecting..."
                TunnelManager.connect(testConfig) { success, message ->
                    runOnUiThread {
                        isBusy = false
                        if (success) {
                            statusText.text = getString(R.string.status_connected)
                            shieldImage.setImageResource(R.drawable.ic_character_connected)
                        } else {
                            // Stays on idle art — this is the real failure path,
                            // not just a reset.
                            statusText.text = getString(R.string.status_disconnected)
                            shieldImage.setImageResource(R.drawable.ic_character_idle)
                            Toast.makeText(this, "Connection failed: $message", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }
}
