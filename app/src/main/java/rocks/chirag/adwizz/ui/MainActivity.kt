package rocks.chirag.adwizz.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import android.graphics.Color
import android.content.Intent
import rocks.chirag.adwizz.data.Settings
import rocks.chirag.adwizz.data.UpdateNotifier
import rocks.chirag.adwizz.data.Updater
import rocks.chirag.adwizz.data.Status
import rocks.chirag.adwizz.data.VpnState
import rocks.chirag.adwizz.vpn.AdBlockVpnService

class MainActivity : ComponentActivity() {

    private val vpnConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            AdBlockVpnService.send(this, AdBlockVpnService.ACTION_START)
        } else {
            Toast.makeText(this, "VPN permission is needed to block ads", Toast.LENGTH_LONG).show()
        }
    }

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeState.mode.value = ThemeMode.from(Settings(this).themeMode)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val dark = isAppInDarkTheme()
            // Keep status/nav bar icons readable when the app theme differs from the system one.
            LaunchedEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(style, style)
            }
            ThemeRevealHost(onApply = { applyTheme(this, it) }) {
                AdWizzTheme {
                    AppScreen(onToggle = ::toggle)
                }
            }
        }
        handleUpdateTap(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleUpdateTap(intent)
    }

    /** Opened from the "update ready" notification: install right away (one tap, no extra screen). */
    private fun handleUpdateTap(intent: Intent?) {
        if (intent?.getBooleanExtra(UpdateNotifier.EXTRA_INSTALL, false) != true) return
        intent.removeExtra(UpdateNotifier.EXTRA_INSTALL)
        UpdateNotifier.cancel(this)
        val app = applicationContext
        // The download can take a while: hold the Activity weakly so a rotation can free the old one,
        // and finish with whichever screen is current (or the app itself).
        val screen = java.lang.ref.WeakReference(this)
        Thread {
            Updater.check(app)?.let { Updater.prepare(app, it) }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                startUpdate(screen.get()?.takeUnless { it.isDestroyed } ?: app)
            }
        }.start()
    }

    private fun toggle() {
        when (VpnState.status.value) {
            Status.RUNNING, Status.STARTING -> AdBlockVpnService.send(this, AdBlockVpnService.ACTION_STOP)
            else -> {
                val consent = VpnService.prepare(this)
                if (consent != null) vpnConsent.launch(consent)
                else AdBlockVpnService.send(this, AdBlockVpnService.ACTION_START)
            }
        }
    }
}
